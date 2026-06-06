#include "engine/memory/stage_loader.h"

#include <cstdio>

using enclave::diffusion::PipelineStage;
using enclave::diffusion::stagesForImg2ImgLowRam;
using enclave::diffusion::stagesForTxt2ImgLowRam;

int main() {
    const auto txt = stagesForTxt2ImgLowRam();
    if (txt.size() != 4 ||
        txt[0] != PipelineStage::Tokenizer ||
        txt[1] != PipelineStage::ClipEncode ||
        txt[2] != PipelineStage::UnetLoop ||
        txt[3] != PipelineStage::VaeDecode) {
        std::fprintf(stderr, "txt2img stage order mismatch\n");
        return 1;
    }

    const auto img = stagesForImg2ImgLowRam();
    if (img.size() != 5 || img[2] != PipelineStage::VaeEncode) {
        std::fprintf(stderr, "img2img stage order mismatch\n");
        return 1;
    }

    std::printf("stage_order_test passed\n");
    return 0;
}
