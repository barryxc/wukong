#include "native_common.h"

#include <jni.h>

#include <algorithm>

extern "C" JNIEXPORT void JNICALL
Java_io_github_barryxc_wukong_hook_core_NativeBuildInfoHook_nativeInstall(
        JNIEnv* env,
        jobject,
        jobjectArray keys,
        jobjectArray values) {
    if (keys == nullptr || values == nullptr) {
        return;
    }
    const jsize count = std::min(env->GetArrayLength(keys), env->GetArrayLength(values));
    pthread_mutex_lock(&wukong::g_lock);
    wukong::g_properties.clear();
    wukong::g_prop_names.clear();
    wukong::clear_fake_prop_infos_locked();
    for (jsize i = 0; i < count; ++i) {
        auto key = reinterpret_cast<jstring>(env->GetObjectArrayElement(keys, i));
        auto value = reinterpret_cast<jstring>(env->GetObjectArrayElement(values, i));
        if (key != nullptr && value != nullptr) {
            const char* key_chars = env->GetStringUTFChars(key, nullptr);
            const char* value_chars = env->GetStringUTFChars(value, nullptr);
            if (key_chars != nullptr && value_chars != nullptr) {
                wukong::g_properties[key_chars] = value_chars;
            }
            if (key_chars != nullptr) {
                env->ReleaseStringUTFChars(key, key_chars);
            }
            if (value_chars != nullptr) {
                env->ReleaseStringUTFChars(value, value_chars);
            }
        }
        if (key != nullptr) {
            env->DeleteLocalRef(key);
        }
        if (value != nullptr) {
            env->DeleteLocalRef(value);
        }
    }
    pthread_mutex_unlock(&wukong::g_lock);
    LOGI("native props updated brand=%s model=%s hardware=%s",
         wukong::fake_property_value("ro.product.brand").c_str(),
         wukong::fake_property_value("ro.product.model").c_str(),
         wukong::fake_property_value("ro.hardware").c_str());
    wukong::install_hooks();
}
