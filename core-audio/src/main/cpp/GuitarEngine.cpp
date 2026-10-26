#include "GuitarEngine.h"

#include <android/log.h>
#include <time.h>

#include <algorithm>
#include <cmath>

#define LOG_TAG "StrumwiseAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace strumwise {

namespace {

constexpr int kMaxChunk = 256;
constexpr int64_t kNanosPerSecond = 1'000'000'000;
// Pre-gain into the bus so a full six-string strum lands around the limiter threshold.
constexpr float kMixGain = 0.42f;

int64_t monotonicNanos() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * kNanosPerSecond + ts.tv_nsec;
}

}  // namespace

GuitarEngine::GuitarEngine() {
    for (int i = 0; i < kVoices; ++i) slots_[i].synth.seed(0x51F15EEDu + 7919u * static_cast<uint32_t>(i));
    prepareDsp(48000.0f);
}

GuitarEngine::~GuitarEngine() {
    stop();
}

// -------------------------------------------------------------------------------------------------
// Stream lifecycle
// -------------------------------------------------------------------------------------------------

bool GuitarEngine::start() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    wantRunning_ = true;
    if (stream_) return true;
    return openStreamLocked();
}

void GuitarEngine::stop() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    wantRunning_ = false;
    closeStreamLocked();
}

bool GuitarEngine::openStreamLocked() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)  // falls back to Shared if unavailable
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Stereo)
            ->setUsage(oboe::Usage::Game)
            ->setContentType(oboe::ContentType::Music)
            ->setDataCallback(this)
            ->setErrorCallback(this);
    // No sample rate requested: we get the device's native rate and avoid resampling latency.

    const oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK) {
        LOGW("openStream failed: %s", oboe::convertToText(result));
        stream_.reset();
        return false;
    }

    const auto rate = static_cast<float>(stream_->getSampleRate());
    sampleRate_.store(stream_->getSampleRate(), std::memory_order_relaxed);
    // The callback isn't running yet, so touching audio-thread state here is safe.
    prepareDsp(rate);
    framesWritten_ = 0;
    callbackCount_ = 0;
    tsValid_.store(false, std::memory_order_release);

    // Two bursts is the usual sweet spot between latency and glitch resistance.
    stream_->setBufferSizeInFrames(stream_->getFramesPerBurst() * 2);

    if (stream_->requestStart() != oboe::Result::OK) {
        LOGW("requestStart failed");
        stream_->close();
        stream_.reset();
        return false;
    }
    running_.store(true, std::memory_order_release);
    LOGI("Stream open: %d Hz, burst %d, %s, sharing %s", stream_->getSampleRate(), stream_->getFramesPerBurst(),
         oboe::convertToText(stream_->getAudioApi()), oboe::convertToText(stream_->getSharingMode()));
    return true;
}

void GuitarEngine::closeStreamLocked() {
    // Freeze the clock where it is; the next stream continues counting from here.
    timeBaseNanos_.store(audioTimeNanos(monotonicNanos()), std::memory_order_release);
    running_.store(false, std::memory_order_release);
    tsValid_.store(false, std::memory_order_release);
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
}

void GuitarEngine::onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) {
    // Typically ErrorDisconnected: the output device changed (headphones plugged/unplugged, BT).
    // Oboe has already closed the stream; open a fresh one on the new default device.
    LOGW("Stream closed with %s; reopening", oboe::convertToText(error));
    std::lock_guard<std::mutex> lock(streamMutex_);
    if (stream_.get() != stream) return;  // stop() already replaced or closed it
    timeBaseNanos_.store(audioTimeNanos(monotonicNanos()), std::memory_order_release);
    running_.store(false, std::memory_order_release);
    tsValid_.store(false, std::memory_order_release);
    stream_.reset();
    if (wantRunning_) openStreamLocked();
}

void GuitarEngine::prepareDsp(float sampleRate) {
    for (VoiceSlot &slot : slots_) {
        slot.synth.prepare(sampleRate);
        slot.sampler.prepare(sampleRate);
        slot.voice = nullptr;
        slot.string = -1;
    }
    for (PendingNote &p : pending_) p.used = false;
    bodyL_.prepare(sampleRate);
    bodyR_.prepare(sampleRate);
    limiter_.prepare(sampleRate);
    click_.prepare(sampleRate);
    volumeSmooth_ = 1.0f - std::exp(-1.0f / (0.01f * sampleRate));
    metronomeBpm_ = 0.0f;
}

// -------------------------------------------------------------------------------------------------
// Control (producer side)
// -------------------------------------------------------------------------------------------------

void GuitarEngine::pushEvent(const EngineEvent &event) {
    std::lock_guard<std::mutex> lock(producerMutex_);
    events_.push(event);  // if 512 events are somehow pending, dropping one is the safest option
}

void GuitarEngine::noteOn(int string, int fret, float velocity, float delayMs) {
    if (string < 0 || string >= kStrings) return;
    EngineEvent e;
    e.type = EngineEvent::Type::NoteOn;
    e.string = string;
    e.fret = std::clamp(fret, 0, 24);
    e.value = velocity;
    e.delayFrames = static_cast<int64_t>(std::max(0.0f, delayMs) * 0.001f * static_cast<float>(sampleRate()));
    pushEvent(e);
}

void GuitarEngine::muteString(int string) {
    if (string < 0 || string >= kStrings) return;
    EngineEvent e;
    e.type = EngineEvent::Type::MuteString;
    e.string = string;
    pushEvent(e);
}

void GuitarEngine::muteAll() {
    EngineEvent e;
    e.type = EngineEvent::Type::MuteAll;
    pushEvent(e);
}

void GuitarEngine::setMasterVolume(float volume) {
    EngineEvent e;
    e.type = EngineEvent::Type::SetVolume;
    e.value = std::clamp(volume, 0.0f, 1.0f);
    pushEvent(e);
}

void GuitarEngine::setTuning(const float openStringHz[kStrings]) {
    EngineEvent e;
    e.type = EngineEvent::Type::SetTuning;
    for (int i = 0; i < kStrings; ++i) e.tuning[i] = openStringHz[i];
    pushEvent(e);
}

void GuitarEngine::setSoundType(int type) {
    EngineEvent e;
    e.type = EngineEvent::Type::SetSoundType;
    e.value = static_cast<float>(type);
    pushEvent(e);
}

void GuitarEngine::setMetronome(float bpm, int64_t firstClickNanos, int beatsPerBar) {
    EngineEvent e;
    e.type = EngineEvent::Type::SetMetronome;
    e.value = bpm;
    e.string = std::clamp(beatsPerBar, 1, 12);  // reuses the int field
    e.atNanos = firstClickNanos;
    pushEvent(e);
}

void GuitarEngine::setSampleBank(std::unique_ptr<SampleBank> bank) {
    std::lock_guard<std::mutex> lock(producerMutex_);
    bank_.store(bank.get(), std::memory_order_release);
    // Old banks may still be referenced by a sounding voice, so they live until the engine dies.
    ownedBanks_.push_back(std::move(bank));
}

// -------------------------------------------------------------------------------------------------
// Developer latency probe
// -------------------------------------------------------------------------------------------------

bool GuitarEngine::startLatencyProbe() {
    std::lock_guard<std::mutex> lock(probeMutex_);
    probe_.close();
    return probe_.open();
}

void GuitarEngine::stopLatencyProbe() {
    std::lock_guard<std::mutex> lock(probeMutex_);
    probe_.close();
}

double GuitarEngine::probeResultMillis() const {
    const int64_t nanos = probe_.resultNanos();
    return nanos < 0 ? -1.0 : static_cast<double>(nanos) / 1e6;
}

// -------------------------------------------------------------------------------------------------
// Clock
// -------------------------------------------------------------------------------------------------

int64_t GuitarEngine::audioTimeNanos(int64_t nowNanos) {
    int64_t last = lastAudioTimeNanos_.load(std::memory_order_acquire);
    if (!running_.load(std::memory_order_acquire) || !tsValid_.load(std::memory_order_acquire)) return last;

    // Seqlock read: retry if the audio thread published mid-read.
    int64_t frame, at;
    uint32_t before, after;
    do {
        before = tsSequence_.load(std::memory_order_acquire);
        frame = tsFrame_.load(std::memory_order_relaxed);
        at = tsTimeNanos_.load(std::memory_order_relaxed);
        after = tsSequence_.load(std::memory_order_acquire);
    } while ((before & 1u) || before != after);

    const double rate = static_cast<double>(sampleRate());
    // Extrapolate from the last timestamp, but never further than 200 ms (e.g. if callbacks stall).
    const int64_t since = std::clamp<int64_t>(nowNanos - at, 0, 200'000'000);
    int64_t now = timeBaseNanos_.load(std::memory_order_acquire) +
                  static_cast<int64_t>(static_cast<double>(frame) * 1e9 / rate) + since;

    // Publish the max so every caller sees a non-decreasing clock.
    while (now > last && !lastAudioTimeNanos_.compare_exchange_weak(last, now, std::memory_order_acq_rel)) {
    }
    return std::max(now, last);
}

void GuitarEngine::publishTimestamp(oboe::AudioStream *stream) {
    int64_t frame;
    int64_t at;
    auto ts = stream->getTimestamp(CLOCK_MONOTONIC);
    if (ts) {
        frame = ts.value().position;
        at = ts.value().timestamp;
    } else {
        // No hardware timestamp yet (or OpenSL ES): estimate that everything still in the buffer is
        // yet to be heard.
        frame = std::max<int64_t>(0, framesWritten_ - stream->getBufferSizeInFrames());
        at = monotonicNanos();
    }
    tsSequence_.fetch_add(1, std::memory_order_acq_rel);  // odd: write in progress
    tsFrame_.store(frame, std::memory_order_relaxed);
    tsTimeNanos_.store(at, std::memory_order_relaxed);
    tsSequence_.fetch_add(1, std::memory_order_acq_rel);  // even: consistent
    tsValid_.store(true, std::memory_order_release);
}

double GuitarEngine::outputLatencyMillis() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    if (!stream_) return -1.0;
    auto latency = stream_->calculateLatencyMillis();
    return latency ? latency.value() : -1.0;
}

int32_t GuitarEngine::underrunCount() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    if (!stream_) return 0;
    auto xruns = stream_->getXRunCount();
    return xruns ? xruns.value() : 0;
}

// -------------------------------------------------------------------------------------------------
// Audio thread
// -------------------------------------------------------------------------------------------------

oboe::DataCallbackResult GuitarEngine::onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) {
    static thread_local bool ftzEnabled = false;
    if (!ftzEnabled) {
        enableFlushToZero();
        ftzEnabled = true;
    }

    EngineEvent event;
    while (events_.pop(event)) handleEvent(event);

    auto *out = static_cast<float *>(audioData);
    const int channels = stream->getChannelCount();
    int done = 0;
    while (done < numFrames) {
        const int64_t now = framesWritten_ + done;
        // Start every scheduled note that is due, and find the next one inside this buffer so the
        // render can be split exactly there (sample-accurate strum spacing).
        int64_t nextDue = framesWritten_ + numFrames;
        for (PendingNote &p : pending_) {
            if (!p.used) continue;
            if (p.dueFrame <= now) {
                p.used = false;
                startNote(p.string, p.fret, p.velocity);
            } else {
                nextDue = std::min(nextDue, p.dueFrame);
            }
        }
        const int chunk = static_cast<int>(std::min<int64_t>({nextDue - now, numFrames - done, kMaxChunk}));
        renderChunk(out + done * channels, channels, chunk, now);
        done += chunk;
    }
    framesWritten_ += numFrames;

    if ((callbackCount_++ & 3) == 0) publishTimestamp(stream);
    return oboe::DataCallbackResult::Continue;
}

void GuitarEngine::handleEvent(const EngineEvent &e) {
    switch (e.type) {
        case EngineEvent::Type::NoteOn: {
            if (e.delayFrames <= 0) {
                startNote(e.string, e.fret, e.value);
                break;
            }
            for (PendingNote &p : pending_) {
                if (!p.used) {
                    p = {e.string, e.fret, e.value, framesWritten_ + e.delayFrames, true};
                    return;
                }
            }
            startNote(e.string, e.fret, e.value);  // schedule full: play now rather than drop
            break;
        }
        case EngineEvent::Type::MuteString:
            for (PendingNote &p : pending_) {
                if (p.used && p.string == e.string) p.used = false;
            }
            for (VoiceSlot &slot : slots_) {
                if (slot.voice && slot.string == e.string) slot.voice->damp();
            }
            break;
        case EngineEvent::Type::MuteAll:
            for (PendingNote &p : pending_) p.used = false;
            for (VoiceSlot &slot : slots_) {
                if (slot.voice) slot.voice->damp();
            }
            break;
        case EngineEvent::Type::SetVolume:
            volumeTarget_ = e.value;
            break;
        case EngineEvent::Type::SetTuning:
            for (int i = 0; i < kStrings; ++i) {
                if (e.tuning[i] > 20.0f) tuning_[i] = e.tuning[i];
            }
            break;
        case EngineEvent::Type::SetSoundType:
            soundType_ = static_cast<int>(e.value);
            break;
        case EngineEvent::Type::SetMetronome: {
            metronomeBpm_ = e.value;
            beatsPerBar_ = e.string;
            if (metronomeBpm_ <= 0.0f) break;
            const double rate = static_cast<double>(sampleRate());
            const double interval = rate * 60.0 / metronomeBpm_;
            const int64_t base = timeBaseNanos_.load(std::memory_order_acquire);
            nextClickFrame_ = static_cast<double>(e.atNanos - base) * rate / 1e9;
            clickBeat_ = 0;
            // A start time in the past just joins the beat grid at the next click.
            while (nextClickFrame_ < static_cast<double>(framesWritten_)) {
                nextClickFrame_ += interval;
                ++clickBeat_;
            }
            break;
        }
    }
}

void GuitarEngine::startNote(int string, int fret, float velocity) {
    // A new note on a string silences whatever that string was playing, as on a real guitar.
    for (VoiceSlot &slot : slots_) {
        if (slot.voice && slot.string == string && slot.voice->isActive()) slot.voice->damp();
    }

    // Pick a free slot; otherwise steal the oldest, preferring voices that are already being damped.
    VoiceSlot *target = nullptr;
    for (VoiceSlot &slot : slots_) {
        if (!slot.voice || !slot.voice->isActive()) {
            target = &slot;
            break;
        }
    }
    if (!target) {
        for (int pass = 0; pass < 2 && !target; ++pass) {
            for (VoiceSlot &slot : slots_) {
                if (pass == 0 && !slot.voice->isDamping()) continue;
                if (!target || slot.order < target->order) target = &slot;
            }
        }
        target->voice->reset();
    }

    const float hz = tuning_[string] * std::exp2(static_cast<float>(fret) / 12.0f);
    SampleBank *bank = bank_.load(std::memory_order_acquire);
    if (soundType_ == kSamples && bank && !bank->samples.empty()) {
        target->sampler.setBank(bank);
        target->voice = &target->sampler;
    } else {
        target->voice = &target->synth;
    }
    target->voice->noteOn(hz, velocity);
    target->string = string;
    target->order = ++noteCounter_;

    // Spread the strings a little across the stereo field (low strings left, high strings right),
    // with constant-power panning.
    const float pan = -0.3f + 0.6f * static_cast<float>(string) / static_cast<float>(kStrings - 1);
    const float angle = (pan + 1.0f) * kPi * 0.25f;
    target->gainL = std::cos(angle);
    target->gainR = std::sin(angle);
}

void GuitarEngine::renderChunk(float *out, int channels, int frames, int64_t startFrame) {
    std::fill(mixL_, mixL_ + frames, 0.0f);
    std::fill(mixR_, mixR_ + frames, 0.0f);

    for (VoiceSlot &slot : slots_) {
        if (!slot.voice || !slot.voice->isActive()) continue;
        slot.voice->render(voiceBuffer_, frames);
        for (int i = 0; i < frames; ++i) {
            mixL_[i] += voiceBuffer_[i] * slot.gainL;
            mixR_[i] += voiceBuffer_[i] * slot.gainR;
        }
    }

    const double interval = metronomeBpm_ > 0.0f ? static_cast<double>(sampleRate()) * 60.0 / metronomeBpm_ : 0.0;
    const auto rate = static_cast<float>(sampleRate());
    if (pulseRequested_.exchange(false, std::memory_order_acq_rel)) {
        // The pulse's first sample is this chunk's first sample; tell the probe when that was written.
        pulseRemaining_ = static_cast<int>(0.004f * rate);
        pulsePhase_ = 0.0f;
        const auto chunkOffset = static_cast<int64_t>(static_cast<double>(startFrame - framesWritten_) * 1e9 / rate);
        probe_.arm(monotonicNanos() + chunkOffset);
    }
    for (int i = 0; i < frames; ++i) {
        float l = bodyL_.process(mixL_[i] * kMixGain);
        float r = bodyR_.process(mixR_[i] * kMixGain);

        // Metronome clicks land on exact stream frames, so they line up with the audio clock.
        if (interval > 0.0 && static_cast<double>(startFrame + i) >= nextClickFrame_) {
            click_.trigger(clickBeat_ % beatsPerBar_ == 0);
            nextClickFrame_ += interval;
            ++clickBeat_;
        }
        const float c = click_.next();
        l += c;
        r += c;

        volume_ += volumeSmooth_ * (volumeTarget_ - volume_);
        l *= volume_;
        r *= volume_;
        limiter_.process(l, r);

        if (pulseRemaining_ > 0) {
            // 4 ms, 2 kHz burst: short, loud and easy to find in the mic signal.
            const float p = 0.7f * std::sin(pulsePhase_);
            pulsePhase_ += 2.0f * kPi * 2000.0f / rate;
            --pulseRemaining_;
            l = p;
            r = p;
        }

        if (channels == 1) {
            out[i] = 0.5f * (l + r);
        } else {
            out[i * channels] = l;
            out[i * channels + 1] = r;
            for (int c = 2; c < channels; ++c) out[i * channels + c] = 0.0f;
        }
    }
}

}  // namespace strumwise
