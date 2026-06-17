#include "qnn_runtime.h"

#include "qnn_fastrpc.h"
#include "qnn_log.h"

#include <dlfcn.h>
#include <mutex>
#include <sys/stat.h>
#include <unistd.h>

namespace enclave::diffusion {
namespace {

using QnnInterfaceGetProvidersFn = Qnn_ErrorHandle_t (*)(
    const QnnInterface_t*** provider_list,
    uint32_t* num_providers);

using QnnSystemInterfaceGetProvidersFn = Qnn_ErrorHandle_t (*)(
    const QnnSystemInterface_t*** provider_list,
    uint32_t* num_providers);

template <typename Fn>
Fn resolveSymbol(void* handle, const char* name) {
    auto* sym = reinterpret_cast<Fn>(dlsym(handle, name));
    if (sym == nullptr) {
        ENCLAVE_QNN_LOGE("Missing symbol %s: %s", name, dlerror());
    }
    return sym;
}

std::mutex g_system_mutex;
void* g_system_handle = nullptr;
int g_system_refcount = 0;
QNN_SYSTEM_INTERFACE_VER_TYPE g_system_interface {};

}  // namespace

bool fileExists(const std::string& path) {
    return access(path.c_str(), F_OK) == 0;
}

void configureQnnLdLibraryPath(const std::string& runtime_dir) {
    setenv("LD_LIBRARY_PATH", runtime_dir.c_str(), 1);
}

bool preloadCdspRpc(const std::string& runtime_dir) {
    (void)runtime_dir;
    // libcdsprpc must come from the vendor partition (AndroidManifest uses-native-library +
    // System.loadLibrary in Kotlin). A bundled copy in jniLibs cannot resolve libhidlbase.so etc.
    if (dlsym(RTLD_DEFAULT, "remote_session_control") != nullptr) {
        ENCLAVE_QNN_LOGI("libcdsprpc.so already loaded (remote_session_control visible).");
        return true;
    }
    if (dlopen("libcdsprpc.so", RTLD_NOW | RTLD_GLOBAL) != nullptr) {
        ENCLAVE_QNN_LOGI("Preloaded libcdsprpc.so from app linker namespace");
        return true;
    }
    ENCLAVE_QNN_LOGW("libcdsprpc.so not preloaded: %s", dlerror());
    return false;
}

bool preloadQnnHtpLibraries(
    const std::string& runtime_dir,
    const char* htp_stub_library,
    std::string& error) {
    if (runtime_dir.empty()) {
        error = "QNN runtime directory is empty.";
        return false;
    }

    configureQnnLdLibraryPath(runtime_dir);
    preloadCdspRpc(runtime_dir);
    if (!enableQnnUnsignedCdspProcessDomain(error)) {
        return false;
    }

    const std::string prepare_path = runtime_dir + "/libQnnHtpPrepare.so";
    if (!fileExists(prepare_path)) {
        error = "Missing libQnnHtpPrepare.so. Re-sync QNN assets (scripts/sync-qnn-jniLibs.sh).";
        return false;
    }
    if (dlopen(prepare_path.c_str(), RTLD_NOW | RTLD_GLOBAL) == nullptr) {
        error = std::string("Failed to load libQnnHtpPrepare.so: ") + dlerror();
        return false;
    }

    if (htp_stub_library != nullptr && htp_stub_library[0] != '\0') {
        const std::string stub_path = runtime_dir + "/" + htp_stub_library;
        if (!fileExists(stub_path)) {
            error = std::string("Missing HTP stub library: ") + htp_stub_library;
            return false;
        }
        if (dlopen(stub_path.c_str(), RTLD_NOW | RTLD_GLOBAL) == nullptr) {
            error = std::string("Failed to load ") + htp_stub_library + ": " + dlerror();
            return false;
        }
    }

    ENCLAVE_QNN_LOGI("Preloaded QNN HTP dependencies from %s", runtime_dir.c_str());
    return true;
}

void setDspLibraryPath(const std::string& dsp_library_path) {
    // QAIRT android-qnn-net-run htp-v79 uses only the app-private runtime dir for unsigned skels.
    // Appending OEM DSP paths causes the HTP backend to load signed vendor skels (e.g. V81) that
    // mismatch 8gen3 V79-compiled graph binaries.
    setenv("DSP_LIBRARY_PATH", dsp_library_path.c_str(), 1);
    setenv("ADSP_LIBRARY_PATH", dsp_library_path.c_str(), 1);
    setenv("CDSP_LIBRARY_PATH", dsp_library_path.c_str(), 1);
    configureQnnLdLibraryPath(dsp_library_path);
}

bool loadEnclaveQnnBackend(
    const std::string& backend_path,
    EnclaveQnnApis& apis,
    void** backend_handle_out,
    std::string& error) {
    void* backend_handle = dlopen(backend_path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (backend_handle == nullptr) {
        error = std::string("Unable to load QNN backend: ") + dlerror();
        return false;
    }

    auto get_providers = resolveSymbol<QnnInterfaceGetProvidersFn>(
        backend_handle,
        "QnnInterface_getProviders");
    if (get_providers == nullptr) {
        error = "QnnInterface_getProviders not found in backend library.";
        dlclose(backend_handle);
        return false;
    }

    const QnnInterface_t** providers = nullptr;
    uint32_t num_providers = 0;
    if (get_providers(&providers, &num_providers) != QNN_SUCCESS || providers == nullptr ||
        num_providers == 0) {
        error = "Failed to resolve QNN interface providers.";
        dlclose(backend_handle);
        return false;
    }

    bool found = false;
    for (uint32_t i = 0; i < num_providers; ++i) {
        if (providers[i] == nullptr) {
            continue;
        }
        // Provider must be at least the header version: newer minors are backward
        // compatible, older ones may lack entries this code calls.
        if (providers[i]->apiVersion.coreApiVersion.major == QNN_API_VERSION_MAJOR &&
            providers[i]->apiVersion.coreApiVersion.minor >= QNN_API_VERSION_MINOR) {
            apis.core = providers[i]->QNN_INTERFACE_VER_NAME;
            found = true;
            break;
        }
    }
    if (!found) {
        error = "No compatible QNN core interface found.";
        dlclose(backend_handle);
        return false;
    }

    *backend_handle_out = backend_handle;
    return true;
}

bool acquireEnclaveQnnSystem(
    const std::string& system_path,
    EnclaveQnnApis& apis,
    void** system_handle_out,
    std::string& error) {
    std::lock_guard<std::mutex> lock(g_system_mutex);
    if (g_system_refcount > 0) {
        apis.system = g_system_interface;
        *system_handle_out = g_system_handle;
        ++g_system_refcount;
        return true;
    }

    void* system_handle = dlopen(system_path.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (system_handle == nullptr) {
        error = std::string("Unable to load QNN system library: ") + dlerror();
        return false;
    }

    auto get_providers = resolveSymbol<QnnSystemInterfaceGetProvidersFn>(
        system_handle,
        "QnnSystemInterface_getProviders");
    if (get_providers == nullptr) {
        error = "QnnSystemInterface_getProviders not found in system library.";
        dlclose(system_handle);
        return false;
    }

    const QnnSystemInterface_t** providers = nullptr;
    uint32_t num_providers = 0;
    if (get_providers(&providers, &num_providers) != QNN_SUCCESS || providers == nullptr ||
        num_providers == 0) {
        error = "Failed to resolve QNN system interface providers.";
        dlclose(system_handle);
        return false;
    }

    bool found = false;
    for (uint32_t i = 0; i < num_providers; ++i) {
        if (providers[i] == nullptr) {
            continue;
        }
        if (providers[i]->systemApiVersion.major == QNN_SYSTEM_API_VERSION_MAJOR &&
            providers[i]->systemApiVersion.minor >= QNN_SYSTEM_API_VERSION_MINOR) {
            g_system_interface = providers[i]->QNN_SYSTEM_INTERFACE_VER_NAME;
            found = true;
            break;
        }
    }
    if (!found) {
        error = "No compatible QNN system interface found.";
        dlclose(system_handle);
        return false;
    }

    g_system_handle = system_handle;
    g_system_refcount = 1;
    apis.system = g_system_interface;
    *system_handle_out = system_handle;
    return true;
}

void releaseEnclaveQnnSystem(void* system_handle) {
    std::lock_guard<std::mutex> lock(g_system_mutex);
    if (system_handle == nullptr || g_system_refcount <= 0) {
        return;
    }
    --g_system_refcount;
    if (g_system_refcount == 0 && g_system_handle != nullptr) {
        dlclose(g_system_handle);
        g_system_handle = nullptr;
        g_system_interface = {};
    }
}

}  // namespace enclave::diffusion
