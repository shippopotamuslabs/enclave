#pragma once

#include <sstream>
#include <string>

namespace local_image_gen {

struct BackendLaunchConfig {
    std::string model_dir;
    bool use_cpu = false;
};

struct GenerateRequest {
    std::string prompt;
    std::string negative_prompt;
    int width = 512;
    int height = 512;
    int steps = 20;
    float cfg_scale = 7.5f;
    long seed = 0;
    std::string output_path;
    std::string scheduler = "dpm";
};

// Minimal JSON string escape used by the request body builder.
inline std::string json_escape_field(const std::string& input) {
    std::string out;
    out.reserve(input.size() + 8);
    for (char c : input) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default: out += c; break;
        }
    }
    return out;
}

// Pure, side-effect-free builder for the `/generate` JSON request body.
// Extracted as a deterministic seam (inline so the host unit test and the
// production translation unit share one implementation) so the emitted body
// can be asserted without spawning the backend process or performing network I/O.
//
// NOTE (TDD Red): this currently hardcodes "scheduler":"dpm" exactly as the
// previous inline body builder did. The Green phase replaces the hardcoded
// literal with request.scheduler.
inline std::string build_generate_request_body(const GenerateRequest& request) {
    std::ostringstream body;
    body << "{"
         << "\"prompt\":\"" << json_escape_field(request.prompt) << "\","
         << "\"negative_prompt\":\"" << json_escape_field(request.negative_prompt) << "\","
         << "\"steps\":" << request.steps << ","
         << "\"cfg\":" << request.cfg_scale << ","
         << "\"width\":" << request.width << ","
         << "\"height\":" << request.height << ","
         << "\"seed\":" << request.seed << ","
         << "\"scheduler\":\"" << json_escape_field(request.scheduler) << "\""
         << "}";
    return body.str();
}

bool run_stable_diffusion_generate(
    const BackendLaunchConfig& config,
    const GenerateRequest& request,
    char* error_out,
    int error_out_len);

}  // namespace local_image_gen