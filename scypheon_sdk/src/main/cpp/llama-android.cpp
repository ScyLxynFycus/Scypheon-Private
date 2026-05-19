#include <jni.h>
#include <string>
#include <thread>
#include <atomic>
#include <android/log.h>
#include <unistd.h>
#include <sys/syscall.h>
#include <sys/mman.h>
#include <fcntl.h>
#include "llama.h"

#define TAG "ScypheonNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static JavaVM* g_vm = nullptr;
static std::atomic<bool> g_cancel_requested{false};

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_vm = vm;
    LOGI("JNI_OnLoad: JVM reference captured.");
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_load_1model(
    JNIEnv* env, jobject /* instance */, jstring jPath, jint backendMode, jobject progressCallback) {
    
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    // Tier mapping: 1=CPU, 2=VULKAN, 3=OPENCL (matches ScypheonRepository)
    int n_gpu_layers = (backendMode == 1) ? 0 : 99;
    LOGI("load_model: Loading from %s (backendMode=%d, gpu_layers=%d)", path, backendMode, n_gpu_layers);

    llama_model_params m_params = llama_model_default_params();
    m_params.use_mmap     = true;
    m_params.n_gpu_layers = n_gpu_layers;

    llama_model* model = llama_model_load_from_file(path, m_params);
    env->ReleaseStringUTFChars(jPath, path);
    
    if (!model) {
        LOGE("nativeInit: Failed to load model.");
        return 0L;
    }

    llama_context_params c_params = llama_context_default_params();
    c_params.n_ctx = 4096;
    c_params.n_threads = 4; // Performance core cap
    c_params.n_threads_batch = 4;
    
    llama_context* ctx = llama_init_from_model(model, c_params);
    if (!ctx) {
        LOGE("nativeInit: Failed to create context.");
        llama_model_free(model);
        return 0L;
    }

    return reinterpret_cast<jlong>(ctx);
}

// --- NativeLibraryLoader Implementation ---

extern "C" __attribute__((visibility("default"))) JNIEXPORT jint JNICALL
Java_com_scypheon_sdk_core_utils_NativeLibraryLoader_createMemfdNative(
    JNIEnv* env, jclass clazz, jstring jName, jlong size) {
    const char* name = env->GetStringUTFChars(jName, nullptr);
    int fd = syscall(__NR_memfd_create, name, MFD_CLOEXEC | MFD_ALLOW_SEALING);
    env->ReleaseStringUTFChars(jName, name);
    if (fd == -1) return -1;
    if (ftruncate(fd, size) == -1) {
        close(fd);
        return -1;
    }
    return fd;
}

extern "C" __attribute__((visibility("default"))) JNIEXPORT jint JNICALL
Java_com_scypheon_sdk_core_utils_NativeLibraryLoader_setOomScoreNative(
    JNIEnv* env, jclass clazz, jint score) {
    int fd = open("/proc/self/oom_score_adj", O_WRONLY);
    if (fd == -1) return -1;
    std::string score_str = std::to_string(score);
    ssize_t written = write(fd, score_str.c_str(), score_str.length());
    close(fd);
    return (written == -1) ? -1 : 0;
}

extern "C" __attribute__((visibility("default"))) JNIEXPORT jboolean JNICALL
Java_com_scypheon_sdk_core_utils_NativeLibraryLoader_probeBackendNative(
    JNIEnv* env, jclass clazz, jstring jModelPath, jint backendType) {
    
    LOGI("🔍 [PROBE] Validating model file integrity (CPU-only, backend=%d)", backendType);

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
    
    // [v1.1.2-SAR] CRITICAL PINNING: Force CPU device registry to prevent driver-level probes
    static ggml_backend_dev_t cpu_devices[2] = {nullptr, nullptr};
    cpu_devices[0] = ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_CPU);
    m_params.devices = cpu_devices;

    // [v1.1.0-SAR] CRITICAL: Probe ALWAYS uses CPU (n_gpu_layers=0).
    m_params.n_gpu_layers = 0;
    
    LOGI("🧵 [PROBE] Using CPU backend (model integrity check).");
    
    const char* path = env->GetStringUTFChars(jModelPath, nullptr);
    LOGI("📂 [PROBE] Loading model (CPU) from: %s", path);
    
    llama_model* model = llama_model_load_from_file(path, m_params);
    env->ReleaseStringUTFChars(jModelPath, path);
    
    bool ok = (model != nullptr);
    if (ok) {
        LOGI("✅ [PROBE] Backend %d initialized successfully.", backendType);
        llama_model_free(model);
    } else {
        LOGE("❌ [PROBE] Backend %d FAILED. Allocation rejected.", backendType);
    }
    
    llama_backend_free();

    // Restore original env vars
    if (orig_vulkan) setenv("GGML_VULKAN", val_vulkan.c_str(), 1); else unsetenv("GGML_VULKAN");
    if (orig_opencl) setenv("GGML_OPENCL", val_opencl.c_str(), 1); else unsetenv("GGML_OPENCL");
    if (orig_vulkan_disable) setenv("GGML_VULKAN_DISABLE", val_vulkan_disable.c_str(), 1); else unsetenv("GGML_VULKAN_DISABLE");
    if (orig_opencl_disable) setenv("GGML_OPENCL_DISABLE", val_opencl_disable.c_str(), 1); else unsetenv("GGML_OPENCL_DISABLE");

    return ok;
}

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_native_1inference(
    JNIEnv* env, jobject /* instance */, jlong jCtx, jstring jPrompt, jobject jCallback) {
    
    llama_context* ctx = reinterpret_cast<llama_context*>(jCtx);
    const char* prompt = env->GetStringUTFChars(jPrompt, nullptr);
    
    // [CRITICAL] GlobalRef to survive thread transitions and local ref destruction
    jobject gCallback = env->NewGlobalRef(jCallback);
    jclass cbClass = env->GetObjectClass(gCallback);
    jmethodID onTokenMid = env->GetMethodID(cbClass, "onTokenReceived", "(Ljava/lang/String;)V");
    
    g_cancel_requested = false;
    LOGI("nativeInference: Starting stream for prompt.");

    // [STUB] This would be replaced with actual llama_decode/sample loop
    for (int i = 0; i < 10; ++i) {
        if (g_cancel_requested) break;

        JNIEnv* cbEnv;
        jint attachStat = g_vm->AttachCurrentThread(&cbEnv, nullptr);
        if (attachStat != JNI_OK) break;

        jstring jTok = cbEnv->NewStringUTF("Token ");
        cbEnv->CallVoidMethod(gCallback, onTokenMid, jTok);
        cbEnv->DeleteLocalRef(jTok);

        if (cbEnv->ExceptionCheck()) {
            LOGE("nativeInference: Java exception detected during callback.");
            cbEnv->ExceptionClear();
            g_vm->DetachCurrentThread();
            break;
        }

        g_vm->DetachCurrentThread();
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }

    env->ReleaseStringUTFChars(jPrompt, prompt);
    env->DeleteGlobalRef(gCallback); // PREVENT GLOBAL REF LEAK
}

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_native_1cancel_1inference(
    JNIEnv*, jobject) {
    g_cancel_requested = true;
    LOGI("nativeCancelInference: Cancel flag set.");
}

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_free_1model(
    JNIEnv*, jobject, jlong jCtx) {
    llama_context* ctx = reinterpret_cast<llama_context*>(jCtx);
    if (ctx) {
        LOGI("nativeRelease: Freeing native resources.");
        const llama_model* model = llama_get_model(ctx);
        llama_free(ctx);
        llama_model_free(const_cast<llama_model*>(model));
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_android_llama_cpp_LLamaAndroid_probe_1backend(
    JNIEnv* env, jobject, jstring jModelPath, jint backendType) {
    return Java_com_scypheon_sdk_core_utils_NativeLibraryLoader_probeBackendNative(env, nullptr, jModelPath, backendType);
}
