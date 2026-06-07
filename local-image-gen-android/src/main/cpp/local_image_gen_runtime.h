#pragma once

#include <string>

namespace local_image_gen {

void set_runtime_paths(const char* native_lib_dir, const char* qnn_runtime_dir);

const std::string& native_lib_dir();
const std::string& qnn_runtime_dir();

/** 0 = missing, 1 = partial, 2 = ready */
int probe_qnn_libs();
bool file_exists_in_dir(const std::string& dir, const char* filename);

bool prepare_qnn_runtime_libs(char* error_out, int error_out_len);
bool qnn_model_dir_ready(const char* model_dir, char* error_out, int error_out_len);
bool mnn_model_dir_ready(const char* model_dir, char* error_out, int error_out_len);

}  // namespace local_image_gen
