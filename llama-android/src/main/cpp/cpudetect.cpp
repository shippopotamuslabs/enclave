// Tiny CPU detector - no SVE flags, no llama.cpp dependency
// This library is loaded FIRST to register the SVE detection function
// Then we decide which llama library to load based on the result
#include <jni.h>
#include <sys/prctl.h>
#include <sys/auxv.h>
#include <android/log.h>

#ifndef PR_SVE_GET_VL
#define PR_SVE_GET_VL 51
#endif

#ifndef AT_HWCAP2
#define AT_HWCAP2 26
#endif

#ifndef HWCAP2_SVE
#define HWCAP2_SVE (1 << 1)
#endif

extern "C"
JNIEXPORT jint JNICALL
Java_com_browntowndev_pocketcrew_feature_inference_llama_JniLlamaEngine_detectSveBits(
        JNIEnv*,
        jobject) {
    // Method 1: Try prctl to get actual SVE vector length
    int vl_bytes = prctl(PR_SVE_GET_VL, 0, 0, 0, 0);

    if (vl_bytes >= 0) {
        // Mask to get just the vector length (low 16 bits)
        vl_bytes = vl_bytes & 0xFFFF;
        int vl_bits = vl_bytes * 8;

        __android_log_print(ANDROID_LOG_INFO, "cpudetect", "SVE: prctl returned %d bytes (%d bits)",
            vl_bytes, vl_bits);

        if (vl_bits > 0) {
            return vl_bits;
        }
    }

    // Method 2: Fallback - check AT_HWCAP2 for SVE capability flag
    // This works on some devices where prctl doesn't expose vector length
    __android_log_print(ANDROID_LOG_INFO, "cpudetect", "SVE: prctl failed, trying getauxval fallback");

    unsigned long hwcap2 = getauxval(AT_HWCAP2);
    if (hwcap2 & HWCAP2_SVE) {
        __android_log_print(ANDROID_LOG_INFO, "cpudetect", "SVE: detected via AT_HWCAP2, assuming 512-bit");
        return 512;  // Assume 512-bit SVE for ARMv9 cores
    }

    __android_log_print(ANDROID_LOG_INFO, "cpudetect", "SVE: not available");
    return 0;
}
