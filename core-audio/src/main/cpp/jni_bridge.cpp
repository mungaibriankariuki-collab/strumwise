// JNI bridge between com.strumwise.audio.NativeBridge (Kotlin) and strumwise::GuitarEngine.
// The engine pointer travels to Kotlin as an opaque jlong handle.
#include <jni.h>
#include <oboe/Oboe.h>

#include <memory>

#include "GuitarEngine.h"

using strumwise::GuitarEngine;

namespace {
GuitarEngine *engine(jlong handle) { return reinterpret_cast<GuitarEngine *>(handle); }
}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_strumwise_audio_NativeBridge_oboeVersion(JNIEnv *env, jobject) {
    return env->NewStringUTF(oboe::getVersionText());
}

JNIEXPORT jlong JNICALL
Java_com_strumwise_audio_NativeBridge_create(JNIEnv *, jobject) {
    return reinterpret_cast<jlong>(new GuitarEngine());
}

JNIEXPORT void JNICALL
Java_com_strumwise_audio_NativeBridge_destroy(JNIEnv *, jobject, jlong handle) {
    delete engine(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_strumwise_audio_NativeBridge_start(JNIEnv *, jobject, jlong handle) {
    return engine(handle)->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_strumwise_audio_NativeBridge_stop(JNIEnv *, jobject, jlong handle) {
    engine(handle)->stop();
}

JNIEXPORT void JNICALL
Java_com_strumwise_audio_NativeBridge_noteOn(JNIEnv *, jobject, jlong handle, jint string, jint fret,
                                             jfloat velocity, jfloat delayMs) {
    engine(handle)->noteOn(string, fret, velocity, delayMs);
}

JNIEXPORT void JNICALL
Java_com_strumwise_audio_NativeBridge_muteString(JNIEnv *, jobject, jlong handle, jint string) {
    engine(handle)->muteString(string);
}

JNIEXPORT void JNICALL
Java_com_strumwise_audio_NativeBridge_muteAll(JNIEnv *, jobject, jlong handle) {
    engine(handle)->muteAll();
}

JNIEXPORT void JNICALL
Java_com_strumwise_audio_NativeBridge_setMasterVolume(JNIEnv *, jobject, jlong handle, jfloat volume) {
    engine(handle)->setMasterVolume(volume);
}

JNIEXPORT void JNICALL
Java_com_strumwise_audio_NativeBridge_setTuning(JNIEnv *env, jobject, jlong handle, jfloatArray hz) {
    if (env->GetArrayLength(hz) != GuitarEngine::kStrings) return;
    float values[GuitarEngine::kStrings];
    env->GetFloatArrayRegion(hz, 0, GuitarEngine::kStrings, values);
    engine(handle)->setTuning(values);
}

JNIEXPORT void JNICALL
Java_com_strumwise_audio_NativeBridge_setSoundType(JNIEnv *, jobject, jlong handle, jint type) {
    engine(handle)->setSoundType(type);
}

JNIEXPORT void JNICALL
Java_com_strumwise_audio_NativeBridge_setMetronome(JNIEnv *, jobject, jlong handle, jfloat bpm,
                                                   jlong firstClickNanos) {
    engine(handle)->setMetronome(bpm, firstClickNanos);
}

JNIEXPORT jlong JNICALL
Java_com_strumwise_audio_NativeBridge_audioTimeNanos(JNIEnv *, jobject, jlong handle, jlong monotonicNowNanos) {
    return engine(handle)->audioTimeNanos(monotonicNowNanos);
}

JNIEXPORT jint JNICALL
Java_com_strumwise_audio_NativeBridge_sampleRate(JNIEnv *, jobject, jlong handle) {
    return engine(handle)->sampleRate();
}

JNIEXPORT jdouble JNICALL
Java_com_strumwise_audio_NativeBridge_outputLatencyMillis(JNIEnv *, jobject, jlong handle) {
    return engine(handle)->outputLatencyMillis();
}

JNIEXPORT jint JNICALL
Java_com_strumwise_audio_NativeBridge_underrunCount(JNIEnv *, jobject, jlong handle) {
    return engine(handle)->underrunCount();
}

/**
 * Decodes WAV files (raw bytes read from assets on the Kotlin side) into a new sample bank and
 * publishes it. Runs on a background Kotlin thread. Returns the number of samples accepted.
 */
JNIEXPORT jint JNICALL
Java_com_strumwise_audio_NativeBridge_loadSamples(JNIEnv *env, jobject, jlong handle, jobjectArray wavFiles,
                                                  jfloatArray rootHz) {
    const jsize count = env->GetArrayLength(wavFiles);
    if (env->GetArrayLength(rootHz) != count) return 0;
    auto bank = std::make_unique<strumwise::SampleBank>();
    jfloat *roots = env->GetFloatArrayElements(rootHz, nullptr);
    for (jsize i = 0; i < count; ++i) {
        auto bytes = static_cast<jbyteArray>(env->GetObjectArrayElement(wavFiles, i));
        const jsize size = env->GetArrayLength(bytes);
        jbyte *data = env->GetByteArrayElements(bytes, nullptr);
        strumwise::Sample sample;
        if (strumwise::decodeWav16(reinterpret_cast<const uint8_t *>(data), static_cast<size_t>(size), sample)) {
            sample.rootHz = roots[i];
            bank->samples.push_back(std::move(sample));
        }
        env->ReleaseByteArrayElements(bytes, data, JNI_ABORT);
        env->DeleteLocalRef(bytes);
    }
    env->ReleaseFloatArrayElements(rootHz, roots, JNI_ABORT);
    const auto accepted = static_cast<jint>(bank->samples.size());
    engine(handle)->setSampleBank(std::move(bank));
    return accepted;
}

}  // extern "C"
