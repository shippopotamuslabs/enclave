#include "stage_loader.h"

#include <stdexcept>

namespace enclave::diffusion {

namespace {

std::vector<PipelineStage> baseStages(bool img2img) {
    std::vector<PipelineStage> stages = {PipelineStage::Tokenizer, PipelineStage::ClipEncode};
    if (img2img) {
        stages.push_back(PipelineStage::VaeEncode);
    }
    stages.push_back(PipelineStage::UnetLoop);
    stages.push_back(PipelineStage::VaeDecode);
    return stages;
}

}  // namespace

const char* stageName(PipelineStage stage) {
    switch (stage) {
        case PipelineStage::Tokenizer:
            return "tokenizer";
        case PipelineStage::ClipEncode:
            return "clip_encode";
        case PipelineStage::VaeEncode:
            return "vae_encode";
        case PipelineStage::UnetLoop:
            return "unet_loop";
        case PipelineStage::VaeDecode:
            return "vae_decode";
    }
    return "unknown";
}

StageLoader::StageLoader(std::vector<PipelineStage> stages) : stages_(std::move(stages)) {}

bool StageLoader::empty() const { return stages_.empty(); }

PipelineStage StageLoader::current() const {
    if (empty() || index_ < 0 || index_ >= static_cast<int>(stages_.size())) {
        throw std::out_of_range("stage loader is not positioned on a stage");
    }
    return stages_[static_cast<size_t>(index_)];
}

bool StageLoader::advance() {
    if (index_ + 1 >= static_cast<int>(stages_.size())) {
        index_ = static_cast<int>(stages_.size());
        return false;
    }
    ++index_;
    return true;
}

void StageLoader::reset() { index_ = 0; }

int StageLoader::index() const { return index_; }

const std::vector<PipelineStage>& StageLoader::stages() const { return stages_; }

std::vector<PipelineStage> stagesForTxt2ImgLowRam() { return baseStages(false); }

std::vector<PipelineStage> stagesForImg2ImgLowRam() { return baseStages(true); }

std::vector<PipelineStage> stagesForMode(bool img2img, bool /*low_ram*/) {
    return baseStages(img2img);
}

}  // namespace enclave::diffusion
