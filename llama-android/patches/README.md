# llama.cpp patches

`scripts/fetch-native-deps.sh` fetches upstream llama.cpp at the pinned
commit and applies every patch in this directory, in filename order of
application as listed here. The patched tree is the tree the committed
`libllama-jni-sve.so` was built from; building against unpatched upstream
changes the native behavior the runtime dispatch relies on.

## llama.cpp-b8241-i8mm-runtime.patch

- Base: ggml-org/llama.cpp tag `b8241`
  (`62b8143ad217fc7d3404119a9440be4a18ce31e0`).
- Scope: `ggml/src/ggml-cpu/ggml-cpu.c` only, 16 lines.
- What it does: makes the I8MM capability check in
  `ggml_cpu_has_matmul_int8()` a runtime check, so a build compiled with
  `-march=...+i8mm` stays safe on CPUs that lack I8MM. The CMake build
  already assumes this gating exists (see the ARM feature comment in
  `llama-android/src/main/cpp/CMakeLists.txt`).

## Producing the patch file

The patch is exported from the donor working tree (the pre-migration
checkout where the modification was made):

```sh
# In the donor checkout, whose third_party/llama.cpp is the patched tree:
git -C third_party/llama.cpp diff -- ggml/src/ggml-cpu/ggml-cpu.c \
  > <this-repo>/llama-android/patches/llama.cpp-b8241-i8mm-runtime.patch
```

The diff must apply cleanly to a pristine `b8241` tree with `patch -p1`;
the fetch script applies it with `--forward` and stamps the tree once
applied.
