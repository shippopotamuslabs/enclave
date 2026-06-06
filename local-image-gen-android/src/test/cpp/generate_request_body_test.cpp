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

    // INF-005 / MUT-004: SDXL policy scheduler "lcm" must reach the JSON body unchanged.
    GenerateRequest sdxlRequest;
    sdxlRequest.prompt = "a bonsai tree";
    sdxlRequest.width = 1024;
    sdxlRequest.height = 1024;
    sdxlRequest.steps = 8;
    sdxlRequest.cfg_scale = 1.5f;
    sdxlRequest.scheduler = "lcm";

    const std::string sdxlBody = build_generate_request_body(sdxlRequest);

    expect_contains(sdxlBody, "\"scheduler\":\"lcm\"", "INF-005");
    expect_not_contains(sdxlBody, "\"scheduler\":\"dpm\"", "MUT-004");

    // SD15 policy scheduler "dpm" must also propagate (contract at native JSON seam).
    GenerateRequest sd15Request;
    sd15Request.prompt = "a mountain landscape";
    sd15Request.width = 512;
    sd15Request.height = 512;
    sd15Request.steps = 20;
    sd15Request.cfg_scale = 7.5f;
    sd15Request.scheduler = "dpm";

    const std::string sd15Body = build_generate_request_body(sd15Request);

    expect_contains(sd15Body, "\"scheduler\":\"dpm\"", "INF-001-scheduler");
    expect_not_contains(sd15Body, "\"scheduler\":\"lcm\"", "INF-001-scheduler-no-lcm");

    if (failures > 0) {
        std::fprintf(stderr, "%d native assertion(s) failed.\n", failures);
        return 1;
    }
    std::printf("All native generate-body assertions passed.\n");
    return 0;
}
