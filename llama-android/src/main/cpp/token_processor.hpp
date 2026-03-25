#ifndef TOKEN_PROCESSOR_HPP
#define TOKEN_PROCESSOR_HPP

#include <string>
#include <string_view>
#include <algorithm>

/**
 * TokenProcessor handles the low-level byte manipulation of tokens coming from llama.cpp.
 * It ensures that only complete UTF-8 sequences are sent to the JNI layer, handles
 * BPE word-start markers, and provides robust detection for thinking tags.
 */
class TokenProcessor {
public:
    TokenProcessor() : buffer("") {}

    /**
     * Appends a raw piece of text to the internal buffer and handles BPE markers.
     */
    void append(const char* piece, int len) {
        if (len < 0) return;
        buffer.append(piece, len);
        handle_bpe_markers();
    }

    /**
     * Extracts all complete and valid UTF-8 characters from the buffer.
     * Invalid bytes are discarded. Incomplete multi-byte characters remain.
     * 
     * If lookahead_tag is provided, it will NOT extract a trailing sequence that
     * matches the start of that tag, allowing split tags to be detected later.
     */
    std::string extract_utf8(std::string_view lookahead_tag = "") {
        std::string result = "";
        size_t processed_idx = 0;
        size_t last_valid_idx = 0;
        
        for (size_t i = 0; i < buffer.length(); ) {
            unsigned char c = (unsigned char)buffer[i];
            int len = 0;
            
            if (c < 0x80) len = 1;
            else if ((c & 0xE0) == 0xC0) len = 2;
            else if ((c & 0xF0) == 0xE0) len = 3;
            else if ((c & 0xF0) == 0xF0) len = 4;
            else {
                // Invalid UTF-8 start byte - skip and discard
                i++;
                processed_idx = i;
                last_valid_idx = i;
                continue;
            }

            if (i + len <= buffer.length()) {
                // Check if the multi-byte sequence is valid
                bool valid = true;
                for (int j = 1; j < len; j++) {
                    if (((unsigned char)buffer[i + j] & 0xC0) != 0x80) {
                        valid = false;
                        break;
                    }
                }

                if (valid) {
                    i += len;
                    last_valid_idx = i;
                } else {
                    // Invalid continuation byte - discard the start byte
                    i++;
                    processed_idx = i;
                    last_valid_idx = i;
                }
            } else {
                // Incomplete multi-byte character, leave in buffer
                break;
            }
        }

        size_t extraction_end = last_valid_idx;

        // If a lookahead tag is provided, check if the buffer ends with a prefix of it.
        // We use string_view to avoid allocations during comparison.
        if (!lookahead_tag.empty() && extraction_end > 0) {
            std::string_view current_view(buffer.data(), extraction_end);
            for (size_t len = std::min(extraction_end, lookahead_tag.length()); len > 0; len--) {
                if (current_view.substr(extraction_end - len) == lookahead_tag.substr(0, len)) {
                    extraction_end -= len;
                    break;
                }
            }
        }

        if (extraction_end > processed_idx) {
            result = buffer.substr(processed_idx, extraction_end - processed_idx);
            buffer.erase(0, extraction_end);
        } else if (processed_idx > 0) {
            // We discarded some invalid bytes but didn't extract anything new
            buffer.erase(0, processed_idx);
        }

        return result;
    }

    /**
     * Checks if the current buffer contains the specified tag.
     */
    bool contains_tag(const std::string& tag) const {
        if (tag.empty()) return false;
        return buffer.find(tag) != std::string::npos;
    }

    /**
     * Clears the internal buffer.
     */
    void clear() {
        buffer.clear();
    }

    /**
     * Returns the current raw buffer content.
     */
    const std::string& get_buffer() const {
        return buffer;
    }

private:
    std::string buffer;

    /**
     * Replaces BPE word-start marker (U+2581 = 0xE2 0x96 0x81) with a standard space.
     */
    void handle_bpe_markers() {
        size_t bpe_pos = 0;
        while ((bpe_pos = buffer.find("\xE2\x96\x81", bpe_pos)) != std::string::npos) {
            buffer.replace(bpe_pos, 3, " ");
            bpe_pos += 1;
        }
    }
};

#endif // TOKEN_PROCESSOR_HPP
