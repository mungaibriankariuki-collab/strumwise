// The guitar audio engine: owns the Oboe output stream, the 16-voice polyphonic mixer, the master bus
// and the audio clock used by the rhythm game.
//
// Threading model
//  - Kotlin threads call the public "control" methods. They only push EngineEvents onto a lock-free
//    queue (producers are serialised by producerMutex_, which the audio thread never touches).
//  - The audio thread (onAudioReady) drains the queue, renders voices and publishes clock timestamps
//    through a seqlock. It never allocates, locks, logs or calls into the JVM.
//  - Stream open/close happens under streamMutex_ on Kotlin threads, or on Oboe's error thread after a
//    disconnect (headphones plugged/unplugged), never concurrently with a running callback.
#pragma once

#include <oboe/Oboe.h>

#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>

#include "KarplusStrongVoice.h"
#include "LockFreeQueue.h"
#include "MasterBus.h"
#include "SampleVoice.h"

namespace strumwise {

class GuitarEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    static constexpr int kStrings = 6;
    static constexpr int kVoices = 16;

    enum SoundType : int { kSynth = 0, kSamples = 1 };

    GuitarEngine();
    ~GuitarEngine() override;

    // ---- Lifecycle (Kotlin threads) ----
    bool start();
    void stop();

    // ---- Control (any Kotlin thread; non-blocking for the audio thread) ----
    void noteOn(int string, int fret, float velocity, float delayMs);
    void muteString(int string);
    void muteAll();
    void setMasterVolume(float volume);
    void setTuning(const float openStringHz[kStrings]);
    void setSoundType(int type);
    /**
     * bpm <= 0 turns the metronome off. The first click (a downbeat) sounds at `firstClickNanos` on the
     * audio clock; every `beatsPerBar`th click is accented.
     */
    void setMetronome(float bpm, int64_t firstClickNanos, int beatsPerBar);
    /** Takes ownership and publishes atomically; the previous bank is kept alive until destruction. */
    void setSampleBank(std::unique_ptr<SampleBank> bank);

    // ---- Queries ----
    /** Monotonic audio clock: nanoseconds of audio *presented* at the speaker since the engine started. */
    int64_t audioTimeNanos(int64_t monotonicNowNanos);
    int32_t sampleRate() const { return sampleRate_.load(std::memory_order_relaxed); }
    double outputLatencyMillis();
    bool isRunning() const { return running_.load(std::memory_order_acquire); }
    int32_t underrunCount();

    // ---- Oboe callbacks ----
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) override;

private:
    struct VoiceSlot {
        KarplusStrongVoice synth;
        SampleVoice sampler;
        Voice *voice = nullptr;  // whichever of the two is sounding
        int string = -1;
        uint64_t order = 0;      // monotonically increasing start counter, for oldest-first stealing
        float gainL = 0.7f, gainR = 0.7f;
    };

    struct PendingNote {
        int string = 0;
        int fret = 0;
        float velocity = 0.0f;
        int64_t dueFrame = 0;  // stream-local written-frame index
        bool used = false;
    };

    bool openStreamLocked();
    void closeStreamLocked();
    void prepareDsp(float sampleRate);
    void pushEvent(const EngineEvent &event);

    // Audio-thread helpers.
    void handleEvent(const EngineEvent &event);
    void startNote(int string, int fret, float velocity);
    void renderChunk(float *out, int channels, int frames, int64_t startFrame);
    void publishTimestamp(oboe::AudioStream *stream);

    std::mutex streamMutex_;
    std::mutex producerMutex_;
    std::shared_ptr<oboe::AudioStream> stream_;
    bool wantRunning_ = false;  // guarded by streamMutex_

    LockFreeQueue<EngineEvent, 512> events_;
    VoiceSlot slots_[kVoices];
    PendingNote pending_[64];
    BodyResonance bodyL_, bodyR_;
    SoftLimiter limiter_;
    ClickVoice click_;

    // Audio-thread state.
    float tuning_[kStrings] = {82.41f, 110.0f, 146.83f, 196.0f, 246.94f, 329.63f};
    float volumeTarget_ = 0.8f;
    float volume_ = 0.8f;
    float volumeSmooth_ = 0.001f;
    int soundType_ = kSynth;
    uint64_t noteCounter_ = 0;
    int64_t framesWritten_ = 0;  // stream-local
    int callbackCount_ = 0;
    float metronomeBpm_ = 0.0f;
    double nextClickFrame_ = 0.0;
    int64_t clickBeat_ = 0;
    int beatsPerBar_ = 4;
    float mixL_[256] = {};
    float mixR_[256] = {};
    float voiceBuffer_[256] = {};

    std::atomic<SampleBank *> bank_{nullptr};
    std::vector<std::unique_ptr<SampleBank>> ownedBanks_;  // guarded by producerMutex_

    // Clock: presented frame position + the monotonic time it was presented (seqlock).
    std::atomic<uint32_t> tsSequence_{0};
    std::atomic<int64_t> tsFrame_{0};
    std::atomic<int64_t> tsTimeNanos_{0};
    std::atomic<bool> tsValid_{false};
    std::atomic<int64_t> timeBaseNanos_{0};      // audio time accumulated by previous streams
    std::atomic<int64_t> lastAudioTimeNanos_{0}; // keeps the clock monotonic across readers/streams
    std::atomic<int32_t> sampleRate_{48000};
    std::atomic<bool> running_{false};
};

}  // namespace strumwise
