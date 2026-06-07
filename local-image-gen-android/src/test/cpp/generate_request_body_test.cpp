#include "../../main/cpp/local_image_gen_backend_client.h"

#include <cassert>
#include <cstring>
#include <iostream>
#include <string>

namespace {

bool contains(const std::string& haystack, const char* needle) {
    return haystack.find(needle) != std::string::npos;
}

void test_juggernaut_scheduler_is_dpm() {
    local_image_gen::GenerateRequest request{};
    request.width = 1024;
    request.height = 1024;
    request.steps = 24;
    request.cfg_scale = 4.0f;
    request.scheduler = "dpm";

    const std::string body = local_image_gen::build_generate_request_body(request);

    assert(contains(body, R"("scheduler":"dpm")"));
    assert(contains(body, R"("steps":24)"));
    assert(contains(body, R"("cfg_scale":4)"));
    assert(!contains(body, R"("scheduler":"lcm")"));
}

void test_mut005_rejects_hardcoded_lcm_for_juggernaut() {
    local_image_gen::GenerateRequest request{};
    request.scheduler = "dpm";

    const std::string body = local_image_gen::build_generate_request_body(request);

    // A lazy SDXL policy reuse would emit lcm; Juggernaut must stay on dpm.
    assert(!contains(body, R"("scheduler":"lcm")"));
    assert(contains(body, R"("scheduler":"dpm")"));
}

}  // namespace

int main() {
    test_juggernaut_scheduler_is_dpm();
    test_mut005_rejects_hardcoded_lcm_for_juggernaut();
    std::cout << "generate_request_body_test passed\n";
    return 0;
}
