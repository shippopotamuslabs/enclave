#include "harness_config.h"
#include "harness_probes.h"

#include <iostream>
#include <string>

namespace enclave::harness {
namespace {

int runStep(const std::string& step, HarnessOptions& options) {
    if (step == "validate") {
        return runValidate(options);
    }
    if (step == "qnn-metadata") {
        return runQnnMetadata(options);
    }
    if (step == "clip") {
        return runClipProbe(options);
    }
    if (step == "qnn-init") {
        return runQnnInit(options);
    }
    if (step == "generate") {
        return runGenerate(options);
    }
    std::cerr << "Unknown step: " << step << '\n';
    return 2;
}

int runAll(HarnessOptions& options) {
#ifdef __ANDROID__
    const char* kSteps[] = {"validate", "qnn-metadata", "clip", "qnn-init", "generate"};
#else
    const char* kSteps[] = {"validate", "qnn-metadata", "clip"};
#endif
    for (const char* step : kSteps) {
        std::cout << "\n=== " << step << " ===\n";
        const int rc = runStep(step, options);
        if (rc != 0) {
            return rc;
        }
    }
    return 0;
}

}  // namespace
}  // namespace enclave::harness

int main(int argc, char** argv) {
    enclave::harness::HarnessOptions options;
    std::string error;
    if (!enclave::harness::parseArgs(argc, argv, options, error)) {
        std::cerr << error << '\n';
        enclave::harness::printUsage(argv[0]);
        return 2;
    }

    const char* family_label = "sd15";
    if (options.family == ENCLAVE_MODEL_SDXL_QNN) {
        family_label = "sdxl";
    } else if (options.family == ENCLAVE_MODEL_JUGGERNAUT_XL_QNN) {
        family_label = "juggernaut";
    }
    std::cout << "enclave-diffusion-harness\n"
              << "  model_dir=" << options.model_dir << '\n'
              << "  family=" << family_label << '\n'
              << "  step=" << options.step << '\n'
              << std::flush;

    if (options.step == "all") {
        return enclave::harness::runAll(options);
    }
    return enclave::harness::runStep(options.step, options);
}
