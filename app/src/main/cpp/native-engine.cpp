#include <jni.h>
#include <string>
#include <android/log.h>
#include <algorithm> // For converting text to lowercase

#define LOG_TAG "HalanoiNativeCore"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_halanoi_app_HalanoiCore_initializeSovereignEngine(JNIEnv* env, jobject) {
    LOGD("⚡ BARE METAL INITIATED: Halanoi Sovereign Engine is booting up...");
    LOGD("⚡ ENGINE READY: C++ Logic Gate Armed.");
    return env->NewStringUTF("Sovereign Engine Active (C++ Core)");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_halanoi_app_HalanoiCore_analyzeText(JNIEnv* env, jobject, jstring text) {
    // 1. Grab the string from Kotlin
    const char *c_text = env->GetStringUTFChars(text, nullptr);
    if (c_text == nullptr) {
        return JNI_FALSE;
    }
    std::string screenText(c_text);
    env->ReleaseStringUTFChars(text, c_text); // Prevent memory leaks!

    // 2. Convert to lowercase for easy matching
    std::transform(screenText.begin(), screenText.end(), screenText.begin(), ::tolower);

    // 3. The C++ Offline AI Brain (MVP Logic)
    // In V2, we will replace this array with a Quantized Gemma NPU Model!
    std::string brainrot[] = {"mrbeast", "prank", "funny", "tiktok", "gta", "survive", "challenge", "spider-man", "trailer"};

    bool detected = false;

    // 4. Scan the text instantly at the bare-metal level
    for (const auto& keyword : brainrot) {
        if (screenText.find(keyword) != std::string::npos) {
            LOGD("🚨 BARE METAL CATCH! C++ detected brainrot keyword: %s", keyword.c_str());
            detected = true;
            break;
        }
    }

    // 5. Fire the result back up to Android
    return detected ? JNI_TRUE : JNI_FALSE;
}
