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
        if (len <= 0) return;
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
        size_t i = 0;
        size_t last_extraction_point = 0;
        
        while (i < buffer.length()) {
            unsigned char c = (unsigned char)buffer[i];
            int len = 0;
            
            if (c < 0x80) {
                len = 1;
            } else if ((c & 0xE0) == 0xC0 && (c & 0x1E) != 0) {
                len = 2;
            } else if ((c & 0xF0) == 0xE0) {
                len = 3;
            } else if ((c & 0xF8) == 0xF0 && (c & 0x07) <= 0x04) {
                len = 4;
            } else {
                // Invalid UTF-8 start byte - skip and discard
                i++;
                last_extraction_point = i;
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
                    // Valid character!
                    // Check if this (and everything before it) would be a prefix of the tag
                    // if it's at the very end of the buffer.
                    if (!lookahead_tag.empty()) {
                        std::string_view remaining(buffer.data() + i, buffer.length() - i);
                        bool is_tag_prefix = false;
                        for (size_t l = 1; l <= std::min(remaining.length(), lookahead_tag.length()); l++) {
                            if (remaining.substr(0, l) == lookahead_tag.substr(0, l) && (i + l == buffer.length())) {
                                is_tag_prefix = true;
                                break;
                            }
                        }
                        if (is_tag_prefix) {
                            // Stop here to protect the potential tag
                            break;
                        }
                    }

                    result.append(buffer.substr(i, len));
                    i += len;
                    last_extraction_point = i;
                } else {
                    // Invalid continuation byte - discard the start byte
                    i++;
                    last_extraction_point = i;
                }
            } else {
                // Incomplete multi-byte character - leave in buffer
                break;
            }
        }

        if (last_extraction_point > 0) {
            buffer.erase(0, last_extraction_point);
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
