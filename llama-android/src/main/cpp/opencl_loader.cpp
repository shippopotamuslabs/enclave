// OpenCL dynamic loader for Android
// Uses dlopen to load libOpenCL.so at runtime from vendor paths
// This avoids static linking issues with Android's strict linker namespaces

#include <jni.h>
#include <dlfcn.h>
#include <string>
#include <vector>
#include <android/log.h>

// Basic OpenCL types - define ourselves to avoid header dependency
typedef int32_t cl_int;
typedef uint32_t cl_uint;
typedef int64_t cl_long;
typedef uint64_t cl_ulong;
typedef size_t cl_size_t;
typedef void* cl_platform_id;
typedef void* cl_device_id;
typedef void* cl_context;
typedef void* cl_program;
typedef void* cl_mem;

#define CL_SUCCESS 0
#define CL_DEVICE_TYPE_GPU 4

// OpenCL enums
typedef enum {
    CL_PLATFORM_NAME = 0x0902,
    CL_PLATFORM_VENDOR = 0x0903,
    CL_DEVICE_NAME = 0x102B,
    CL_DEVICE_TYPE = 0x1000,
    CL_DEVICE_VENDOR = 0x102C,
    CL_PROGRAM_BUILD_LOG = 0x1184
} cl_platform_info;

typedef enum {
    CL_PROGRAM_BUILD_STATUS = 0x1183
} cl_program_build_info;

// OpenCL function pointer types
typedef cl_int (*PFN_clGetPlatformIDs)(cl_uint, cl_platform_id*, cl_uint*);
typedef cl_int (*PFN_clGetPlatformInfo)(cl_platform_id, cl_platform_info, size_t, void*, size_t*);
typedef cl_int (*PFN_clGetDeviceIDs)(cl_platform_id, cl_uint, cl_uint, cl_device_id*, cl_uint*);
typedef cl_int (*PFN_clGetDeviceInfo)(cl_device_id, cl_uint, size_t, void*, size_t*);
typedef cl_context (*PFN_clCreateContext)(const void*, cl_uint, const cl_device_id*, void (*)(const char*, const void*, size_t, void*), void*, cl_int*);
typedef cl_program (*PFN_clCreateProgramWithSource)(cl_context, cl_uint, const char**, const size_t*, cl_int*);
typedef cl_int (*PFN_clBuildProgram)(cl_program, cl_uint, const cl_device_id*, const char*, void (*)(cl_program, void*), void*);
typedef cl_int (*PFN_clGetProgramBuildInfo)(cl_program, cl_device_id, cl_program_build_info, size_t, void*, size_t*);
typedef cl_int (*PFN_clReleaseProgram)(cl_program);
typedef cl_int (*PFN_clReleaseContext)(cl_context);
typedef cl_int (*PFN_clReleaseDevice)(cl_device_id);

struct OpenCLFunctions {
    PFN_clGetPlatformIDs clGetPlatformIDs = nullptr;
    PFN_clGetPlatformInfo clGetPlatformInfo = nullptr;
    PFN_clGetDeviceIDs clGetDeviceIDs = nullptr;
    PFN_clGetDeviceInfo clGetDeviceInfo = nullptr;
    PFN_clCreateContext clCreateContext = nullptr;
    PFN_clCreateProgramWithSource clCreateProgramWithSource = nullptr;
    PFN_clBuildProgram clBuildProgram = nullptr;
    PFN_clGetProgramBuildInfo clGetProgramBuildInfo = nullptr;
    PFN_clReleaseProgram clReleaseProgram = nullptr;
    PFN_clReleaseContext clReleaseContext = nullptr;
    PFN_clReleaseDevice clReleaseDevice = nullptr;

    bool isLoaded() const {
        return clGetPlatformIDs != nullptr && clGetDeviceIDs != nullptr;
    }
};

class OpenCLLoader {
public:
    static OpenCLLoader& getInstance() {
        static OpenCLLoader instance;
        return instance;
    }

    ~OpenCLLoader() {
        unload();
    }

    bool load() {
        if (m_loaded && m_functions.isLoaded()) {
            return true;
        }

        // Try to load from various vendor paths
        const char* libraryPaths[] = {
            "/vendor/lib64/libOpenCL.so",
            "/vendor/lib/libOpenCL.so",
            "/system/vendor/lib64/libOpenCL.so",
            "/system/vendor/lib/libOpenCL.so",
            nullptr
        };

        void* handle = nullptr;
        for (int i = 0; libraryPaths[i] != nullptr; i++) {
            handle = dlopen(libraryPaths[i], RTLD_NOW | RTLD_LOCAL);
            if (handle) {
                __android_log_print(ANDROID_LOG_INFO, "OpenCLLoader", "Loaded OpenCL from: %s", libraryPaths[i]);
                break;
            }
        }

        if (!handle) {
            __android_log_print(ANDROID_LOG_WARN, "OpenCLLoader", "Failed to load OpenCL library from any path");
            return false;
        }

        m_handle = handle;

        // Load function pointers
        m_functions.clGetPlatformIDs = (PFN_clGetPlatformIDs)dlsym(handle, "clGetPlatformIDs");
        m_functions.clGetPlatformInfo = (PFN_clGetPlatformInfo)dlsym(handle, "clGetPlatformInfo");
        m_functions.clGetDeviceIDs = (PFN_clGetDeviceIDs)dlsym(handle, "clGetDeviceIDs");
        m_functions.clGetDeviceInfo = (PFN_clGetDeviceInfo)dlsym(handle, "clGetDeviceInfo");
        m_functions.clCreateContext = (PFN_clCreateContext)dlsym(handle, "clCreateContext");
        m_functions.clCreateProgramWithSource = (PFN_clCreateProgramWithSource)dlsym(handle, "clCreateProgramWithSource");
        m_functions.clBuildProgram = (PFN_clBuildProgram)dlsym(handle, "clBuildProgram");
        m_functions.clGetProgramBuildInfo = (PFN_clGetProgramBuildInfo)dlsym(handle, "clGetProgramBuildInfo");
        m_functions.clReleaseProgram = (PFN_clReleaseProgram)dlsym(handle, "clReleaseProgram");
        m_functions.clReleaseContext = (PFN_clReleaseContext)dlsym(handle, "clReleaseContext");
        m_functions.clReleaseDevice = (PFN_clReleaseDevice)dlsym(handle, "clReleaseDevice");

        if (!m_functions.isLoaded()) {
            __android_log_print(ANDROID_LOG_ERROR, "OpenCLLoader", "Failed to load all OpenCL function pointers");
            unload();
            return false;
        }

        m_loaded = true;
        __android_log_print(ANDROID_LOG_INFO, "OpenCLLoader", "OpenCL loaded successfully");
        return true;
    }

    void unload() {
        if (m_handle) {
            dlclose(m_handle);
            m_handle = nullptr;
        }
        m_functions = OpenCLFunctions();
        m_loaded = false;
    }

    bool isLoaded() const {
        return m_loaded;
    }

    const OpenCLFunctions& getFunctions() const {
        return m_functions;
    }

    // Check if OpenCL is available on this device
    bool isOpenCLAvailable() {
        if (!load()) {
            return false;
        }

        cl_uint numPlatforms = 0;
        cl_int err = m_functions.clGetPlatformIDs(0, nullptr, &numPlatforms);
        if (err != CL_SUCCESS || numPlatforms == 0) {
            __android_log_print(ANDROID_LOG_WARN, "OpenCLLoader", "No OpenCL platforms found");
            return false;
        }

        // Try to get a GPU device
        cl_platform_id platform = nullptr;
        err = m_functions.clGetPlatformIDs(1, &platform, nullptr);
        if (err != CL_SUCCESS) {
            return false;
        }

        cl_uint numDevices = 0;
        err = m_functions.clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 0, nullptr, &numDevices);
        if (err != CL_SUCCESS || numDevices == 0) {
            __android_log_print(ANDROID_LOG_WARN, "OpenCLLoader", "No OpenCL GPU devices found");
            return false;
        }

        __android_log_print(ANDROID_LOG_INFO, "OpenCLLoader", "OpenCL is available with %u GPU device(s)", numDevices);
        return true;
    }

private:
    OpenCLLoader() : m_handle(nullptr), m_loaded(false) {}

    void* m_handle;
    bool m_loaded;
    OpenCLFunctions m_functions;
};
