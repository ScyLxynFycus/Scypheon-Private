#include <jni.h>
#include <string>
#include <vector>
#include "sha256.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_scypheon_sdk_core_security_AuditChain_nativeSignEntry(
    JNIEnv* env, jobject /* this */, jstring previousHash, jstring entryData) {
    
    const char* prev_hash_chars = env->GetStringUTFChars(previousHash, nullptr);
    const char* entry_data_chars = env->GetStringUTFChars(entryData, nullptr);
    
    std::string combined = std::string(prev_hash_chars) + std::string(entry_data_chars);
    std::string result = SHA256::hashString(combined);

    env->ReleaseStringUTFChars(previousHash, prev_hash_chars);
    env->ReleaseStringUTFChars(entryData, entry_data_chars);
    
    return env->NewStringUTF(result.c_str());
}
