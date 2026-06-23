use std::env;
use std::path::{Path, PathBuf};

fn cargo_home() -> PathBuf {
    env::var("CARGO_HOME")
        .map(PathBuf::from)
        .or_else(|_| env::var("HOME").map(|home| PathBuf::from(home).join(".cargo")))
        .unwrap_or_else(|_| PathBuf::from(".cargo"))
}

/// Locate the vendored llama.cpp tree shipped inside `llama-cpp-sys-2`.
fn find_llama_cpp_src() -> PathBuf {
    let version = env::var("CARGO_PKG_VERSION")
        .ok()
        .and_then(|_| read_llama_sys_version_from_lock())
        .unwrap_or_else(|| "0.1.150".to_string());

    let pattern = cargo_home()
        .join("registry/src")
        .join("*")
        .join(format!("llama-cpp-sys-2-{version}"))
        .join("llama.cpp");

    glob::glob(pattern.to_string_lossy().as_ref())
        .expect("valid glob pattern")
        .next()
        .and_then(Result::ok)
        .unwrap_or_else(|| {
            panic!(
                "could not find llama.cpp sources for llama-cpp-sys-2-{version} under {}",
                cargo_home().display()
            )
        })
}

fn read_llama_sys_version_from_lock() -> Option<String> {
    let manifest_dir = PathBuf::from(env::var("CARGO_MANIFEST_DIR").ok()?);
    let lock_paths = [
        manifest_dir.join("../../Cargo.lock"),
        manifest_dir.join("../Cargo.lock"),
        manifest_dir.join("Cargo.lock"),
    ];

    for lock_path in lock_paths {
        if !lock_path.is_file() {
            continue;
        }
        let lock = std::fs::read_to_string(&lock_path).ok()?;
        let mut in_sys = false;
        for line in lock.lines() {
            if line.trim() == "name = \"llama-cpp-sys-2\"" {
                in_sys = true;
                continue;
            }
            if in_sys && line.trim_start().starts_with("version = ") {
                return line.split('"').nth(1).map(str::to_string);
            }
            if in_sys && line.starts_with("[[package]]") {
                break;
            }
        }
    }
    None
}

/// Debian/Ubuntu `nvidia-cuda-toolkit` installs static CUDA libs under
/// `/usr/lib/<triplet>` instead of `/usr/local/cuda/lib64`. `llama-cpp-sys-2`
/// uses `find_cuda_helper`, which only searches the latter — supplement the
/// linker search path when distro packages are present.
fn emit_distro_cuda_link_search() {
    println!("cargo:rerun-if-env-changed=CUDA_LIBRARY_PATH");

    if env::var("CUDA_LIBRARY_PATH").is_ok() {
        return;
    }

    let target = env::var("TARGET").unwrap_or_default();
    let mut candidates: Vec<PathBuf> = Vec::new();

    if target.contains("linux") {
        if target.contains("x86_64") {
            candidates.push(PathBuf::from("/usr/lib/x86_64-linux-gnu"));
            candidates.push(PathBuf::from("/usr/lib64"));
        } else if target.contains("aarch64") {
            candidates.push(PathBuf::from("/usr/lib/aarch64-linux-gnu"));
        }
        candidates.push(PathBuf::from("/opt/cuda/lib64"));
        candidates.push(PathBuf::from("/usr/local/cuda/lib64"));
    }

    for dir in candidates {
        let cudart = dir.join("libcudart_static.a");
        if cudart.is_file() {
            println!("cargo:rustc-link-search=native={}", dir.display());
            println!("cargo:rerun-if-changed={}", cudart.display());
            return;
        }
    }
}

fn main() {
    if env::var("CARGO_FEATURE_CUDA").is_ok() {
        emit_distro_cuda_link_search();
    }

    println!("cargo:rerun-if-changed=cpp/chat_template_jinja.cpp");
    println!("cargo:rerun-if-changed=cpp/chat_template_jinja.h");

    let llama_src = find_llama_cpp_src();
    let cpp_dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("cpp");

    cc::Build::new()
        .cpp(true)
        .file(cpp_dir.join("chat_template_jinja.cpp"))
        .include(&llama_src)
        .include(llama_src.join("common"))
        .include(llama_src.join("include"))
        .include(llama_src.join("ggml/include"))
        .include(llama_src.join("vendor"))
        .flag_if_supported("-std=c++17")
        // llama.cpp Jinja headers declare static helpers that our wrapper does not use.
        .flag_if_supported("-Wno-unused-function")
        .compile("enclave_jinja_chat");
}
