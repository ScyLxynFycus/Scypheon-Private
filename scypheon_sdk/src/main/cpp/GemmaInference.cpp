#include <jni.h>
#include <atomic>
#include <string>
#include <vector>
#include <cstring>
#include <android/log.h>
#include "llama.h"
#include "sha256.h"

#define TAG "ScypheonInference"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#include <sys/stat.h>
#include <fstream>
#include <iostream>

// Global state for JNI synchronization
std::atomic<bool> g_stop_requested{false};
std::atomic<bool> g_initialized{false};
std::string g_files_dir = "";
llama_model* g_model = nullptr;
llama_context* g_ctx = nullptr;

extern "C" JNIEXPORT void JNICALL
Java_com_scypheon_sdk_core_agent_orchestrator_GemmaNativeRuntime_nativeInitialize(JNIEnv* env, jobject obj, jstring filesDir) {
    const char* c_files_dir = env->GetStringUTFChars(filesDir, nullptr);
    g_files_dir = std::string(c_files_dir);
    env->ReleaseStringUTFChars(filesDir, c_files_dir);
    
    // [SCYPHEON-VITREON SYNC] Initialize backend once per process
    static bool backend_initialized = false;
    if (!backend_initialized) {
        llama_backend_init();
        llama_numa_init(GGML_NUMA_STRATEGY_DISTRIBUTE);
        backend_initialized = true;
        LOGI("BACKEND_INIT: Native llama backend initialized.");
    }
}

static void apply_backend_enforcement(llama_model_params& m_params) {
    if (g_files_dir.empty()) return;

    struct stat st;
    bool has_vulkan_crash = (stat((g_files_dir + "/VULKAN_crash.json").c_str(), &st) == 0);
    bool has_opencl_crash = (stat((g_files_dir + "/OPENCL_crash.json").c_str(), &st) == 0);

    // TRIPLE FALLBACK LOGIC
    if (has_vulkan_crash && has_opencl_crash) {
        LOGI("FALLBACK: Both GPU backends blacklisted. Forcing CPU.");
        m_params.n_gpu_layers = 0;
    } else if (has_vulkan_crash) {
        LOGI("FALLBACK: Vulkan blacklisted. Attempting OpenCL.");
        setenv("GGML_VULKAN", "0", 1);
        setenv("GGML_OPENCL", "1", 1);
        m_params.n_gpu_layers = 99;
    } else {
        LOGI("BACKEND: Attempting Vulkan Acceleration.");
        setenv("GGML_VULKAN", "1", 1);
        m_params.n_gpu_layers = 99;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_scypheon_sdk_core_agent_orchestrator_GemmaNativeRuntime_nativeStopInference(JNIEnv* env, jobject obj) {
    g_stop_requested.store(true);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_scypheon_sdk_core_agent_orchestrator_GemmaNativeRuntime_nativeIsInitialized(JNIEnv* env, jobject obj) {
    return g_initialized.load();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_scypheon_sdk_core_agent_orchestrator_GemmaNativeRuntime_nativeLoadModel(JNIEnv* env, jobject obj, jstring path, jstring expectedHash) {
    const char* model_path = env->GetStringUTFChars(path, nullptr);
    const char* c_expected_hash = env->GetStringUTFChars(expectedHash, nullptr);
    
    // 1. Mandatory SHA-256 Integrity Check
    std::string actual_hash = SHA256::hashFile(model_path);
    if (actual_hash != std::string(c_expected_hash)) {
        LOGE("INTEGRITY_FAILURE: Expected %s, Got %s", c_expected_hash, actual_hash.c_str());
        env->ReleaseStringUTFChars(path, model_path);
        env->ReleaseStringUTFChars(expectedHash, c_expected_hash);
        return -1; // ERROR_INTEGRITY_FAILED
    }
    
    // 2. Setup Parameters with Fallback
    llama_model_params m_params = llama_model_default_params();
    m_params.use_mmap = true;
    apply_backend_enforcement(m_params);

    // 3. Mark "Trying" for crash detection
    std::string trying_flag = g_files_dir + "/GPU_TRYING.flag";
    if (!g_files_dir.empty()) {
        std::ofstream flag(trying_flag);
        flag << "1";
        flag.close();
    }
    
    g_model = llama_model_load_from_file(model_path, m_params);
    
    // Remove "Trying" flag on success
    if (g_model && !g_files_dir.empty()) {
        remove(trying_flag.c_str());
    }

    if (!g_model) {
        LOGE("LLAMA_LOAD_FAILED: Failed to load model weights.");
        env->ReleaseStringUTFChars(path, model_path);
        env->ReleaseStringUTFChars(expectedHash, c_expected_hash);
        return -2;
    }

    llama_context_params c_params = llama_context_default_params();
    c_params.n_ctx = 4096;
    c_params.n_threads = 4;
    c_params.n_threads_batch = 4;
    
    g_ctx = llama_init_from_model(g_model, c_params);
    
    env->ReleaseStringUTFChars(path, model_path);
    env->ReleaseStringUTFChars(expectedHash, c_expected_hash);
    
    if (!g_ctx) {
        LOGE("CTX_INIT_FAILED: Failed to create inference context.");
        llama_model_free(g_model);
        g_model = nullptr;
        return -3;
    }
    
    g_initialized.store(true);
    LOGI("NATIVE_LOAD_SUCCESS: Scypheon Engine Ready.");
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_scypheon_sdk_core_agent_orchestrator_GemmaNativeRuntime_nativeGenerate(JNIEnv* env, jobject obj, jstring prompt, jobject callback) {
    if (!g_initialized.load()) return;
    g_stop_requested.store(false);
    
    const char* c_prompt = env->GetStringUTFChars(prompt, nullptr);
    
    jclass cb_class = env->GetObjectClass(callback);
    jmethodID mid = env->GetMethodID(cb_class, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");

    const struct llama_vocab * vocab = llama_model_get_vocab(g_model);
    std::vector<llama_token> tokens(strlen(c_prompt) + 4);
    int n_tokens = llama_tokenize(vocab, c_prompt, strlen(c_prompt), tokens.data(), tokens.size(), true, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, c_prompt, strlen(c_prompt), tokens.data(), tokens.size(), true, true);
    }
    tokens.resize(n_tokens);

    // Initialize sampler
    struct llama_sampler * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    int n_past = 0;
    while (n_past < 512) {
        if (g_stop_requested.load()) {
            LOGI("GENERATE_CANCELLED: Hardware interrupt triggered.");
            break;
        }

        if (llama_decode(g_ctx, llama_batch_get_one(tokens.data(), tokens.size()))) {
            LOGE("DECODE_FAILED: Error during token generation.");
            break;
        }

        n_past += tokens.size();
        tokens.clear();

        const llama_token id = llama_sampler_sample(smpl, g_ctx, -1);
        llama_sampler_accept(smpl, id);

        if (llama_vocab_is_eog(vocab, id)) break;

        char buf[128];
        int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            std::string piece(buf, n);
            jstring jPiece = env->NewStringUTF(piece.c_str());
            env->CallObjectMethod(callback, mid, jPiece);
            env->DeleteLocalRef(jPiece);
        }

        tokens.push_back(id);
    }

    llama_sampler_free(smpl);
    env->ReleaseStringUTFChars(prompt, c_prompt);
}

extern "C" JNIEXPORT void JNICALL
Java_com_scypheon_sdk_core_agent_orchestrator_GemmaNativeRuntime_nativeRelease(JNIEnv* env, jobject obj) {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_initialized.store(false);
}
