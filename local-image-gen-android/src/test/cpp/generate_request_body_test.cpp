// Native unit test for the `/generate` JSON request body builder.
//
// Deterministic seam for scenarios INF-005 and MUT-004: asserts the C++ backend
// JSON body honours the caller-provided scheduler instead of always emitting
// "scheduler":"dpm". Runs on the host toolchain (no Android/NDK headers, no gtest
// dependency) via the CMake project in this directory.
//
// TDD Red: build_generate_request_body() currently hardcodes "dpm", so both
// assertions below fail until the Green phase emits request.scheduler.

#include "local_image_gen_backend_client.h"

#include <cstdio>
#include <string>

namespace {

int failures = 0;

void expect_contains(const std::string& haystack, const std::string& needle, const char* scenario) {
    if (haystack.find(needle) == std::string::npos) {
        std::fprintf(stderr, "[%s] FAILED: expected body to contain %s\n  body: %s\n",
                     scenario, needle.c_str(), haystack.c_str());
        ++failures;
    }
}

void expect_not_contains(const std::string& haystack, const std::string& needle, const char* scenario) {
    if (haystack.find(needle) != std::string::npos) {
        std::fprintf(stderr, "[%s] FAILED: expected body NOT to contain %s\n  body: %s\n",
                     scenario, needle.c_str(), haystack.c_str());
        ++failures;
    }
}

}  // namespace

int main() {
    using local_image_gen::GenerateRequest;
    using local_image_gen::build_generate_request_body;

    // INF-005: an SDXL request carries scheduler = "lcm".
    GenerateRequest request;
    request.prompt = "a bonsai tree";
    request.width = 1024;
    request.height = 1024;
    request.steps = 8;
    request.cfg_scale = 1.5f;
    request.scheduler = "lcm";

    const std::string body = build_generate_request_body(request);

    // INF-005 / MUT-004: the JSON body must contain "scheduler":"lcm"
    // and must NOT contain "scheduler":"dpm".
    expect_contains(body, "\"scheduler\":\"lcm\"", "INF-005");
    expect_not_contains(body, "\"scheduler\":\"dpm\"", "MUT-004");

    if (failures > 0) {
        std::fprintf(stderr, "%d native assertion(s) failed.\n", failures);
        return 1;
    }
    std::printf("All native generate-body assertions passed.\n");
    return 0;
}
