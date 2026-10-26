// Round-trip latency probe for the developer overlay.
//
// The engine writes a short loud pulse to the speaker and notes the time (on CLOCK_MONOTONIC) at which
// that pulse sample was handed to Oboe. This probe listens on a low-latency input stream and notes the
// time the pulse arrives back in the app. The difference is the app-to-app round trip:
// output buffering + DAC + speaker -> air -> mic + ADC + input buffering.
//
// Needs RECORD_AUDIO, so the app only offers it in debug builds.
#pragma once

#include <oboe/Oboe.h>
#include <time.h>

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <memory>

namespace strumwise {

class LatencyProbe : public oboe::AudioStreamDataCallback {
public:
    bool open() {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Input)
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setSharingMode(oboe::SharingMode::Exclusive)
                ->setFormat(oboe::AudioFormat::Float)
                ->setChannelCount(oboe::ChannelCount::Mono)
                ->setInputPreset(oboe::InputPreset::Unprocessed)  // no AGC/noise suppression smearing the pulse
                ->setDataCallback(this);
        if (builder.openStream(stream_) != oboe::Result::OK) return false;
        sampleRate_ = static_cast<double>(stream_->getSampleRate());
        if (stream_->requestStart() != oboe::Result::OK) {
            close();
            return false;
        }
        return true;
    }

    void close() {
        if (stream_) {
            stream_->stop();
            stream_->close();
            stream_.reset();
        }
        armed_.store(false);
    }

    /** Called by the engine (audio thread) when the pulse sample was written. */
    void arm(int64_t pulseWrittenNanos) {
        pulseWrittenNanos_.store(pulseWrittenNanos, std::memory_order_relaxed);
        resultNanos_.store(-1, std::memory_order_relaxed);
        armed_.store(true, std::memory_order_release);
    }

    /** Last measured round trip in nanoseconds, or -1 while waiting. */
    int64_t resultNanos() const { return resultNanos_.load(std::memory_order_acquire); }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *, void *audioData, int32_t numFrames) override {
        const int64_t now = monotonicNanos();
        const auto *in = static_cast<const float *>(audioData);
        if (!armed_.load(std::memory_order_acquire)) {
            // Track the room's noise floor so the detector threshold adapts to it.
            for (int i = 0; i < numFrames; ++i) noiseFloor_ += 0.001f * (std::fabs(in[i]) - noiseFloor_);
            return oboe::DataCallbackResult::Continue;
        }
        const float threshold = std::max(0.05f, noiseFloor_ * 10.0f);
        const int64_t pulseAt = pulseWrittenNanos_.load(std::memory_order_relaxed);
        for (int i = 0; i < numFrames; ++i) {
            if (std::fabs(in[i]) < threshold) continue;
            // Sample i reached the app (numFrames - i) frames before this callback ran.
            const auto age = static_cast<int64_t>((numFrames - i) * 1e9 / sampleRate_);
            const int64_t arrived = now - age;
            if (arrived <= pulseAt) continue;  // noise from before the pulse
            resultNanos_.store(arrived - pulseAt, std::memory_order_release);
            armed_.store(false, std::memory_order_release);
            break;
        }
        return oboe::DataCallbackResult::Continue;
    }

private:
    static int64_t monotonicNanos() {
        timespec ts{};
        clock_gettime(CLOCK_MONOTONIC, &ts);
        return static_cast<int64_t>(ts.tv_sec) * 1'000'000'000 + ts.tv_nsec;
    }

    std::shared_ptr<oboe::AudioStream> stream_;
    double sampleRate_ = 48000.0;
    float noiseFloor_ = 0.0f;
    std::atomic<bool> armed_{false};
    std::atomic<int64_t> pulseWrittenNanos_{0};
    std::atomic<int64_t> resultNanos_{-1};
};

}  // namespace strumwise
