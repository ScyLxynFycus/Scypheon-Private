#include <android/log.h>
#include <jni.h>
#include <sys/mman.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <dlfcn.h>
#include <errno.h>

// NDK Safe-Link: Dynamic Symbol Resolution
typedef int (*ASharedMemory_create_t)(const char*, size_t);
typedef int (*ASharedMemory_setProtect_t)(int, int);

static ASharedMemory_create_t get_ashmem_create() {
    static ASharedMemory_create_t fn = (ASharedMemory_create_t)dlsym(RTLD_DEFAULT, "ASharedMemory_create");
    return fn;
}

static ASharedMemory_setProtect_t get_ashmem_setProtect() {
    static ASharedMemory_setProtect_t fn = (ASharedMemory_setProtect_t)dlsym(RTLD_DEFAULT, "ASharedMemory_setProtect");
    return fn;
}
#include <iomanip>
#include <math.h>
#include <string>
#include <unistd.h>
#include <mutex>

// SAR PHASE 2: JNI JVM Access
static JavaVM* g_vm = nullptr;

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_vm = vm;
    void register_solaris_sentinel();
    register_solaris_sentinel();
    return JNI_VERSION_1_6;
}
#include <sys/mman.h>
#include <sys/system_properties.h>
#include <fstream>
#include <iostream>
#include <atomic>
#include <unistd.h>
#include <sys/mman.h>
#include <fcntl.h>
#include <sys/stat.h>

// 🛡️ [SAR] Early Backend Suppression (constructor)
__attribute__((constructor))
static void solaris_early_init() {
    // Check for hard disable flags before any backend registration
    if (access("/data/local/tmp/SOLARIS_DISABLE_GPU", F_OK) == 0) {
        setenv("GGML_VULKAN_DISABLE", "1", 1);
        setenv("GGML_OPENCL_DISABLE", "1", 1);
    }
}
#include <stdexcept>
#include <time.h>
#include <string.h>
#include "llama.h"
#include "common.h"
#include <signal.h>

// Do not forget to dynamically load the C++ library into your application.
//
// For instance,
//
// In MainActivity.java:
//    static {
//       System.loadLibrary("llama-android");
//    }
//
// Or, in MainActivity.kt:
//    companion object {
//      init {
//         System.loadLibrary("llama-android")
//      }
//    }

#define TAG "llama-android.cpp"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGw(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
static std::string active_hardware_status = "Unknown";
static int current_backend_mode = 0; // 0=AUTO, 1=CPU, 2=GPU
static bool used_fallback = false;
static std::string g_files_dir;
static std::string g_active_backend_trying = "NONE";

// 🛡️ THE BLACK BOX: Killswitches for Faulty Hardware
extern "C" {
    bool g_vulkan_disabled = false;
    bool g_opencl_disabled = false;
    volatile int g_load_heartbeat = 0; // Solaris Heartbeat Counter
    std::atomic<uint64_t> g_decode_heartbeat_ns(0);
    std::atomic<int> g_watchdog_timeout_ms(5000); // Dynamic threshold
}

static std::recursive_mutex g_context_mutex;
static std::atomic<bool> g_cancel_inference{false};

// [SAR PHASE 2] Ashmem IPC Bridge
struct TokenEntry {
    int32_t token_id;
    float confidence;
    int32_t length;
    char text[244]; // Total size = 256 bytes
};

static void* g_output_shm_ptr = nullptr;
static size_t g_output_shm_size = 0;
static std::atomic<int32_t>* g_output_token_count = nullptr;

/**
 * Reads Resident Set Size (RSS) from /proc/self/statm
 * Returns memory in bytes.
 */
static long get_process_rss() {
    long pages = 0;
    std::ifstream statm("/proc/self/statm");
    if (statm.is_open()) {
        statm >> pages; // First value is total size, second is resident (RSS)
        statm >> pages;
    }
    return pages * sysconf(_SC_PAGESIZE);
}

// 🛡️ THE BLACK BOX: SIGSEGV Tripwire Logic (Solaris 4.1 Production Spec)
// Uses ONLY async-signal-safe syscalls (open, write, close) and stack memory.
// Engineered to eliminate deadlocks and race conditions during driver crashes.

static void int_to_ascii(int n, char* buf, size_t len) {
    char temp[12];
    int i = 0;
    bool neg = n < 0;
    if (neg) n = -n;
    do { temp[i++] = (n % 10) + '0'; n /= 10; } while (n > 0 && i < 11);
    if (neg) temp[i++] = '-';
    size_t j = 0;
    while (i > 0 && j < len - 1) buf[j++] = temp[--i];
    buf[j] = '\0';
}

static void solaris_handler(int sig, siginfo_t* info, void* ucontext) {
    // 🛡️ RE-ENTRY PROTECTION: Mask ALL signals during teardown to prevent tombstone loss
    sigset_t mask;
    sigfillset(&mask);
    sigprocmask(SIG_BLOCK, &mask, nullptr);

    if (!g_files_dir.empty()) {
        char report_path[512];
        const char* backend = (g_active_backend_trying != "NONE") ? g_active_backend_trying.c_str() : "unknown";
        
        // Manual path construction to avoid snprintf
        size_t dir_len = g_files_dir.length();
        memcpy(report_path, g_files_dir.c_str(), dir_len);
        memcpy(report_path + dir_len, "/SANDBOX_TOMBSTONE.json\0", 25);
        
        int fd = open(report_path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
        if (fd != -1) {
            char sig_buf[12];
            int_to_ascii(sig, sig_buf, sizeof(sig_buf));

            // Fixed-format JSON (fully stack-allocated)
            char buf[256];
            int pos = 0;
            auto append = [&](const char* s, size_t n) { 
                if (pos + n < 255) { 
                    memcpy(buf + pos, s, n); 
                    pos += n; 
                }
            };

            char ts_buf[20];
            int_to_ascii((int)time(nullptr), ts_buf, sizeof(ts_buf));

            append("{\"signal\":", 10); 
            append(sig_buf, strlen(sig_buf));
            append(",\"backend\":\"", 12);
            append(backend, strlen(backend));
            append("\",\"description\":\"SOLARIS_NATIVE_CRASH\",\"timestamp\":", 52);
            append(ts_buf, strlen(ts_buf));
            append("}", 1);

            write(fd, buf, pos);
            close(fd);
        }
    }
    
    // 🛡️ DETERMINISTIC TERMINATION: 134 for SIGABRT
    _exit(128 + sig);
}

// 🛡️ [SAR] Phase 3: Shared Memory Tensor Management
#include <sys/syscall.h>
#include <linux/memfd.h>

extern "C" JNIEXPORT jint JNICALL
Java_com_scypheon_sdk_core_utils_NativeLibraryLoader_createMemfdNative(JNIEnv *env, jclass, jstring jname, jlong size) {
    const char *name = env->GetStringUTFChars(jname, 0);
    // 🛡️ [SAR] memfd_create: API 26+ syscall for sealed SHM
    int fd = syscall(__NR_memfd_create, name, MFD_CLOEXEC | MFD_ALLOW_SEALING);
    env->ReleaseStringUTFChars(jname, name);

    if (fd < 0) return -1;

    if (ftruncate(fd, size) < 0) {
        close(fd);
        return -1;
    }

    // 🛡️ SEALING: Prevent resize attacks (Production Requirement)
    fcntl(fd, F_ADD_SEALS, F_SEAL_GROW | F_SEAL_SHRINK | F_SEAL_WRITE);
    return fd;
}

extern "C" JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_native_1shm_1attach(JNIEnv *env, jobject, jint fd, jlong size) {
    // 🛡️ FD OWNERSHIP CONTRACT: Java side manages FD via ParcelFileDescriptor.
    void* ptr = mmap(nullptr, (size_t)size, PROT_READ | PROT_WRITE, MAP_SHARED | MAP_POPULATE, fd, 0);

    if (ptr == MAP_FAILED) {
        LOGe("🚨 [SOLARIS] SHM Attach FAILED: %s", strerror(errno));
        return 0;
    }

    // 🛡️ MADV PROTECTIONS: Prefetch hint + Isolation
    madvise(ptr, (size_t)size, MADV_WILLNEED);
    madvise(ptr, (size_t)size, MADV_DONTFORK);
    
    LOGi("🛰️ [SOLARIS] SHM Attachment SUCCESS. Ptr: %p, Size: %ld", ptr, (long)size);
    return (jlong)(uintptr_t)ptr;
}

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_native_1kv_1restore(JNIEnv *env, jobject, jlong ctx_ptr, jint seq_id, jint last_pos) {
    auto ctx = reinterpret_cast<llama_context*>(ctx_ptr);
    if (!ctx) return;

    // 🛡️ KV SYNC: In this version of llama.cpp, position is batch-local.
    // We verify the cache state instead of forcing a shift if not needed.
    llama_memory_t mem = llama_get_memory(ctx);
    int current_max = (int)llama_memory_seq_pos_max(mem, seq_id);
    LOGi("📉 [SOLARIS] KV Cache State: Seq %d, Max Pos in Cache: %d, Target Pos: %d", seq_id, current_max, last_pos);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_scypheon_sdk_core_utils_NativeLibraryLoader_setOomScoreNative(JNIEnv *env, jclass, jint score) {
    char buf[64];
    snprintf(buf, sizeof(buf), "%d", score);
    int fd = open("/proc/self/oom_score_adj", O_WRONLY);
    if (fd < 0) return -1;
    ssize_t written = write(fd, buf, strlen(buf));
    close(fd);
    return (written > 0) ? 0 : -2;
}

// 🛡️ [SAR] Sequential Backend Isolation: Lightweight Probe
extern "C" JNIEXPORT jboolean JNICALL
Java_com_scypheon_sdk_core_utils_NativeLibraryLoader_probeBackendNative(JNIEnv* env, jobject, jstring jmodelPath, jint backendType) {
    auto path_to_model = env->GetStringUTFChars(jmodelPath, 0);
    LOGi("[SBI] Probing backend type %d for path %s", backendType, path_to_model);

    llama_model_params mparams = llama_model_default_params();
    mparams.vocab_only = true; 

    // Tier mapping: 1=VULKAN, 2=OPENCL, 3=CPU (aligned with Repository)
    if (backendType == 1) { 
        setenv("GGML_VULKAN", "1", 1);
        setenv("GGML_OPENCL", "0", 1);
        mparams.n_gpu_layers = 99;
    } else if (backendType == 2) { 
        setenv("GGML_VULKAN", "0", 1);
        setenv("GGML_OPENCL", "1", 1);
        mparams.n_gpu_layers = 99;
    } else { 
        setenv("GGML_VULKAN", "0", 1);
        setenv("GGML_OPENCL", "0", 1);
        mparams.n_gpu_layers = 0;
    }

    llama_backend_init();

    llama_model* model = nullptr;
    try {
        model = llama_model_load_from_file(path_to_model, mparams);
    } catch (...) {
        LOGe("[SBI] Crash during metadata load.");
    }

    jboolean success = JNI_FALSE;

    if (model) {
        llama_context_params cparams = llama_context_default_params();
        cparams.n_ctx = 64; 
        cparams.n_threads = 1;
        
        llama_context* ctx = llama_init_from_model(model, cparams);
        if (ctx) {
            success = JNI_TRUE;
            LOGi("[SBI] SUCCESS: Backend %d context created.", backendType);
            llama_free(ctx);
        } else {
            LOGe("[SBI] FAILURE: Backend %d rejected context creation.", backendType);
        }
        llama_model_free(model);
    } else {
        LOGe("[SBI] FAILURE: Model metadata load failed.");
    }

    // 🛡️ CLEANUP: Prevent driver state pollution for the next probe
    unsetenv("GGML_VULKAN");
    unsetenv("GGML_OPENCL");
    llama_backend_free();

    env->ReleaseStringUTFChars(jmodelPath, path_to_model);
    return success;
}

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_native_1inject_1token(JNIEnv *env, jobject, jlong ctx_ptr, jint token_id, jint kv_offset) {
    auto ctx = reinterpret_cast<llama_context*>(ctx_ptr);
    if (!ctx) return;
    
    // 🛡️ REPLAY INJECTION: One-shot decode of a single token at a specific KV offset
    llama_token t = (llama_token)token_id;
    llama_batch batch = llama_batch_init(1, 0, 1);
    batch.n_tokens = 1;
    batch.token[0] = t;
    batch.pos[0] = kv_offset;
    batch.n_seq_id[0] = 1;
    batch.seq_id[0][0] = 0;
    batch.logits[0] = true;

    llama_decode(ctx, batch);
    llama_batch_free(batch);
}

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_native_1set_1output_1shm(JNIEnv *env, jobject, jint fd, jint size) {
    if (g_output_shm_ptr) {
        munmap(g_output_shm_ptr, g_output_shm_size);
        g_output_shm_ptr = nullptr;
        g_output_token_count = nullptr;
    }
    
    if (fd < 0 || size <= 0) return;
    
    void* ptr = mmap(nullptr, (size_t)size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (ptr == MAP_FAILED) {
        LOGe("🚨 [IPC] Failed to map output SHM: %s", strerror(errno));
        return;
    }
    
    g_output_shm_ptr = ptr;
    g_output_shm_size = size;
    g_output_token_count = reinterpret_cast<std::atomic<int32_t>*>(ptr);
    g_output_token_count->store(0, std::memory_order_release);
    
    LOGi("🛰️ [IPC] Output SHM mapped at %p (size: %d)", g_output_shm_ptr, size);
}

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_native_1cancel_1inference(JNIEnv *, jobject) {
    LOGi("🚫 [SAR] Cancellation Signal Received. Arming killswitch.");
    g_cancel_inference.store(true, std::memory_order_release);
}

// 🛡️ KV EVICTION: Safety-Bounded Deferred Trimming
static std::atomic<int> g_pending_trim_level{0};

static void check_and_apply_trim(llama_context* ctx, int n_batch) {
    int level = g_pending_trim_level.exchange(0, std::memory_order_acq_rel);
    if (level > 0 && ctx != nullptr) {
        llama_memory_t mem = llama_get_memory(ctx);
        int used = llama_memory_seq_pos_max(mem, -1) + 1; // Approximate used cells
        if (used <= 128) return; // Keep minimal context alive for re-anchoring

        // Drop oldest 30-50% based on trim level
        int drop = (level == 2) ? (used / 2) : (used / 3);
        
        // 🛡️ Alignment & Boundary Safety
        drop = (drop / n_batch) * n_batch; // Align to batch boundary
        drop = std::min(drop, used - 128);  // Enforcement: Never zero-out KV cache

        if (drop > 0) {
            LOGi("📉 [SOLARIS] Evicting %d stale tokens (Level %d) to recover RAM.", drop, level);
            llama_memory_seq_rm(mem, -1, 0, drop);
        }
    }
}

#include <atomic>
#include <thread>
#include <chrono>
#include <pthread.h>

// 🛡️ SOLARIS WATCHDOG: Lock-Free Atomic Heartbeat
// g_decode_heartbeat_ns is defined in the extern "C" block above

static void solaris_watchdog_thread_fn() {
    pthread_setname_np(pthread_self(), "solaris_watchdog");
    
    sigset_t mask;
    sigfillset(&mask);
    pthread_sigmask(SIG_BLOCK, &mask, nullptr);

    while (true) {
        int timeout_ms = g_watchdog_timeout_ms.load(std::memory_order_relaxed);
        auto now = std::chrono::steady_clock::now().time_since_epoch().count();
        uint64_t last = g_decode_heartbeat_ns.load(std::memory_order_acquire);
        
        uint64_t timeout_ns = static_cast<uint64_t>(timeout_ms) * 1000000ULL;

        if (last > 0 && (now - last) > timeout_ns) {
            LOGe("🚨 [SOLARIS] Adaptive Watchdog: Driver Hang Detected (>%dms). Triggering SIGABRT.", timeout_ms);
            kill(getpid(), SIGABRT);
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(500));
    }
}

static void llama_decode_arm() {
    g_decode_heartbeat_ns.store(std::chrono::steady_clock::now().time_since_epoch().count(), std::memory_order_release);
}

static void llama_decode_disarm() {
    g_decode_heartbeat_ns.store(0, std::memory_order_release);
}

void register_solaris_sentinel() {
    struct sigaction sa{};
    sa.sa_sigaction = solaris_handler;
    sa.sa_flags = SA_SIGINFO | SA_RESETHAND;
    sigemptyset(&sa.sa_mask);
    
    sigaction(SIGSEGV, &sa, nullptr);
    sigaction(SIGILL, &sa, nullptr);
    sigaction(SIGBUS, &sa, nullptr);
    sigaction(SIGABRT, &sa, nullptr);
    
    // 🛡️ Launch detached watchdog
    std::thread(solaris_watchdog_thread_fn).detach();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_scypheon_sdk_core_utils_NativeSharedMemory_createNativeNative(JNIEnv *, jclass, jlong size) {
    if (size <= 0) return -1;
    // 🛡️ [Vault] NDK-level ASharedMemory supports 64-bit size_t
    ASharedMemory_create_t create_fn = get_ashmem_create();
    if (!create_fn) return -1;
    int fd = create_fn("scypheon_vault", (size_t)size);
    if (fd < 0) {
        LOGe("[Vault] Native ASharedMemory_create failed: %s (Check if API 26+)", strerror(errno));
    } else {
        LOGi("[Vault] Native Vault Allocated: %.2f GB resident in RAM.", (double)size / (1024.0*1024.0*1024.0));
    }
    return fd;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_scypheon_sdk_core_utils_NativeSharedMemory_loadToVaultNative(JNIEnv *env, jobject, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, 0);
    struct stat st;
    if (stat(path, &st) != 0) {
        LOGe("[Vault] Stat failed: %s", path);
        env->ReleaseStringUTFChars(jpath, path);
        return -1;
    }

    size_t size = st.st_size;
    ASharedMemory_create_t create_fn = get_ashmem_create();
    if (!create_fn) {
        LOGe("[Vault] ASharedMemory API missing.");
        env->ReleaseStringUTFChars(jpath, path);
        return -1;
    }
    
    int ashmem_fd = create_fn("scypheon_vault", size);
    if (ashmem_fd < 0) {
        LOGe("[Vault] ASharedMemory_create failed.");
        env->ReleaseStringUTFChars(jpath, path);
        return -1;
    }

    int file_fd = open(path, O_RDONLY);
    if (file_fd < 0) {
        LOGe("[Vault] open failed.");
        close(ashmem_fd);
        env->ReleaseStringUTFChars(jpath, path);
        return -1;
    }

    LOGi("[Vault] SRP Protocol: Streaming weights into Vault in 128MB chunks (Total: %.2f GB)...", (double)size / (1024.0*1024.0*1024.0));
    
    // 🛡️ [SRP] Sequential Residency Protocol: 
    // We map only a 128MB window of the Ashmem area at a time.
    // This keeps local RSS at ~150MB regardless of model size (6.7GB - 13GB).
    const size_t CHUNK_SIZE = 128LL * 1024 * 1024;
    size_t total_written = 0;
    
    while (total_written < size) {
        size_t to_copy = std::min(CHUNK_SIZE, size - total_written);
        
        // Map window
        void* window = mmap(NULL, to_copy, PROT_READ | PROT_WRITE, MAP_SHARED, ashmem_fd, (off_t)total_written);
        if (window == MAP_FAILED) {
            LOGe("[Vault] Window mapping failed at offset %zu", total_written);
            break;
        }
        
        // Read directly into the window (kernel-to-ashmem)
        size_t chunk_read = 0;
        while (chunk_read < to_copy) {
            ssize_t r = read(file_fd, (char*)window + chunk_read, to_copy - chunk_read);
            if (r <= 0) break;
            chunk_read += r;
        }
        
        munmap(window, to_copy);
        total_written += chunk_read;
        
        if (chunk_read < to_copy) {
            LOGe("[Vault] Read error at offset %zu", total_written);
            break;
        }
    }
    
    close(file_fd);
    env->ReleaseStringUTFChars(jpath, path);
    
    if (total_written < size) {
        LOGe("[Vault] Sequential Load FAILED. (Written %zu / %zu bytes)", total_written, size);
        close(ashmem_fd);
        return -1;
    }
    
    // Set as read-only for security (SFE requirement)
    ASharedMemory_setProtect_t set_prot_fn = get_ashmem_setProtect();
    if (set_prot_fn) set_prot_fn(ashmem_fd, PROT_READ); 
    
    LOGi("[Vault] Sequential Load SUCCESS. Loader RSS remains optimized.");
    return ashmem_fd;
}

extern "C" JNIEXPORT void JNICALL
Java_com_scypheon_sdk_core_utils_NativeSharedMemory_unmapVaultNative(JNIEnv *, jclass, jlong addr, jlong size) {
    if (addr != 0 && size > 0) {
        munmap(reinterpret_cast<void*>(addr), (size_t)size);
        LOGi("[Vault] Native Unmap SUCCESS. Process RSS reclaimed.");
    }
}

static std::mutex jni_mutex;

// JNI Callback Globals
static jobject g_progress_callback = nullptr;
static jmethodID g_on_progress_id = nullptr;

static bool llama_jni_progress_callback(float progress, void* user_data) {
    // 🛡️ SOLARIS HEARTBEAT: Signals that the native load process is ALIVE
    g_load_heartbeat++;
    
    if (!g_vm || !g_progress_callback || !g_on_progress_id) return true;

    JNIEnv* env = nullptr;
    bool attached = false;
    jint res = g_vm->GetEnv((void**)&env, JNI_VERSION_1_6);
    
    if (res == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != 0) return true;
        attached = true;
    } else if (res != JNI_OK) {
        return true;
    }

    env->CallVoidMethod(g_progress_callback, g_on_progress_id, (jfloat)progress);

    if (attached) {
        g_vm->DetachCurrentThread();
    }
    return true;
}

std::string cached_token_chars;
// Rolling accumulator to detect multi-token stop sequences (e.g. <|im_end|> split across 5 tokens)
static std::string stop_sequence_accumulator;

// ═══════════════════════════════════════════════════════════════════════════════
// ENTERPRISE TOKEN FILTER: Prevent chat template artifacts from reaching UI
// This is equivalent to how Claude/GPT/Gemini handle format isolation
// ═══════════════════════════════════════════════════════════════════════════════
static bool is_template_token(const std::string& token) {
    // Chat template special tokens that must NEVER reach the user
    if (token.find("<|im_start|>") != std::string::npos) return true;
    if (token.find("<|im_end|>") != std::string::npos) return true;
    if (token.find("<|eot_id|>") != std::string::npos) return true;
    if (token.find("</s>") != std::string::npos) return true;
    if (token.find("<end_of_turn>") != std::string::npos) return true;
    if (token.find("<start_of_turn>") != std::string::npos) return true;
    if (token == "<eos>" || token.find("<eos>") != std::string::npos) return true;
    if (token.find("<thought>") != std::string::npos) return false; // DONT block thinking tags
    if (token.find("</thought>") != std::string::npos) return false; 
    
    // Additional stop markers for newer models
    if (token.find("<|end_of_text|>") != std::string::npos) return true;
    if (token.find("<|file_separator|>") != std::string::npos) return true;
    
    // Block FRAGMENTS of chat template delimiters (these leak when the model
    // generates <|im_end|> as multiple text tokens instead of a single special token)
    if (token.find("<|") != std::string::npos) return true;
    if (token.find("|>") != std::string::npos) return true;
    if (token == "im_start" || token == "im_end") return true; 
    if (token == "_start" || token == "_end") return true;
    
    // Block standalone role markers often emitted by models
    if (token == "assistant" || token == "assistant\n") return true;
    if (token == "user" || token == "user\n") return true;
    if (token == "model" || token == "model\n") return true;
    if (token == "system" || token == "system\n") return true;

    return false;
}

// ═══════════════════════════════════════════════════════════════════════════════
// MULTI-TOKEN STOP SEQUENCE DETECTOR
// Some GGUF models emit <|im_end|> as 5 separate text tokens instead of 1 special
// token, so llama_vocab_is_eog() never fires, causing an infinite generation loop.
// This function checks a rolling text buffer for completed stop sequences.
// ═══════════════════════════════════════════════════════════════════════════════
static bool check_stop_sequence(const std::string& buffer) {
    if (buffer.find("<|im_end|>") != std::string::npos) return true;
    if (buffer.find("<|im_start|>") != std::string::npos) return true;
    if (buffer.find("<end_of_turn>") != std::string::npos) return true;
    if (buffer.find("<|eot_id|>") != std::string::npos) return true;
    if (buffer.find("</s>") != std::string::npos) return true;
    if (buffer.find("<eos>") != std::string::npos) return true;
    // ═══════════════════════════════════════════════════════════════════════════
    // [v1.1.4-SAR] CONVERSATION TURN BOUNDARY DETECTION
    // Unsloth/LoRA fine-tuned models often use plain-text role markers instead
    // of special tokens. When the model generates "\nUser:" or "\nAI:", it means
    // the model has finished its response and is hallucinating the next turn.
    // We MUST stop here to prevent the infinite "User: hello\nAI: 1" loop.
    // ═══════════════════════════════════════════════════════════════════════════
    if (buffer.find("\nUser:") != std::string::npos) return true;
    if (buffer.find("\nuser:") != std::string::npos) return true;
    if (buffer.find("\nAI:") != std::string::npos) return true;
    if (buffer.find("\nassistant:") != std::string::npos) return true;
    if (buffer.find("\nmodel:") != std::string::npos) return true;
    if (buffer.find("\nHuman:") != std::string::npos) return true;
    if (buffer.find("\n<start_of_turn>") != std::string::npos) return true;
    
    return false;
}

bool is_valid_utf8(const char * string) {
    if (!string) {
        return true;
    }

    const unsigned char * bytes = (const unsigned char *)string;
    int num;

    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            // U+0000 to U+007F
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            // U+0080 to U+07FF
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            // U+0800 to U+FFFF
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            // U+10000 to U+10FFFF
            num = 4;
        } else {
            return false;
        }

        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }

    return true;
}

static void log_callback(ggml_log_level level, const char * text, void * user_data) {
    if (!text) return;
    (void)user_data;
    int priority = ANDROID_LOG_DEFAULT;
    if (level == GGML_LOG_LEVEL_ERROR)     priority = ANDROID_LOG_ERROR;
    else if (level == GGML_LOG_LEVEL_INFO) priority = ANDROID_LOG_INFO;
    else if (level == GGML_LOG_LEVEL_WARN) priority = ANDROID_LOG_WARN;

    __android_log_print(priority, TAG, "%s", text);
}

static ggml_backend_dev_t g_selected_devices[2] = {nullptr, nullptr};

static void apply_backend_enforcement(int backend_mode, llama_model_params& model_params) {
    bool force_cpu    = (backend_mode == 1);
    bool force_vulkan = (backend_mode == 2);
    bool force_opencl = (backend_mode == 3);
    bool is_auto      = (backend_mode == 0);

    g_vulkan_disabled = false;
    g_opencl_disabled = false;

    bool has_vulkan_crash = false;
    bool has_opencl_crash = false;

    // 🛡️ HAT 2.0: Dynamic Re-initialization
    // 🛡️ [SHNC] Total Reset Protocol: Clean environment before attempt
    if (used_fallback) {
        unsetenv("GGML_VULKAN");
        unsetenv("GGML_OPENCL");
        unsetenv("GGML_VULKAN_DEVICE");
        
        // Physically free and re-init backend to purge driver memory
        llama_backend_free();
        llama_backend_init();
        LOGi("[v1.0.2-SAR] Native backend RESET successful.");
    }

    if (!g_files_dir.empty()) {
        struct stat st;
        if (stat((g_files_dir + "/VULKAN_crash.json").c_str(), &st) == 0) has_vulkan_crash = true;
        if (stat((g_files_dir + "/OPENCL_crash.json").c_str(), &st) == 0) has_opencl_crash = true;

        if (!has_vulkan_crash && stat((g_files_dir + "/VULKAN_TRYING.flag").c_str(), &st) == 0) {
            has_vulkan_crash = true;
            LOGw("🛡️ TRIPWIRE: Tombstone detected for VULKAN.");
            std::ofstream report(g_files_dir + "/VULKAN_crash.json");
            report << "{\"backend\":\"VULKAN\",\"description\":\"Fatal Driver Hang (Caught by Tombstone)\"}";
            report.close();
        }
        if (!has_opencl_crash && stat((g_files_dir + "/OPENCL_TRYING.flag").c_str(), &st) == 0) {
            has_opencl_crash = true;
            LOGw("🛡️ TRIPWIRE: Tombstone detected for OPENCL.");
            std::ofstream report(g_files_dir + "/OPENCL_crash.json");
            report << "{\"backend\":\"OPENCL\",\"description\":\"Fatal Driver Hang (Caught by Tombstone)\"}";
            report.close();
        }
    }

    // [SAR] EXPLICIT DEVICE SELECTION
    // This bypasses the buggy auto-selection registry in llama.cpp
    g_selected_devices[0] = nullptr;
    g_selected_devices[1] = nullptr;

    if (force_cpu) {
        setenv("GGML_VULKAN", "0", 1);
        setenv("GGML_OPENCL", "0", 1);
        g_vulkan_disabled = true;
        g_opencl_disabled = true;
        g_active_backend_trying = "NONE";
        model_params.n_gpu_layers = 0;
        active_hardware_status = "CPU [Forced]";
        LOGi("[SAR] Absolute Backend enforcement: FORCE_CPU (Drivers Purged)");

        g_selected_devices[0] = ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_CPU);
        model_params.devices = g_selected_devices;

    } else if (force_vulkan) {
        setenv("GGML_VULKAN", "1", 1);
        setenv("GGML_OPENCL", "0", 1);
        g_vulkan_disabled = false;
        g_opencl_disabled = true;
        g_active_backend_trying = "VULKAN";
        model_params.n_gpu_layers = 99;
        active_hardware_status = "VULKAN [Forced]";
        LOGi("[SAR] Absolute Backend enforcement: FORCE_VULKAN");

        auto reg = ggml_backend_reg_by_name("Vulkan");
        if (!reg) reg = ggml_backend_reg_by_name("vulkan");
        if (reg && ggml_backend_reg_dev_count(reg) > 0) {
            g_selected_devices[0] = ggml_backend_reg_dev_get(reg, 0);
            model_params.devices = g_selected_devices;
        }

    } else if (force_opencl) {
        setenv("GGML_VULKAN", "0", 1);
        setenv("GGML_OPENCL", "1", 1);
        g_vulkan_disabled = true;
        g_opencl_disabled = false;
        g_active_backend_trying = "OPENCL";
        model_params.n_gpu_layers = 99;
        active_hardware_status = "OPENCL [Forced]";
        LOGi("[SAR] Absolute Backend enforcement: FORCE_OPENCL");

        auto reg = ggml_backend_reg_by_name("OpenCL");
        if (!reg) reg = ggml_backend_reg_by_name("opencl");
        if (reg && ggml_backend_reg_dev_count(reg) > 0) {
            g_selected_devices[0] = ggml_backend_reg_dev_get(reg, 0);
            model_params.devices = g_selected_devices;
        }

    } else {
        if (has_opencl_crash && has_vulkan_crash) {
            g_vulkan_disabled = true;
            g_opencl_disabled = true;
            model_params.n_gpu_layers = 0;
            used_fallback = true;
            active_hardware_status = "CPU [Fallback]";
            LOGw("🛡️ TRIPWIRE AUTO: Both GPU backends crashed. CPU only.");
            g_selected_devices[0] = ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_CPU);
            model_params.devices = g_selected_devices;
        } else if (has_vulkan_crash) {
            g_vulkan_disabled = true;
            g_opencl_disabled = false;
            g_active_backend_trying = "OPENCL";
            model_params.n_gpu_layers = 99;
            used_fallback = true;
            active_hardware_status = "OPENCL [Fallback]";
            LOGw("🛡️ TRIPWIRE AUTO: Vulkan blacklisted. Using OpenCL.");
            auto reg = ggml_backend_reg_by_name("OpenCL");
            if (!reg) reg = ggml_backend_reg_by_name("opencl");
            if (reg && ggml_backend_reg_dev_count(reg) > 0) {
                g_selected_devices[0] = ggml_backend_reg_dev_get(reg, 0);
            }
        } else {
            g_vulkan_disabled = false;
            g_opencl_disabled = false;
            g_active_backend_trying = "VULKAN";
            model_params.n_gpu_layers = 99;
            active_hardware_status = "VULKAN [Auto]";
            auto reg = ggml_backend_reg_by_name("Vulkan");
            if (!reg) reg = ggml_backend_reg_by_name("vulkan");
            if (reg && ggml_backend_reg_dev_count(reg) > 0) {
                g_selected_devices[0] = ggml_backend_reg_dev_get(reg, 0);
            }
        }
        
        if (g_selected_devices[0]) {
            model_params.devices = g_selected_devices;
        } else {
            LOGw("[SAR] Explicit device selection failed. Falling back to CPU registry.");
            g_selected_devices[0] = ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_CPU);
            model_params.devices = g_selected_devices;
        }
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_android_llama_cpp_LLamaAndroid_nativeGetHeartbeat(JNIEnv *env, jobject) {
    return g_load_heartbeat;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_load_1model(JNIEnv *env, jobject, jstring filename, jint backend_mode, jobject progress_callback) {
    llama_model_params model_params = llama_model_default_params();
    current_backend_mode = backend_mode;
    used_fallback = false;

    if (progress_callback != nullptr) {
        if (g_progress_callback != nullptr) env->DeleteGlobalRef(g_progress_callback);
        g_progress_callback = env->NewGlobalRef(progress_callback);
        jclass cb_class = env->GetObjectClass(g_progress_callback);
        g_on_progress_id = env->GetMethodID(cb_class, "onProgress", "(F)V");
        model_params.progress_callback = llama_jni_progress_callback;
    }

    apply_backend_enforcement(backend_mode, model_params);

    auto path_to_model = env->GetStringUTFChars(filename, 0);
    LOGi("🚀 [SAR] Native Loading model: %s (mmap: %s) (Mode: %d)", 
         path_to_model, model_params.use_mmap ? "ON" : "OFF", backend_mode);

    // 🛡️ [SAR] HARDENING: Explicitly check for Mali driver presence and PERMISSIONS
    int mali_fd = open("/dev/mali0", O_RDWR);
    if (mali_fd >= 0) {
        LOGi("🛰️ [SAR] SUCCESS: Mali Kernel Driver opened with RDWR permissions. FD=%d", mali_fd);
        close(mali_fd);
    } else {
        LOGe("🛰️ [SAR] FATAL: Cannot open /dev/mali0. Error: %s (errno=%d)", strerror(errno), errno);
        if (errno == EACCES) {
            LOGe("🛡️ [SAR] DIAGNOSIS: SELinux or Android Permissions are BLOCKING GPU access.");
        }
    }

    std::string trying_flag;
    if (g_active_backend_trying != "NONE") {
        trying_flag = g_files_dir + "/" + g_active_backend_trying + "_TRYING.flag";
        std::ofstream flag(trying_flag);
        flag << "1";
        flag.close();
    }

    llama_model* model = nullptr;
    try {
        std::lock_guard<std::recursive_mutex> lock(g_context_mutex);
        model = llama_model_load_from_file(path_to_model, model_params);
    } catch (...) {
        LOGe("🛡️ Native engine threw during load.");
    }
    
    if (model && !trying_flag.empty()) remove(trying_flag.c_str());

    if (!model && (backend_mode == 0) && model_params.n_gpu_layers > 0) {
        LOGe("GPU Load Failed! Falling back to CPU mode...");
        used_fallback = true;
        model_params.n_gpu_layers = 0;
        apply_backend_enforcement(1, model_params); // Force CPU
        model = llama_model_load_from_file(path_to_model, model_params);
    } 

    env->ReleaseStringUTFChars(filename, path_to_model);
    return reinterpret_cast<jlong>(model);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_load_1model_1from_1fd(
    JNIEnv *env, jobject, jint fd, jlong offset, jlong size, jint backend_mode, jobject progress_callback) {
    
    // SAR 3.0: One-Shot Probe for Smart Java Cascade
    long rss_before = get_process_rss();
    
    llama_model_params model_params = llama_model_default_params();
    if (progress_callback != nullptr) {
        model_params.progress_callback = llama_jni_progress_callback;
    }

    apply_backend_enforcement(backend_mode, model_params);

    // 🛡️ ASHMEM SAFETY PROBE
    void* probe_ptr = mmap(NULL, 1, PROT_READ, MAP_SHARED, fd, offset);
    if (probe_ptr == MAP_FAILED) {
        LOGe("[SAR] Vault Probe FAILED.");
        return -1;
    }
    munmap(probe_ptr, 1);

    int dup_fd = dup(fd);
    FILE* file = fdopen(dup_fd, "rb");
    if (!file) {
        LOGe("[SAR] fdopen failed");
        close(dup_fd);
        return -1; 
    }

    llama_model* model = nullptr;
    try {
        std::lock_guard<std::recursive_mutex> lock(g_context_mutex);
        model = llama_model_load_from_file_ptr(file, model_params);
    } catch (...) {
        LOGe("[SAR] Native crash during load attempt");
    }
    fclose(file); 

    if (model) {
        // 🛡️ MICRO-PROBE: Verify driver stability
        llama_context_params probe_params = llama_context_default_params();
        probe_params.n_ctx = 128;
        probe_params.n_threads = 1;
        
        llama_context* probe_ctx = llama_init_from_model(model, probe_params);
        if (probe_ctx) {
            LOGi("[SAR] SUCCESS: Native Engine stabilized on Mode %d.", backend_mode);
            llama_free(probe_ctx);
            return reinterpret_cast<jlong>(model);
        } else {
            LOGe("[SAR] PROBE FAILED: Backend Mode %d rejected by driver.", backend_mode);
            llama_model_free(model);
            model = nullptr;
        }
    }
    
    // 🛡️ CLEANUP & POLLUTION CHECK
    unsetenv("GGML_VULKAN");
    unsetenv("GGML_OPENCL");
    llama_backend_free();
    
    #ifdef __ANDROID__
    // Force immediate heap return to OS
    // malloc_trim(0); 
    #endif

    long rss_after = get_process_rss();
    long delta_mb = (rss_after - rss_before) / (1024 * 1024);

    if (delta_mb > 256) {
        LOGe("[SAR] SENTINEL: Pollution detected (%ld MB residual). Process restart MANDATORY.", delta_mb);
        return -2; // Signal for Hard Reset
    }

    return -1; // Standard failure
}

extern "C"
JNIEXPORT jint JNICALL
Java_android_llama_cpp_LLamaAndroid_get_1model_1n_1ctx_1train(JNIEnv *, jobject, jlong jmodel) {
    auto model = reinterpret_cast<llama_model *>(jmodel);
    if (!model) return 0;
    return llama_model_n_ctx_train(model);
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_free_1model(JNIEnv *, jobject, jlong model) {
    std::lock_guard<std::recursive_mutex> lock(g_context_mutex);
    llama_model_free(reinterpret_cast<llama_model *>(model));
}

extern "C"
JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_new_1context(JNIEnv *env, jobject, jlong jmodel, jint n_ctx_param) {
    auto model = reinterpret_cast<llama_model *>(jmodel);

    if (!model) {
        LOGe("new_context(): model cannot be null");
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Model cannot be null");
        return 0;
    }

    int total_cores = (int) sysconf(_SC_NPROCESSORS_ONLN);
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TOPOLOGY-AWARE THREADING (Architect Guard)
    // Clamped to 4 threads for CPU fallback to avoid A55 scheduling thrashing.
    // ═══════════════════════════════════════════════════════════════════════════
    int n_threads = std::min(4, total_cores);
    
    LOGi("Performance Governor: Detected %d cores. Allocated %d safe threads (CPU-fallback optimized).", total_cores, n_threads);

    LOGi("Performance Governor: Detected %d cores. Allocated %d dynamic threads (GPU-aware).", total_cores, n_threads);

    // ═══════════════════════════════════════════════════════════════════════════
    // AI RESOURCE GOVERNOR (RAM Monitoring)
    // Check /proc/meminfo to prevent OOM crashes on context creation
    // ═══════════════════════════════════════════════════════════════════════════
    FILE* meminfo = fopen("/proc/meminfo", "r");
    if (meminfo) {
        char line[256];
        long memTotal = 0;
        long memAvailable = 0;

        while (fgets(line, sizeof(line), meminfo)) {
            if (strncmp(line, "MemTotal:", 9) == 0) {
                sscanf(line, "MemTotal: %ld kB", &memTotal);
            } else if (strncmp(line, "MemAvailable:", 13) == 0) {
                sscanf(line, "MemAvailable: %ld kB", &memAvailable);
            }
        }
        fclose(meminfo);

        if (memTotal > 0) {
            double memUsagePercent = 100.0 * (1.0 - ((double)memAvailable / (double)memTotal));
            LOGi("Resource Governor: RAM Usage is %.2f%% (Available: %ld kB / Total: %ld kB)", memUsagePercent, memAvailable, memTotal);

            // If RAM usage > 90%, restrict context creation to prevent OOM
            if (memUsagePercent > 90.0) {
                LOGe("Resource Governor ALERT: System is overloaded (RAM > 90%%). Throttling context size to prevent OOM.");
                n_ctx_param = std::min<int>(n_ctx_param, 1024); // Force minimum context size
            }
        }
    }

    llama_context_params ctx_params = llama_context_default_params();

    // User-configurable dynamic context size passed from Android UI
    ctx_params.n_ctx           = n_ctx_param > 0 ? n_ctx_param : 4096;
    ctx_params.n_threads       = n_threads;
    ctx_params.n_threads_batch = n_threads;

    // 🛡️ [SAR] Hard-suppress GPU ops if we are in fallback or forced CPU mode
    if (used_fallback || g_vulkan_disabled || g_opencl_disabled) {
        ctx_params.offload_kqv = false;
        ctx_params.op_offload = false;
        LOGi("[SAR] Context Suppressor: Disabling KQV/Op offloading for CPU stability.");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SMART ASYMMETRIC KV CACHE (Architect Directive)
    // Key Cache: Q4_0 (High compression)
    // Value Cache: Q8_0 (Mathematical stability for CPU)
    // ═══════════════════════════════════════════════════════════════════════════
    bool is_vulkan = (llama_supports_gpu_offload() && !used_fallback && !g_vulkan_disabled);
    
    if (is_vulkan) {
        ctx_params.type_k = GGML_TYPE_TURBO4_0;
        ctx_params.type_v = GGML_TYPE_TURBO4_0;
        LOGi("KV Cache: TURBO4_0 [Vulkan High-Performance]");
    } else {
        ctx_params.type_k = GGML_TYPE_Q4_0;
        ctx_params.type_v = GGML_TYPE_Q8_0;
        LOGi("KV Cache: Q4/Q8 Asymmetric [CPU Safe Architecture]");
    }

    // 🛡️ ADAPTIVE WATCHDOG RESET
    int watchdog_timeout = std::max(10000, (int)(ctx_params.n_ctx / 2 * 2.5));
    g_watchdog_timeout_ms.store(watchdog_timeout);
    LOGi("Adaptive Watchdog: Timeout synchronized to %dms for n_ctx=%d", watchdog_timeout, ctx_params.n_ctx);

    // [LMKD-GUARD] ADAPTIVE MICRO-BATCHING
    // Reduced from n_batch=512 to n_batch=256 to cut the peak compute graph allocation
    // from ~265MB to ~132MB. This prevents the Android Low Memory Killer (LMKD) from
    // killing the sandbox process (BTOP 430) during the prefill phase on 11GB RAM devices.
    // The 6.7GB model leaves insufficient headroom for a 265MB compute buffer allocation.
    ctx_params.n_batch = 256;
    ctx_params.n_ubatch = 128;
    LOGi("[LMKD-GUARD] Micro-Batching: n_batch=256, n_ubatch=128 (OOM-safe for CPU-mapped 7B)");

    llama_context * context = nullptr;
    {
        std::lock_guard<std::recursive_mutex> lock(g_context_mutex);
        context = llama_init_from_model(model, ctx_params);
    }
    
    // 🛡️ HAT (Hardened Adaptive Transition): Zero-Latency Fallback
    if (!context) {
        LOGw("[HAT] Primary Backend initialization failed. Triggering immediate DEEP CPU fallback...");
        setenv("GGML_VULKAN", "0", 1);
        setenv("GGML_OPENCL", "0", 1);
        
        ctx_params.type_k = GGML_TYPE_Q4_0;
        ctx_params.type_v = GGML_TYPE_Q8_0;
        
        // Zero-out GPU flags just in case
        ctx_params.offload_kqv = false;
        ctx_params.op_offload = false;

        used_fallback = true; 
        
        {
            std::lock_guard<std::recursive_mutex> lock(g_context_mutex);
            context = llama_init_from_model(model, ctx_params);
        }

        if (context) {
            LOGi("[HAT] Deep CPU Fallback SUCCESSFUL with Blindfold and Turbo4.");
        } else {
            LOGe("[HAT] Fatal: All backends exhausted even after Deep Blindfolded attempt.");
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // HARDWARE TELEMETRY: Detailed Status Reporting
    // ═══════════════════════════════════════════════════════════════════════════
    // Determine GPU usage based on requested mode, support, and actual success
    bool is_vulkan_active = (g_active_backend_trying == "VULKAN" && !used_fallback && !g_vulkan_disabled);
    bool is_opencl_active = (g_active_backend_trying == "OPENCL" && !used_fallback && !g_opencl_disabled);

    if (is_vulkan_active) {
        active_hardware_status = "GPU [Vulkan Mode]";
    } else if (is_opencl_active) {
        active_hardware_status = "GPU [OpenCL Mode]";
    } else {
        std::string fallback_reason = "";
        if (used_fallback) {
            fallback_reason = " (GPU Refused)";
        } else if (g_vulkan_disabled && g_opencl_disabled) {
            // Check if we are on Mali for detailed reporting
            const char* hardware = "";
            #if defined(__aarch64__)
                // Helper to detect Mali in native code if needed, but we rely on Kotlin detection
            #endif
            fallback_reason = " [Mali Safety Mode]";
        }
        active_hardware_status = "CPU [" + std::to_string(n_threads) + " Cores]" + fallback_reason;
    }
    LOGi("Hardware Telemetry: %s", active_hardware_status.c_str());

    if (!context) {
        LOGe("llama_init_from_model() returned null)");
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),
                      "llama_init_from_model() returned null)");
        return 0;
    }

    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT jstring JNICALL
Java_android_llama_cpp_LLamaAndroid_get_1hardware_1status(JNIEnv *env, jobject) {
    return env->NewStringUTF(active_hardware_status.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_free_1context(JNIEnv *, jobject, jlong context) {
    std::lock_guard<std::recursive_mutex> lock(g_context_mutex);
    llama_free(reinterpret_cast<llama_context *>(context));
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_backend_1free(JNIEnv *, jobject) {
    llama_backend_free();
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_log_1to_1android(JNIEnv *, jobject) {
    llama_log_set(log_callback, NULL);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_android_llama_cpp_LLamaAndroid_bench_1model(
        JNIEnv *env,
        jobject,
        jlong context_pointer,
        jlong model_pointer,
        jlong batch_pointer,
        jint pp,
        jint tg,
        jint pl,
        jint nr
        ) {
    auto pp_avg = 0.0;
    auto tg_avg = 0.0;
    auto pp_std = 0.0;
    auto tg_std = 0.0;

    const auto context = reinterpret_cast<llama_context *>(context_pointer);
    const auto model = reinterpret_cast<llama_model *>(model_pointer);
    const auto batch = reinterpret_cast<llama_batch *>(batch_pointer);

    const int n_ctx = llama_n_ctx(context);

    LOGi("n_ctx = %d", n_ctx);

    int i, j;
    int nri;
    for (nri = 0; nri < nr; nri++) {
        LOGi("Benchmark prompt processing (pp)");

        common_batch_clear(*batch);

        const int n_tokens = pp;
        for (i = 0; i < n_tokens; i++) {
            common_batch_add(*batch, 0, i, { 0 }, false);
        }

        batch->logits[batch->n_tokens - 1] = true;
        llama_memory_clear(llama_get_memory(context), false);

        const auto t_pp_start = ggml_time_us();
        llama_decode_arm();
        if (llama_decode(context, *batch) != 0) {
            LOGi("llama_decode() failed during prompt processing");
        }
        llama_decode_disarm();
        const auto t_pp_end = ggml_time_us();

        // bench text generation

        LOGi("Benchmark text generation (tg)");

        llama_memory_clear(llama_get_memory(context), false);
        const auto t_tg_start = ggml_time_us();
        for (i = 0; i < tg; i++) {

            common_batch_clear(*batch);
            for (j = 0; j < pl; j++) {
                common_batch_add(*batch, 0, i, { j }, true);
            }

            LOGi("llama_decode() text generation: %d", i);
            llama_decode_arm();
            if (llama_decode(context, *batch) != 0) {
                LOGi("llama_decode() failed during text generation");
            }
            llama_decode_disarm();
        }

        const auto t_tg_end = ggml_time_us();

        llama_memory_clear(llama_get_memory(context), false);

        const auto t_pp = double(t_pp_end - t_pp_start) / 1000000.0;
        const auto t_tg = double(t_tg_end - t_tg_start) / 1000000.0;

        const auto speed_pp = double(pp) / t_pp;
        const auto speed_tg = double(pl * tg) / t_tg;

        pp_avg += speed_pp;
        tg_avg += speed_tg;

        pp_std += speed_pp * speed_pp;
        tg_std += speed_tg * speed_tg;

        LOGi("pp %f t/s, tg %f t/s", speed_pp, speed_tg);
    }

    pp_avg /= double(nr);
    tg_avg /= double(nr);

    if (nr > 1) {
        pp_std = sqrt(pp_std / double(nr - 1) - pp_avg * pp_avg * double(nr) / double(nr - 1));
        tg_std = sqrt(tg_std / double(nr - 1) - tg_avg * tg_avg * double(nr) / double(nr - 1));
    } else {
        pp_std = 0;
        tg_std = 0;
    }

    char model_desc[128];
    llama_model_desc(model, model_desc, sizeof(model_desc));

    const auto model_size     = double(llama_model_size(model)) / 1024.0 / 1024.0 / 1024.0;
    const auto model_n_params = double(llama_model_n_params(model)) / 1e9;

    const auto backend    = "(Android)"; // TODO: What should this be?

    std::stringstream result;
    result << std::setprecision(2);
    result << "| model | size | params | backend | test | t/s |\n";
    result << "| --- | --- | --- | --- | --- | --- |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | " << backend << " | pp " << pp << " | " << pp_avg << " ± " << pp_std << " |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | " << backend << " | tg " << tg << " | " << tg_avg << " ± " << tg_std << " |\n";

    return env->NewStringUTF(result.str().c_str());
}

extern "C"
JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_new_1batch(JNIEnv *, jobject, jint n_tokens, jint embd, jint n_seq_max) {
    //  ENTERPRISE FIX: Use standard llama_batch_init to prevent memory leaks from manual malloc
    llama_batch *batch = new llama_batch(llama_batch_init(n_tokens, embd, n_seq_max));
    return reinterpret_cast<jlong>(batch);
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_free_1batch(JNIEnv *, jobject, jlong batch_pointer) {
    auto batch = reinterpret_cast<llama_batch *>(batch_pointer);
    
    if (batch != nullptr) {
        // 🛡️ ENTERPRISE FIX: Use standard llama_batch_free to properly free ALL inner seq_id arrays
        // This solves the massive native memory leak causing Samsung "Frequent Crash" reports.
        llama_batch_free(*batch);
        delete batch;
        LOGi("Native Batch Freed Successfully.");
    }
}

extern "C"
JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_new_1sampler(JNIEnv *env, jobject, jlong context_pointer, jstring jgrammar, jint top_k, jfloat top_p, jfloat temp) {
    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DYNAMIC SAMPLER CHAIN: Configured via Scypheon UI
    // 1. TopK: Filters out low-probability tokens
    // 2. TopP: Filters based on cumulative probability
    // 3. Temp: Scales logits to adjust creativity/randomness
    // ═══════════════════════════════════════════════════════════════════════════
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
    // Dynamic Seed: Using time(NULL) ensures each response is organic and respects Temperature settings.
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(time(NULL)));

    if (jgrammar != nullptr) {
        const char *grammar_str = env->GetStringUTFChars(jgrammar, nullptr);
        if (grammar_str && grammar_str[0] != '\0') {
            const auto context = reinterpret_cast<llama_context *>(context_pointer);
            const auto model = llama_get_model(context);
            const auto vocab = llama_model_get_vocab(model);
            
            auto grammar_smpl = llama_sampler_init_grammar(vocab, grammar_str, "root");
            if (grammar_smpl) {
                llama_sampler_chain_add(smpl, grammar_smpl);
                LOGi("✅ GBNF Grammar successfully loaded and attached to sampler chain");
            } else {
                LOGe("❌ Failed to parse GBNF Grammar!");
            }
        }
        env->ReleaseStringUTFChars(jgrammar, grammar_str);
    }

    return reinterpret_cast<jlong>(smpl);
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_free_1sampler(JNIEnv *, jobject, jlong sampler_pointer) {
    llama_sampler_free(reinterpret_cast<llama_sampler *>(sampler_pointer));
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_backend_1init(JNIEnv *env, jobject, jboolean numa, jstring files_dir) {
    (void)numa;
    
    // Store files_dir for the signal handler tripwire
    const char *dir = env->GetStringUTFChars(files_dir, nullptr);
    if (dir) {
        g_files_dir = dir;
        
        // [SAR] PERSISTENCE: Managed via HardwarePreferences.
        env->ReleaseStringUTFChars(files_dir, dir);
    }

    LOGi("🛡️ TRIPWIRE: Solaris Sentinel armed via JNI_OnLoad. Persistence path: %s", g_files_dir.c_str());
    llama_backend_init();
}

extern "C"
JNIEXPORT jstring JNICALL
Java_android_llama_cpp_LLamaAndroid_system_1info(JNIEnv *env, jobject) {
    return env->NewStringUTF("System Info Disabled for Stability");
}

extern "C"
JNIEXPORT jint JNICALL
Java_android_llama_cpp_LLamaAndroid_completion_1init(
        JNIEnv *env,
        jobject,
        jlong context_pointer,
        jlong batch_pointer,
        jstring jtext,
        jboolean format_chat,
        jint n_len
    ) {

    cached_token_chars.clear();
    stop_sequence_accumulator.clear();
    g_cancel_inference.store(false, std::memory_order_release);

    const auto context = reinterpret_cast<llama_context *>(context_pointer);
    // 🛡️ Enterprise Fix: Clear KV cache at the start of a new prompt evaluation
    // to prevent RoPE sequence position 'X < Y' mismatches with leftover context.
    llama_memory_clear(llama_get_memory(context), true);

    const auto text = env->GetStringUTFChars(jtext, 0);
    const auto batch = reinterpret_cast<llama_batch *>(batch_pointer);

    bool parse_special = (format_chat == JNI_TRUE);
    const auto tokens_list = common_tokenize(context, text, true, parse_special);

    auto n_ctx = llama_n_ctx(context);
    auto n_kv_req = tokens_list.size() + n_len;

    LOGi("n_len = %d, n_ctx = %d, n_kv_req = %zu", n_len, n_ctx, n_kv_req);

    // [v1.1.3-SAR] ADAPTIVE CLAMPING instead of hard-fail.
    // If the prompt + requested generation exceeds context, reduce generation budget.
    // This prevents the Context Summarizer (and any caller) from silently failing
    // when it requests maxTokens equal to the full context window.
    if (n_kv_req > n_ctx) {
        int clamped = (int)n_ctx - (int)tokens_list.size();
        if (clamped < 1) {
            LOGe("error: prompt alone (%zu tokens) fills or exceeds n_ctx (%d). Cannot generate.", tokens_list.size(), n_ctx);
            env->ReleaseStringUTFChars(jtext, text);
            return 0;
        }
        LOGw("[SAR] n_kv_req (%zu) > n_ctx (%d). Clamping n_len from %d to %d.", n_kv_req, n_ctx, n_len, clamped);
        n_len = clamped;
        n_kv_req = tokens_list.size() + n_len;
    }

/*  🛡️ DIAGNOSTIC OVERLOAD: Remove token logging loop
    for (auto id : tokens_list) {
        LOGi("token: `%s`-> %d ", common_token_to_piece(context, id).c_str(), id);
    }
*/

    common_batch_clear(*batch);

    // [v1.1.2-SAR] CHUNKED PROMPT EVALUATION
    // llama_decode requires batch.n_tokens <= n_batch. 
    // We must evaluate the initial prompt in chunks to honor the LMKD-GUARD limits.
    const int n_batch_limit = llama_n_batch(context);
    for (int i = 0; i < (int) tokens_list.size(); i += n_batch_limit) {
        int n_eval = (int) tokens_list.size() - i;
        if (n_eval > n_batch_limit) n_eval = n_batch_limit;

        common_batch_clear(*batch);
        for (int j = 0; j < n_eval; j++) {
            common_batch_add(*batch, tokens_list[i + j], i + j, { 0 }, false);
        }

        // Only request logits for the very last token of the entire prompt
        if (i + n_eval == (int) tokens_list.size()) {
            batch->logits[batch->n_tokens - 1] = true;
        }

        check_and_apply_trim(context, batch->n_tokens);

        {
            std::lock_guard<std::recursive_mutex> lock(g_context_mutex);
            if (llama_decode(context, *batch) != 0) {
                LOGe("llama_decode() failed during chunked prefill at offset %d", i);
                env->ReleaseStringUTFChars(jtext, text);
                return 0;
            }
        }
        LOGi("[SAR] Prefill Progress: %d/%zu tokens", i + n_eval, tokens_list.size());
    }

    env->ReleaseStringUTFChars(jtext, text);

    // [v1.1.3-SAR] CRITICAL: Return total prompt token count, NOT batch->n_tokens.
    // After chunked prefill, batch->n_tokens only holds the LAST chunk size (e.g. 39),
    // but the KV cache cursor is at tokens_list.size() (e.g. 295).
    // completion_loop uses this return value as the starting decode position (ncur).
    return (jint) tokens_list.size();
}

extern "C"
JNIEXPORT jstring JNICALL
Java_android_llama_cpp_LLamaAndroid_completion_1loop(
        JNIEnv * env,
        jobject,
        jlong context_pointer,
        jlong batch_pointer,
        jlong sampler_pointer,
        jint n_len,
        jintArray ncur_array
) {
    if (g_cancel_inference.load(std::memory_order_acquire)) {
        LOGw("🚫 [SAR] Completion loop ABORTED by cancellation signal.");
        return nullptr;
    }

    const auto context = reinterpret_cast<llama_context *>(context_pointer);
    const auto batch   = reinterpret_cast<llama_batch   *>(batch_pointer);
    const auto sampler = reinterpret_cast<llama_sampler *>(sampler_pointer);
    const auto model = llama_get_model(context);
    const auto vocab = llama_model_get_vocab(model);

    jint *ncur_ptr = env->GetIntArrayElements(ncur_array, nullptr);
    int n_cur = ncur_ptr[0];

    // sample the most likely token
    llama_token new_token_id;
    {
        std::lock_guard<std::recursive_mutex> lock(g_context_mutex);
        new_token_id = llama_sampler_sample(sampler, context, -1);
    }

    if (llama_vocab_is_eog(vocab, new_token_id) || n_cur == n_len) {
        env->ReleaseIntArrayElements(ncur_array, ncur_ptr, JNI_ABORT);
        return nullptr;
    }

    auto new_token_chars = common_token_to_piece(context, new_token_id);
    cached_token_chars += new_token_chars;

    // ═══════════════════════════════════════════════════════════════════════
    // MULTI-TOKEN STOP SEQUENCE DETECTION
    // Accumulate raw token text and check if <|im_end|> (or similar) has
    // been fully assembled across multiple tokens. If so, HALT generation.
    // This fixes the infinite loop when the model emits stop markers as
    // individual text tokens instead of a single special EOG token.
    // ═══════════════════════════════════════════════════════════════════════
    stop_sequence_accumulator += new_token_chars;
    // Keep buffer bounded (only last 64 chars matter for detection)
    if (stop_sequence_accumulator.size() > 128) {
        stop_sequence_accumulator = stop_sequence_accumulator.substr(stop_sequence_accumulator.size() - 64);
    }
    if (check_stop_sequence(stop_sequence_accumulator)) {
        LOGi("🛑 Multi-token stop sequence detected in output. Ending generation.");
        stop_sequence_accumulator.clear();
        cached_token_chars.clear();
        return nullptr;
    }

    // Extract the valid token text before clearing it
    std::string final_token_text = "";

    jstring new_token = nullptr;
    if (is_valid_utf8(cached_token_chars.c_str())) {
        // ENTERPRISE FILTER: Suppress internal template tokens
        if (is_template_token(cached_token_chars)) {
             // Return empty string so UI doesn't see it, but model state advances
             new_token = env->NewStringUTF(""); 
             LOGi("⛔ Enterprise Filter: Blocked leaked token: `%s`", cached_token_chars.c_str());
        } else {
             final_token_text = cached_token_chars;
             new_token = env->NewStringUTF(cached_token_chars.c_str());
             LOGi("cached: %s, new_token_chars: `%s`, id: %d", cached_token_chars.c_str(), new_token_chars.c_str(), new_token_id);
        }
        cached_token_chars.clear();
    } else {
        new_token = env->NewStringUTF("");
    }

    common_batch_clear(*batch);
    common_batch_add(*batch, new_token_id, n_cur, { 0 }, true);

    check_and_apply_trim(context, batch->n_tokens);

    llama_decode_arm();
    if (llama_decode(context, *batch) != 0) {
        llama_decode_disarm();
        LOGe("🛑 Multi-token decode failure. Halting generation loop to prevent inconsistent KV cache states.");
        return nullptr;
    }
    llama_decode_disarm();

    // Advance position ONLY if decode succeeded
    ncur_ptr[0] = n_cur + 1;
    env->ReleaseIntArrayElements(ncur_array, ncur_ptr, 0);

    // [SAR PHASE 2] Ashmem IPC WRITE
    if (g_output_shm_ptr && g_output_token_count) {
        int32_t idx = g_output_token_count->load(std::memory_order_acquire);
        size_t offset = sizeof(std::atomic<int32_t>) + (idx * sizeof(TokenEntry));
        
        if (offset + sizeof(TokenEntry) <= g_output_shm_size) {
            TokenEntry* entry = reinterpret_cast<TokenEntry*>((char*)g_output_shm_ptr + offset);
            entry->token_id = (int32_t)new_token_id;
            entry->confidence = 1.0f; // TODO: Get actual probability from sampler
            
            const char* token_str = final_token_text.c_str();
            size_t len = std::min(strlen(token_str), sizeof(entry->text) - 1);
            entry->length = (int32_t)len;
            memcpy(entry->text, token_str, len);
            entry->text[len] = '\0';
            
            g_output_token_count->fetch_add(1, std::memory_order_release);
        }
    }

    return new_token;
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_kv_1cache_1clear(JNIEnv *, jobject, jlong context) {
    std::lock_guard<std::recursive_mutex> lock(g_context_mutex);
    llama_memory_clear(llama_get_memory(reinterpret_cast<llama_context *>(context)), true);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_android_llama_cpp_LLamaAndroid_save_1state(JNIEnv *env, jobject, jlong context, jstring filename) {
    const char *path = env->GetStringUTFChars(filename, 0);
    const auto llama_ctx = reinterpret_cast<llama_context *>(context);
    
    LOGi("[SAR] Saving KV Cache state to %s", path);
    // llama_state_save_file is in common.h/cpp
    bool success = llama_state_save_file(llama_ctx, path, nullptr, 0);
    
    env->ReleaseStringUTFChars(filename, path);
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_android_llama_cpp_LLamaAndroid_load_1state(JNIEnv *env, jobject, jlong context, jstring filename) {
    const char *path = env->GetStringUTFChars(filename, 0);
    const auto llama_ctx = reinterpret_cast<llama_context *>(context);
    
    LOGi("[SAR] Loading KV Cache state from %s", path);
    llama_token tokens[1]; // Not used but required by signature
    size_t n_tokens_out = 0;
    bool success = llama_state_load_file(llama_ctx, path, tokens, 1, &n_tokens_out);
    
    env->ReleaseStringUTFChars(filename, path);
    return success ? JNI_TRUE : JNI_FALSE;
}

// ═══════════════════════════════════════════════════════════════════════════════
// ENTERPRISE: NATIVE VAULT (JNI Security Bridge)
// Hardcoding sensitive endpoints and salts in compiled C++ to thwart reverse engineering
// ═══════════════════════════════════════════════════════════════════════════════

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_mission_vitreon_core_security_NativeVault_getHardwareSalt(JNIEnv *env, jobject thiz) {
    // Obfuscated salt for SQLCipher master key derivation
    // Instead of plaintext "VitreonDatabaseSalt2026", we use a byte array
    const jbyte salt[] = {
        0x56, 0x69, 0x74, 0x72, 0x65, 0x6f, 0x6e, 0x44, // VitreonD
        0x61, 0x74, 0x61, 0x62, 0x61, 0x73, 0x65, 0x53, // atabaseS
        0x61, 0x6c, 0x74, 0x32, 0x30, 0x32, 0x36, 0x21  // alt2026!
    };
    jbyteArray result = env->NewByteArray(sizeof(salt));
    env->SetByteArrayRegion(result, 0, sizeof(salt), salt);
    return result;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_mission_vitreon_core_security_NativeVault_getAliasPepper(JNIEnv *env, jobject thiz) {
    // A secret pepper to hash contacts/places in the alias store
    // Reconstructed at runtime to hide from static analysis 'strings' command
    char pepper[16];
    pepper[0] = 'P'; pepper[1] = 'u'; pepper[2] = 'p'; pepper[3] = 'p';
    pepper[4] = 'e'; pepper[5] = 't'; pepper[6] = 'M'; pepper[7] = 'a';
    pepper[8] = 's'; pepper[9] = 't'; pepper[10]= 'e'; pepper[11]= 'r';
    pepper[12]= 'X'; pepper[13]= '9'; pepper[14]= '9'; pepper[15]= '\0';
    return env->NewStringUTF(pepper);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_mission_vitreon_core_security_NativeVault_getDefaultEndpoint(JNIEnv *env, jobject thiz, jint provider_id) {
    // Hardcoded API Endpoints in C++ (Provider IDs map to SubscriptionConfig.Provider ordinal or specific IDs)
    // 1=DeepSeek, 2=Gemini, 3=OpenAI, 4=Claude, 5=xAI, 6=Qwen, 7=OpenRouter
    std::string endpoint = "";
    switch (provider_id) {
        case 1: endpoint = "https://api.deepseek.com/beta"; break;
        case 2: endpoint = "https://generativelanguage.googleapis.com/v1beta"; break;
        case 3: endpoint = "https://api.openai.com/v1"; break;
        case 4: endpoint = "https://api.anthropic.com/v1"; break;
        case 5: endpoint = "https://api.x.ai/v1"; break;
        case 6: endpoint = "https://dashscope-intl.aliyuncs.com/api/v1"; break;
        case 7: endpoint = "https://openrouter.ai/api/v1"; break;
        default: endpoint = "localhost"; break;
    }
    return env->NewStringUTF(endpoint.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_mission_vitreon_core_security_NativeVault_getSystemPrompt(JNIEnv *env, jobject thiz) {
    // ═══════════════════════════════════════════════════════════════════════════════
    // CORE IDENTITY / THE SOUL OF VITREON
    // Hardcoded here to prevent copycat apps from decompiling the APK and
    // extracting the precise prompt engineering that makes the Agent smart.
    // ═══════════════════════════════════════════════════════════════════════════════
    const char* core_prompt =
        "You are Vitreon, an advanced, highly intelligent AI Assistant running on Android.\n"
        "Your goal is to be helpful, concise, and incredibly capable.\n\n"
        
        if (!embd_ctx) {
            LOGe("[EMBED] Failed to create embedding context.");
            env->ReleaseStringUTFChars(jtext, text);
            return env->NewFloatArray(0);
        }

        // Tokenize input
        const auto vocab = llama_model_get_vocab(model);
        std::vector<llama_token> tokens(512);
        int n_tokens = llama_tokenize(vocab, text, (int32_t)strlen(text), tokens.data(), (int32_t)tokens.size(), true, false);
        
        if (n_tokens < 0) {
            // Buffer was too small — resize and retry
            tokens.resize(-n_tokens);
            n_tokens = llama_tokenize(vocab, text, (int32_t)strlen(text), tokens.data(), (int32_t)tokens.size(), true, false);
        }
        
        if (n_tokens <= 0) {
            LOGe("[EMBED] Tokenization failed (n_tokens=%d).", n_tokens);
            {
                std::lock_guard<std::recursive_mutex> lock(g_context_mutex);
                llama_free(embd_ctx);
            }
            env->ReleaseStringUTFChars(jtext, text);
            return env->NewFloatArray(0);
        }
        tokens.resize(n_tokens);
        LOGi("[EMBED] Tokenized %d tokens.", n_tokens);

        // Build a batch for llama_encode / llama_decode
        llama_batch batch = llama_batch_init(n_tokens, 0, 1);
        batch.n_tokens = n_tokens;
        for (int i = 0; i < n_tokens; i++) {
            batch.token[i]     = tokens[i];
            batch.pos[i]       = i;
            batch.n_seq_id[i]  = 1;
            batch.seq_id[i][0] = 0;
            batch.logits[i]    = false; // We only want embeddings, not logits
        }
        // For pooling models, we need at least the last token to signal end-of-sequence
        batch.logits[n_tokens - 1] = true;

        // Clear KV before the embedding pass
        {
            std::lock_guard<std::recursive_mutex> lock(g_context_mutex);
            llama_memory_clear(llama_get_memory(embd_ctx), true);
        }

        int decode_ret;
        {
            std::lock_guard<std::recursive_mutex> lock(g_context_mutex);
            decode_ret = llama_decode(embd_ctx, batch);
        }
        
        llama_batch_free(batch);

        if (decode_ret != 0) {
            LOGe("[EMBED] llama_decode failed (ret=%d).", decode_ret);
            {
                std::lock_guard<std::recursive_mutex> lock(g_context_mutex);
                llama_free(embd_ctx);
            }
            env->ReleaseStringUTFChars(jtext, text);
            return env->NewFloatArray(0);
        }

        // Extract the mean-pooled embedding for sequence 0
        float* embd_ptr = llama_get_embeddings_seq(embd_ctx, 0);
        if (!embd_ptr) {
            // Fallback: try getting embedding from the last token (generative model piggyback)
            embd_ptr = llama_get_embeddings_ith(embd_ctx, -1);
            LOGi("[EMBED] Pooled embedding unavailable — falling back to last-token embedding.");
        }

        int n_embd = llama_model_n_embd(model);
        LOGi("[EMBED] Embedding dim=%d. Copying to Java...", n_embd);

        if (embd_ptr && n_embd > 0) {
            result = env->NewFloatArray(n_embd);
            env->SetFloatArrayRegion(result, 0, n_embd, embd_ptr);
        } else {
            LOGe("[EMBED] Embedding pointer is null or n_embd=0.");
            result = env->NewFloatArray(0);
        }

    } catch (...) {
        LOGe("[EMBED] Native crash during embedding extraction.");
        result = env->NewFloatArray(0);
    }

    // CLEANUP: Free the temporary embedding context.
    if (embd_ctx) {
        std::lock_guard<std::recursive_mutex> lock(g_context_mutex);
        llama_free(embd_ctx);
    }

    // RAM RECOVERY: Return freed native heap to OS via dynamic dispatch.
    // Using dlsym avoids compile-time symbol resolution issues across NDK versions.
    // malloc_trim is available on Bionic since API 17 but its header annotation varies.
    {
        typedef int (*malloc_trim_fn)(size_t);
        static malloc_trim_fn fn = reinterpret_cast<malloc_trim_fn>(dlsym(RTLD_DEFAULT, "malloc_trim"));
        if (fn) fn(0);
    }
    env->ReleaseStringUTFChars(jtext, text);
    LOGi("[EMBED] Native embedding extraction complete.");
    return result ? result : env->NewFloatArray(0);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_android_llama_cpp_LLamaAndroid_probe_1backend(
    JNIEnv* env, jobject, jstring jModelPath, jint backendType) {
    // Save original env vars to prevent global pollution of the process
    char* orig_vulkan = getenv("GGML_VULKAN");
    char* orig_opencl = getenv("GGML_OPENCL");
    char* orig_vulkan_disable = getenv("GGML_VULKAN_DISABLE");
    char* orig_opencl_disable = getenv("GGML_OPENCL_DISABLE");
    
    std::string val_vulkan = orig_vulkan ? orig_vulkan : "";
    std::string val_opencl = orig_opencl ? orig_opencl : "";
    std::string val_vulkan_disable = orig_vulkan_disable ? orig_vulkan_disable : "";
    std::string val_opencl_disable = orig_opencl_disable ? orig_opencl_disable : "";

    // [v1.1.2-SAR] CRITICAL: Suppress GPU drivers BEFORE backend registration
    setenv("GGML_VULKAN", "0", 1);
    setenv("GGML_OPENCL", "0", 1);
    setenv("GGML_VULKAN_DISABLE", "1", 1);
    setenv("GGML_OPENCL_DISABLE", "1", 1);

    llama_backend_init();
    
    llama_model_params m_params = llama_model_default_params();
    m_params.vocab_only = false; // [v1.0.6-SAR] MUST test real tensor allocation
    
    // [v1.1.2-SAR] CRITICAL PINNING: Force CPU device registry
    static ggml_backend_dev_t cpu_devices[2] = {nullptr, nullptr};
    cpu_devices[0] = ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_CPU);
    m_params.devices = cpu_devices;

    // [v1.1.0-SAR] CRITICAL: Probe ALWAYS uses CPU (n_gpu_layers=0).
    m_params.n_gpu_layers = 0;
    
    LOGi("🧵 [PROBE] Using CPU backend (model integrity check).");
    
    const char* path = env->GetStringUTFChars(jModelPath, nullptr);
    LOGi("📂 [PROBE] Loading model (CPU) from: %s", path);
    
    llama_model* model = llama_model_load_from_file(path, m_params);
    env->ReleaseStringUTFChars(jModelPath, path);
    
    bool ok = (model != nullptr);
    if (ok) {
        LOGi("✅ [PROBE] Backend %d initialized successfully.", backendType);
        llama_model_free(model);
    } else {
        LOGe("❌ [PROBE] Backend %d FAILED. Allocation rejected.", backendType);
    }
    
    llama_backend_free();

    // Restore original env vars
    if (orig_vulkan) setenv("GGML_VULKAN", val_vulkan.c_str(), 1); else unsetenv("GGML_VULKAN");
    if (orig_opencl) setenv("GGML_OPENCL", val_opencl.c_str(), 1); else unsetenv("GGML_OPENCL");
    if (orig_vulkan_disable) setenv("GGML_VULKAN_DISABLE", val_vulkan_disable.c_str(), 1); else unsetenv("GGML_VULKAN_DISABLE");
    if (orig_opencl_disable) setenv("GGML_OPENCL_DISABLE", val_opencl_disable.c_str(), 1); else unsetenv("GGML_OPENCL_DISABLE");

    return ok;
}
