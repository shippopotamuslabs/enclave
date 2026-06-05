#include "local_image_gen_backend_client.h"

#include "local_image_gen_runtime.h"

#include <android/log.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <errno.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/wait.h>
#include <unistd.h>

#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#define LOG_TAG "local-image-gen-backend"
#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "thirdparty/stb_image_write.h"

namespace local_image_gen {
namespace {

constexpr const char* kCoreExecutableName = "libstable_diffusion_core.so";
constexpr const char* kCoreLogTag = "local-image-gen-core";

struct BackendProcess {
    pid_t pid = -1;
    int log_fd = -1;
};

void copy_error(const char* msg, char* error_out, int error_out_len) {
    if (error_out == nullptr || error_out_len <= 0) {
        return;
    }
    std::strncpy(error_out, msg, static_cast<size_t>(error_out_len - 1));
    error_out[error_out_len - 1] = '\0';
}

bool file_exists(const std::string& path) {
    return access(path.c_str(), F_OK) == 0;
}

std::string json_escape(const std::string& input) {
    std::string out;
    out.reserve(input.size() + 8);
    for (char c : input) {
        switch (c) {
            case '\\':
                out += "\\\\";
                break;
            case '"':
                out += "\\\"";
                break;
            case '\n':
                out += "\\n";
                break;
            case '\r':
                out += "\\r";
                break;
            case '\t':
                out += "\\t";
                break;
            default:
                out += c;
                break;
        }
    }
    return out;
}

bool http_request(
    int port,
    const char* method,
    const char* path,
    const char* body,
    std::string& response,
    int timeout_ms) {
    const int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        return false;
    }
    timeval tv {};
    tv.tv_sec = timeout_ms / 1000;
    tv.tv_usec = (timeout_ms % 1000) * 1000;
    setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));

    sockaddr_in addr {};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(static_cast<uint16_t>(port));
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    if (connect(sock, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) != 0) {
        close(sock);
        return false;
    }

    std::ostringstream req;
    req << method << " " << path << " HTTP/1.1\r\n"
        << "Host: 127.0.0.1\r\n"
        << "Connection: close\r\n";
    if (body != nullptr) {
        req << "Content-Type: application/json\r\n"
            << "Content-Length: " << std::strlen(body) << "\r\n";
    }
    req << "\r\n";
    if (body != nullptr) {
        req << body;
    }
    const auto payload = req.str();
    if (send(sock, payload.c_str(), payload.size(), 0) < 0) {
        close(sock);
        return false;
    }

    char buf[8192];
    response.clear();
    while (true) {
        const ssize_t n = recv(sock, buf, sizeof(buf), 0);
        if (n <= 0) {
            break;
        }
        response.append(buf, static_cast<size_t>(n));
    }
    close(sock);
    return !response.empty();
}

std::string describe_child_status(int status) {
    char buf[128];
    if (WIFEXITED(status)) {
        std::snprintf(buf, sizeof(buf), "exit code %d", WEXITSTATUS(status));
        return buf;
    }
    if (WIFSIGNALED(status)) {
        std::snprintf(buf, sizeof(buf), "signal %d", WTERMSIG(status));
        return buf;
    }
    return "unknown status";
}

void emit_core_log_line(std::string& pending) {
    while (true) {
        const auto pos = pending.find('\n');
        if (pos == std::string::npos) {
            break;
        }
        std::string line = pending.substr(0, pos);
        if (!line.empty() && line.back() == '\r') {
            line.pop_back();
        }
        if (!line.empty()) {
            __android_log_print(ANDROID_LOG_INFO, kCoreLogTag, "%s", line.c_str());
        }
        pending.erase(0, pos + 1);
    }
}

void drain_core_logs(int fd, std::string& pending) {
    if (fd < 0) {
        return;
    }
    char buffer[4096];
    while (true) {
        const ssize_t n = read(fd, buffer, sizeof(buffer));
        if (n > 0) {
            pending.append(buffer, static_cast<size_t>(n));
            emit_core_log_line(pending);
            continue;
        }
        if (n == 0 || errno == EAGAIN || errno == EWOULDBLOCK) {
            break;
        }
        break;
    }
}

void flush_core_logs(int fd, std::string& pending) {
    drain_core_logs(fd, pending);
    if (!pending.empty()) {
        __android_log_print(ANDROID_LOG_INFO, kCoreLogTag, "%s", pending.c_str());
        pending.clear();
    }
}

bool wait_for_health(BackendProcess& process, int port, int timeout_ms, std::string& failure_reason) {
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeout_ms);
    std::string pending_log;
    while (std::chrono::steady_clock::now() < deadline) {
        drain_core_logs(process.log_fd, pending_log);
        int status = 0;
        const pid_t result = waitpid(process.pid, &status, WNOHANG);
        if (result == process.pid) {
            flush_core_logs(process.log_fd, pending_log);
            failure_reason = "Diffusion backend exited before health check (" +
                describe_child_status(status) + ").";
            return false;
        }
        std::string response;
        if (http_request(port, "GET", "/health", nullptr, response, 500) &&
            response.find("200") != std::string::npos) {
            flush_core_logs(process.log_fd, pending_log);
            return true;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    flush_core_logs(process.log_fd, pending_log);
    failure_reason = "Diffusion backend health check timed out.";
    return false;
}

std::vector<unsigned char> base64_decode(const std::string& input) {
    static const int8_t table[256] = {
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62,
        -1, -1, -1, 63, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -1, -1, -1, -1, 0,
        1,  2,  3,  4,  5,  6,  7,  8,  9,  10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22,
        23, 24, 25, -1, -1, -1, -1, -1, -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38,
        39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -1, -1, -1, -1, -1,
    };
    std::vector<unsigned char> out;
    out.reserve(input.size());
    int val = 0;
    int valb = -8;
    for (unsigned char c : input) {
        if (table[c] == -1) {
            break;
        }
        val = (val << 6) + table[c];
        valb += 6;
        if (valb >= 0) {
            out.push_back(static_cast<unsigned char>((val >> valb) & 0xFF));
            valb -= 8;
        }
    }
    return out;
}

std::string lowercase_ascii(std::string value) {
    for (char& c : value) {
        if (c >= 'A' && c <= 'Z') {
            c = static_cast<char>(c - 'A' + 'a');
        }
    }
    return value;
}

std::string decode_chunked_body(const std::string& body) {
    std::string decoded;
    size_t pos = 0;
    while (pos < body.size()) {
        const auto line_end = body.find("\r\n", pos);
        if (line_end == std::string::npos) {
            return decoded.empty() ? body : decoded;
        }
        std::string size_text = body.substr(pos, line_end - pos);
        const auto extension = size_text.find(';');
        if (extension != std::string::npos) {
            size_text = size_text.substr(0, extension);
        }
        char* end = nullptr;
        const unsigned long chunk_size = std::strtoul(size_text.c_str(), &end, 16);
        if (end == size_text.c_str()) {
            return decoded.empty() ? body : decoded;
        }
        pos = line_end + 2;
        if (chunk_size == 0) {
            return decoded;
        }
        if (pos + chunk_size > body.size()) {
            return decoded.empty() ? body : decoded;
        }
        decoded.append(body, pos, static_cast<size_t>(chunk_size));
        pos += static_cast<size_t>(chunk_size);
        if (pos + 2 <= body.size() && body[pos] == '\r' && body[pos + 1] == '\n') {
            pos += 2;
        }
    }
    return decoded;
}

std::string decode_http_response_body(const std::string& response) {
    const auto header_end = response.find("\r\n\r\n");
    if (header_end == std::string::npos) {
        return response;
    }
    const std::string headers = lowercase_ascii(response.substr(0, header_end));
    const std::string body = response.substr(header_end + 4);
    if (headers.find("transfer-encoding: chunked") != std::string::npos) {
        return decode_chunked_body(body);
    }
    return body;
}

bool has_json_type(const std::string& body, const char* type) {
    const std::string compact = std::string("\"type\":\"") + type + "\"";
    const std::string spaced = std::string("\"type\": \"") + type + "\"";
    return body.find(compact) != std::string::npos || body.find(spaced) != std::string::npos;
}

bool has_sse_event(const std::string& body, const char* event_name) {
    const std::string marker = std::string("event: ") + event_name;
    return body.find(marker) != std::string::npos;
}

std::string response_tail_for_log(const std::string& body) {
    constexpr size_t kMaxLogBodyBytes = 2048;
    const size_t start = body.size() > kMaxLogBodyBytes ? body.size() - kMaxLogBodyBytes : 0;
    std::string tail = body.substr(start);
    for (char& c : tail) {
        if (c == '\r' || c == '\n' || c == '\t') {
            c = ' ';
        }
    }
    return tail;
}

std::string extract_json_string_field(const std::string& json, const char* key) {
    const std::string needle = std::string("\"") + key + "\":\"";
    const auto pos = json.find(needle);
    if (pos == std::string::npos) {
        return "";
    }
    std::string out;
    for (size_t i = pos + needle.size(); i < json.size(); ++i) {
        const char c = json[i];
        if (c == '"' && (i == 0 || json[i - 1] != '\\')) {
            break;
        }
        if (c == '\\' && i + 1 < json.size()) {
            const char next = json[i + 1];
            if (next == 'n') {
                out.push_back('\n');
                ++i;
                continue;
            }
            if (next == 'r') {
                out.push_back('\r');
                ++i;
                continue;
            }
            if (next == 't') {
                out.push_back('\t');
                ++i;
                continue;
            }
            if (next == '"' || next == '\\') {
                out.push_back(next);
                ++i;
                continue;
            }
        }
        out.push_back(c);
    }
    return out;
}

int extract_json_int_field(const std::string& json, const char* key, int fallback) {
    const std::string needle = std::string("\"") + key + "\":";
    const auto pos = json.find(needle);
    if (pos == std::string::npos) {
        return fallback;
    }
    return std::atoi(json.c_str() + pos + needle.size());
}

bool parse_complete_event(const std::string& body, std::string& rgb, int& width, int& height, int& channels) {
    auto marker = body.find("event: complete");
    if (marker == std::string::npos) {
        marker = body.find("\"type\":\"complete\"");
    }
    if (marker == std::string::npos) {
        return false;
    }
    const auto slice = body.substr(marker);
    const std::string image_b64 = extract_json_string_field(slice, "image");
    if (image_b64.empty()) {
        return false;
    }
    const auto decoded = base64_decode(image_b64);
    rgb.assign(reinterpret_cast<const char*>(decoded.data()), decoded.size());
    width = extract_json_int_field(slice, "width", 512);
    height = extract_json_int_field(slice, "height", 512);
    channels = extract_json_int_field(slice, "channels", 3);
    return !rgb.empty();
}

std::string extract_backend_error_message(const std::string& body) {
    auto marker = body.find("event: error");
    if (marker == std::string::npos) {
        marker = body.find("\"type\":\"error\"");
    }
    if (marker == std::string::npos) {
        return "";
    }
    return extract_json_string_field(body.substr(marker), "message");
}

BackendProcess spawn_backend(
    const BackendLaunchConfig& config,
    int port,
    char* error_out,
    int error_out_len) {
    const std::string executable = native_lib_dir() + "/" + kCoreExecutableName;
    if (!file_exists(executable)) {
        copy_error("libstable_diffusion_core.so is not packaged.", error_out, error_out_len);
        return {};
    }
    if (!prepare_qnn_runtime_libs(error_out, error_out_len)) {
        return {};
    }

    std::vector<std::string> args;
    args.emplace_back(executable);
    if (config.use_cpu) {
        args.push_back("--clip");
        args.push_back(config.model_dir + "/clip.mnn");
        args.push_back("--unet");
        args.push_back(config.model_dir + "/unet.mnn");
        args.push_back("--vae_decoder");
        args.push_back(config.model_dir + "/vae_decoder.mnn");
        args.push_back("--tokenizer");
        args.push_back(config.model_dir + "/tokenizer.json");
        args.push_back("--port");
        args.push_back(std::to_string(port));
        args.push_back("--text_embedding_size");
        args.push_back("768");
        args.push_back("--cpu");
        if (file_exists(config.model_dir + "/vae_encoder.mnn")) {
            args.push_back("--vae_encoder");
            args.push_back(config.model_dir + "/vae_encoder.mnn");
        }
    } else {
        // IMPORTANT: pass the "clip.mnn" sentinel, NOT clip_v2.mnn. The backend only
        // runs its clip_v2 auto-upgrade (which sets use_clip_v2=true and loads
        // token_emb.bin/pos_emb.bin) when the --clip arg ends in exactly "clip.mnn"
        // (see local-dream main.cpp). The sentinel file itself need not exist; the
        // backend resolves clip_v2.mnn in the same dir. Passing clip_v2.mnn directly
        // leaves use_clip_v2=false and crashes on a null "input_ids" tensor at startup.
        args.push_back("--clip");
        args.push_back(config.model_dir + "/clip.mnn");
        args.push_back("--unet");
        args.push_back(config.model_dir + "/unet.bin");
        args.push_back("--vae_decoder");
        args.push_back(config.model_dir + "/vae_decoder.bin");
        args.push_back("--tokenizer");
        args.push_back(config.model_dir + "/tokenizer.json");
        args.push_back("--backend");
        args.push_back(qnn_runtime_dir() + "/libQnnHtp.so");
        args.push_back("--system_library");
        args.push_back(qnn_runtime_dir() + "/libQnnSystem.so");
        args.push_back("--port");
        args.push_back(std::to_string(port));
        args.push_back("--text_embedding_size");
        args.push_back("768");
        args.push_back("--use_cpu_clip");
        if (file_exists(config.model_dir + "/vae_encoder.bin")) {
            args.push_back("--vae_encoder");
            args.push_back(config.model_dir + "/vae_encoder.bin");
        }
        if (file_exists(config.model_dir + "/V_PRED")) {
            args.push_back("--use_v_pred");
        }
    }

    std::vector<char*> argv;
    argv.reserve(args.size() + 1);
    for (auto& arg : args) {
        argv.push_back(arg.data());
    }
    argv.push_back(nullptr);

    int log_pipe[2] = {-1, -1};
    if (pipe(log_pipe) != 0) {
        copy_error("Failed to create diffusion backend log pipe.", error_out, error_out_len);
        return {};
    }
    fcntl(log_pipe[0], F_SETFL, fcntl(log_pipe[0], F_GETFL, 0) | O_NONBLOCK);

    const pid_t pid = fork();
    if (pid == 0) {
        close(log_pipe[0]);
        const std::string ld_path =
            qnn_runtime_dir() + ":/vendor/lib64:/vendor/lib/rfsa/adsp:/system/lib64";
        const std::string dsp_path = qnn_runtime_dir() + ":/vendor/lib/rfsa/adsp";
        const std::string adsp_path =
            qnn_runtime_dir() + ";/vendor/lib/rfsa/adsp;/vendor/dsp/cdsp;/system/lib/rfsa/adsp";
        setenv("LD_LIBRARY_PATH", ld_path.c_str(), 1);
        setenv("DSP_LIBRARY_PATH", dsp_path.c_str(), 1);
        setenv("ADSP_LIBRARY_PATH", adsp_path.c_str(), 1);
        dup2(log_pipe[1], STDOUT_FILENO);
        dup2(log_pipe[1], STDERR_FILENO);
        close(log_pipe[1]);
        chdir(native_lib_dir().c_str());
        execv(executable.c_str(), argv.data());
        _exit(127);
    }
    if (pid < 0) {
        close(log_pipe[0]);
        close(log_pipe[1]);
        copy_error("Failed to fork diffusion backend.", error_out, error_out_len);
        return {};
    }
    close(log_pipe[1]);
    return BackendProcess {pid, log_pipe[0]};
}

void stop_backend(BackendProcess& process) {
    if (process.pid > 0) {
        kill(process.pid, SIGTERM);
        bool reaped = false;
        for (int i = 0; i < 20; ++i) {  // up to ~2s for graceful shutdown
            if (waitpid(process.pid, nullptr, WNOHANG) == process.pid) {
                reaped = true;
                break;
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }
        if (!reaped) {
            kill(process.pid, SIGKILL);
            waitpid(process.pid, nullptr, 0);  // SIGKILL cannot be caught; blocking reap is safe
        }
        process.pid = -1;
    }
    if (process.log_fd >= 0) {
        close(process.log_fd);
        process.log_fd = -1;
    }
}

}  // namespace

bool run_stable_diffusion_generate(
    const BackendLaunchConfig& config,
    const GenerateRequest& request,
    char* error_out,
    int error_out_len) {
    if (config.use_cpu) {
        if (!mnn_model_dir_ready(config.model_dir.c_str(), error_out, error_out_len)) {
            return false;
        }
    } else if (!qnn_model_dir_ready(config.model_dir.c_str(), error_out, error_out_len)) {
        return false;
    }

    const int port = 18081 + static_cast<int>(getpid() % 200);
    BackendProcess child = spawn_backend(config, port, error_out, error_out_len);
    if (child.pid < 0) {
        return false;
    }

    bool ok = false;
    std::string health_failure;
    if (!wait_for_health(child, port, 300000, health_failure)) {
        copy_error(health_failure.c_str(), error_out, error_out_len);
    } else {
        const std::string body = build_generate_request_body(request);

        std::atomic<bool> keep_draining_logs {true};
        std::thread log_thread([&child, &keep_draining_logs]() {
            std::string pending_log;
            while (keep_draining_logs.load()) {
                drain_core_logs(child.log_fd, pending_log);
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
            }
            flush_core_logs(child.log_fd, pending_log);
        });

        std::string raw_response;
        const bool request_ok =
            http_request(port, "POST", "/generate", body.c_str(), raw_response, 3600000);
        keep_draining_logs.store(false);
        log_thread.join();

        if (!request_ok) {
            copy_error("HTTP generate request failed.", error_out, error_out_len);
        } else {
            const std::string response = decode_http_response_body(raw_response);
            if (has_sse_event(response, "error") || has_json_type(response, "error")) {
                const std::string message = extract_backend_error_message(response);
                const std::string error =
                    message.empty() ? "Diffusion backend returned an error event."
                                    : "Diffusion backend error: " + message;
                copy_error(error.c_str(), error_out, error_out_len);
            } else {
            std::string rgb;
            int out_w = 0;
            int out_h = 0;
            int channels = 3;
            if (!parse_complete_event(response, rgb, out_w, out_h, channels)) {
                __android_log_print(
                    ANDROID_LOG_ERROR,
                    LOG_TAG,
                    "Missing complete event in backend response. raw_bytes=%zu decoded_bytes=%zu tail=%s",
                    raw_response.size(),
                    response.size(),
                    response_tail_for_log(response).c_str());
                copy_error("Missing complete event in backend response.", error_out, error_out_len);
            } else if (channels != 3) {
                copy_error("Unexpected channel count from backend.", error_out, error_out_len);
            } else {
                const int stride = out_w * 3;
                if (stbi_write_png(request.output_path.c_str(), out_w, out_h, 3,
                                   reinterpret_cast<const unsigned char*>(rgb.data()), stride) != 0) {
                    ok = true;
                } else {
                    copy_error("Failed to write PNG output.", error_out, error_out_len);
                }
            }
            }
        }
    }

    stop_backend(child);
    return ok;
}

}  // namespace local_image_gen
