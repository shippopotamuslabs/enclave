//! GPU / backend diagnostics logged at llama.cpp init and model load.

use llama_cpp_2::context::LlamaContext;
use llama_cpp_2::llama_backend::LlamaBackend;
use llama_cpp_2::{list_llama_ggml_backend_devices, LlamaBackendDeviceType};

/// Log backend capabilities and enumerated GGML devices once at startup.
pub fn log_backend_capabilities(backend: &LlamaBackend) {
    let gpu_offload = backend.supports_gpu_offload();
    tracing::info!(
        gpu_offload_supported = gpu_offload,
        mmap_supported = backend.supports_mmap(),
        mlock_supported = backend.supports_mlock(),
        "llama.cpp backend initialized"
    );

    if !gpu_offload {
        tracing::warn!(
            "GPU offload is not available — inference will run on CPU. \
             Rebuild with `--features enclave-desktop-inference/vulkan` (cross-vendor GPU) \
             or `--features enclave-desktop-inference/cuda` (NVIDIA, requires CUDA toolkit)."
        );
    }

    let devices = list_llama_ggml_backend_devices();
    if devices.is_empty() {
        tracing::info!("no GGML backend devices reported");
        return;
    }

    for device in devices {
        let kind = match device.device_type {
            LlamaBackendDeviceType::Cpu => "cpu",
            LlamaBackendDeviceType::Accelerator => "accel",
            LlamaBackendDeviceType::Gpu => "gpu",
            LlamaBackendDeviceType::IntegratedGpu => "igpu",
            LlamaBackendDeviceType::Unknown => "unknown",
        };
        tracing::info!(
            index = device.index,
            name = %device.name,
            description = %device.description,
            backend = %device.backend,
            device_type = kind,
            memory_total = %format_bytes(device.memory_total),
            memory_free = %format_bytes(device.memory_free),
            "GGML backend device"
        );
    }
}

/// Log the GPU layer offload request before loading weights.
pub fn log_model_load_plan(n_gpu_layers: u32, gpu_offload_supported: bool) {
    let requested = if n_gpu_layers == u32::MAX {
        "all".to_string()
    } else {
        n_gpu_layers.to_string()
    };

    tracing::info!(
        requested_gpu_layers = %requested,
        gpu_offload_supported,
        "loading GGUF model"
    );

    if gpu_offload_supported {
        tracing::info!(
            "requesting full GPU layer offload (n_gpu_layers = -1); \
             watch for llama.cpp lines like \"offloaded X/Y layers to GPU\""
        );
    }
}

/// After context creation, emit llama.cpp's per-device memory breakdown.
pub fn log_context_memory_breakdown(context: &LlamaContext) {
    tracing::info!("model memory breakdown (per device):");
    context.print_memory_breakdown();
}

pub fn format_bytes(bytes: usize) -> String {
    const KIB: f64 = 1024.0;
    const MIB: f64 = KIB * 1024.0;
    const GIB: f64 = MIB * 1024.0;
    let n = bytes as f64;
    if n >= GIB {
        format!("{:.2} GiB", n / GIB)
    } else if n >= MIB {
        format!("{:.0} MiB", n / MIB)
    } else if n >= KIB {
        format!("{:.0} KiB", n / KIB)
    } else {
        format!("{bytes} B")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn format_bytes_scales_units() {
        assert_eq!(format_bytes(512), "512 B");
        assert_eq!(format_bytes(2048), "2 KiB");
        assert_eq!(format_bytes(5 * 1024 * 1024), "5 MiB");
    }
}
