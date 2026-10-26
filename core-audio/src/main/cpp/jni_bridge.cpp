#include <jni.h>
#include <oboe/Oboe.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_strumwise_audio_NativeInfo_nativeOboeVersion(JNIEnv *env, jclass) {
    return env->NewStringUTF(oboe::getVersionText());
}
