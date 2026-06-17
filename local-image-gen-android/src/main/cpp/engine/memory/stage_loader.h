#pragma once

#include <string>
#include <vector>

namespace enclave::diffusion {

enum class PipelineStage {
    Tokenizer = 0,
    ClipEncode,
    VaeEncode,
    UnetLoop,
    VaeDecode,
};

const char* stageName(PipelineStage stage);

class StageLoader {
 public:
    explicit StageLoader(std::vector<PipelineStage> stages);

    bool empty() const;
    PipelineStage current() const;
    bool advance();
    void reset();
    int index() const;
    const std::vector<PipelineStage>& stages() const;

 private:
    std::vector<PipelineStage> stages_;
    int index_ = 0;
};

/** Ordered stages for txt2img under LOW_RAM policy. */
std::vector<PipelineStage> stagesForTxt2ImgLowRam();

/** Ordered stages for img2img under LOW_RAM policy. */
std::vector<PipelineStage> stagesForImg2ImgLowRam();

/** FULL policy still runs the same stage order but may retain embeddings between requests. */
std::vector<PipelineStage> stagesForMode(bool img2img, bool low_ram);

}  // namespace enclave::diffusion
