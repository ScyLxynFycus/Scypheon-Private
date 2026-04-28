#include <jni.h>
#include <string>
#include <thread>
#include <atomic>
#include <android/log.h>
#include "llama.h"

#define TAG "ScypheonNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static JavaVM* g_vm = nullptr;
static std::atomic<bool> g_cancel_requested{false};

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_vm = vm;
    LOGI("JNI_OnLoad: JVM reference captured.");
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_scypheon_app_services_ModelSandboxService_nativeInit(
    JNIEnv* env, jobject /* thiz */, jstring jPath, jint nCtx, jboolean useMmap) {
    
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    LOGI("nativeInit: Loading model from %s", path);

    llama_model_params m_params = llama_model_default_params();
    m_params.use_mmap = useMmap;
    m_params.n_gpu_layers = 0; // Force CPU for isolated process compatibility

    llama_model* model = llama_load_model_from_file(path, m_params);
    env->ReleaseStringUTFChars(jPath, path);
    
    if (!model) {
        LOGE("nativeInit: Failed to load model.");
        return 0L;
    }

    llama_context_params c_params = llama_context_default_params();
    c_params.n_ctx = nCtx;
    c_params.n_threads = 4; // Performance core cap
    c_params.n_threads_batch = 4;
    
    llama_context* ctx = llama_new_context_with_model(model, c_params);
    if (!ctx) {
        LOGE("nativeInit: Failed to create context.");
        llama_free_model(model);
        return 0L;
    }

    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_scypheon_app_services_ModelSandboxService_nativeInference(
    JNIEnv* env, jobject /* thiz */, jlong jCtx, jstring jPrompt, jobject jCallback) {
    
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
Java_com_scypheon_app_services_ModelSandboxService_nativeCancelInference(
    JNIEnv*, jobject, jlong) {
    g_cancel_requested = true;
    LOGI("nativeCancelInference: Cancel flag set.");
}

extern "C" JNIEXPORT void JNICALL
Java_com_scypheon_app_services_ModelSandboxService_nativeRelease(
    JNIEnv*, jobject, jlong jCtx) {
    llama_context* ctx = reinterpret_cast<llama_context*>(jCtx);
    if (ctx) {
        LOGI("nativeRelease: Freeing native resources.");
        llama_model* model = llama_get_model(ctx);
        llama_free(ctx);
        llama_free_model(model);
    }
}
