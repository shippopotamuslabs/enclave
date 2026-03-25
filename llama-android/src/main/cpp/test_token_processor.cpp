#include "token_processor.hpp"
#include <iostream>
#include <cassert>
#include <vector>

void test_basic_string() {
    std::cout << "Running test_basic_string..." << std::endl;
    TokenProcessor processor;
    std::string input = "Hello World";
    processor.append(input.c_str(), input.length());
    std::string output = processor.extract_utf8();
    assert(output == "Hello World");
    assert(processor.get_buffer().empty());
}

void test_split_emoji() {
    std::cout << "Running test_split_emoji..." << std::endl;
    TokenProcessor processor;
    
    // "😁" is F0 9F 98 81
    const char* part1 = "\xF0\x9F";
    const char* part2 = "\x98\x81";
    
    processor.append(part1, 2);
    std::string out1 = processor.extract_utf8();
    assert(out1.empty());
    assert(processor.get_buffer() == part1);
    
    processor.append(part2, 2);
    std::string out2 = processor.extract_utf8();
    assert(out2 == "😁");
    assert(processor.get_buffer().empty());
}

void test_bpe_marker() {
    std::cout << "Running test_bpe_marker..." << std::endl;
    TokenProcessor processor;
    
    // BPE word-start marker is U+2581 (E2 96 81)
    const char* input = "\xE2\x96\x81word";
    processor.append(input, 7);
    std::string output = processor.extract_utf8();
    assert(output == " word");
    assert(processor.get_buffer().empty());
}

void test_split_bpe_marker() {
    std::cout << "Running test_split_bpe_marker..." << std::endl;
    TokenProcessor processor;
    
    processor.append("\xE2", 1);
    std::string out1 = processor.extract_utf8();
    assert(out1.empty());
    
    processor.append("\x96\x81", 2);
    std::string out2 = processor.extract_utf8();
    assert(out2 == " ");
    assert(processor.get_buffer().empty());
}

void test_split_tag_detection() {
    std::cout << "Running test_split_tag_detection..." << std::endl;
    TokenProcessor processor;
    std::string tag = "</think>";
    
    processor.append("This is a ", 10);
    assert(!processor.contains_tag(tag));
    processor.extract_utf8(tag);
    
    processor.append("</thi", 5);
    assert(!processor.contains_tag(tag));
    // extract_utf8(tag) should NOT extract "</thi" because it's a prefix of tag
    std::string output1 = processor.extract_utf8(tag);
    assert(output1.empty());
    assert(processor.get_buffer() == "</thi");
    
    processor.append("nk> more text", 13);
    assert(processor.contains_tag(tag));
    
    // Once tag is found, we extract without lookahead
    std::string output2 = processor.extract_utf8("");
    assert(output2 == "</think> more text");
    assert(processor.get_buffer().empty());
}

void test_invalid_utf8_recovery() {
    std::cout << "Running test_invalid_utf8_recovery..." << std::endl;
    TokenProcessor processor;
    
    // Invalid byte followed by valid string
    const char* input = "\xFFHello";
    processor.append(input, 6);
    std::string output = processor.extract_utf8();
    assert(output == "Hello");
}

void test_complex_mixing() {
    std::cout << "Running test_complex_mixing..." << std::endl;
    TokenProcessor processor;
    std::string tag = "</think>";
    
    // Partial BPE + Partial Emoji + Tag
    processor.append("\xE2", 1); // Start of BPE
    processor.append("\x96\x81", 2); // Finish BPE -> " "
    processor.append("\xF0\x9F", 2); // Start of Emoji
    
    assert(processor.get_buffer() == " \xF0\x9F");
    
    processor.append("\x98\x81</thi", 7);
    assert(!processor.contains_tag(tag));
    
    // Should extract " 😁" but PROTECT "</thi"
    std::string output = processor.extract_utf8(tag);
    assert(output == " 😁");
    assert(processor.get_buffer() == "</thi");
    
    processor.append("nk>", 3);
    assert(processor.contains_tag(tag));
    // Once tag is found, we extract without lookahead (simulating engine stopping)
    assert(processor.extract_utf8("") == "</think>");
}

int main() {
    try {
        test_basic_string();
        test_split_emoji();
        test_bpe_marker();
        test_split_bpe_marker();
        test_split_tag_detection();
        test_invalid_utf8_recovery();
        test_complex_mixing();
        
        std::cout << "\nALL TESTS PASSED!" << std::endl;
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "Test failed with exception: " << e.what() << std::endl;
        return 1;
    }
}
