// Sample-playback voice: plays recorded guitar notes (16-bit WAV from assets), pitch-shifted to the
// requested fret by resampling with linear interpolation. Shares the Voice interface with the synth.
#pragma once

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <memory>
#include <string_view>
#include <vector>

#include "DspUtils.h"
#include "Voice.h"

namespace strumwise {

/** One decoded recording. `rootHz` is the pitch the sample was recorded at. */
struct Sample {
    std::vector<float> data;  // mono, normalised to [-1, 1]
    float sampleRate = 44100.0f;
    float rootHz = 82.41f;
};

/**
 * An immutable set of samples. Built off the audio thread, then published to the engine with an
 * atomic pointer swap; the audio thread only ever reads it.
 */
struct SampleBank {
    std::vector<Sample> samples;

    /** The sample whose root pitch is closest (in semitones) to the target, or nullptr if empty. */
    const Sample *closestTo(float hz) const {
        const Sample *best = nullptr;
        float bestDistance = 1e9f;
        const float target = hzToMidi(hz);
        for (const Sample &s : samples) {
            const float distance = std::fabs(hzToMidi(s.rootHz) - target);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = &s;
            }
        }
        return best;
    }
};

/**
 * Minimal RIFF/WAVE decoder for 16-bit PCM (mono or stereo, any sample rate).
 * Stereo files are mixed to mono. Returns false for anything else.
 */
inline bool decodeWav16(const uint8_t *bytes, size_t size, Sample &out) {
    auto u16 = [&](size_t at) { return static_cast<uint32_t>(bytes[at] | (bytes[at + 1] << 8)); };
    auto u32 = [&](size_t at) { return u16(at) | (u16(at + 2) << 16); };

    if (size < 12 || std::string_view(reinterpret_cast<const char *>(bytes), 4) != "RIFF" ||
        std::string_view(reinterpret_cast<const char *>(bytes) + 8, 4) != "WAVE") {
        return false;
    }
    uint32_t channels = 0, rate = 0, bits = 0, format = 0;
    size_t pos = 12;
    while (pos + 8 <= size) {
        const std::string_view id(reinterpret_cast<const char *>(bytes) + pos, 4);
        const uint32_t chunkSize = u32(pos + 4);
        const size_t body = pos + 8;
        if (body + chunkSize > size) return false;
        if (id == "fmt " && chunkSize >= 16) {
            format = u16(body);
            channels = u16(body + 2);
            rate = u32(body + 4);
            bits = u16(body + 14);
        } else if (id == "data") {
            if (format != 1 || bits != 16 || channels < 1 || channels > 2 || rate == 0) return false;
            const size_t frames = chunkSize / (2 * channels);
            out.data.resize(frames);
            for (size_t f = 0; f < frames; ++f) {
                float acc = 0.0f;
                for (uint32_t c = 0; c < channels; ++c) {
                    const auto raw = static_cast<int16_t>(u16(body + (f * channels + c) * 2));
                    acc += static_cast<float>(raw) / 32768.0f;
                }
                out.data[f] = acc / static_cast<float>(channels);
            }
            out.sampleRate = static_cast<float>(rate);
            return true;
        }
        pos = body + chunkSize + (chunkSize & 1);  // chunks are word-aligned
    }
    return false;
}

class SampleVoice final : public Voice {
public:
    void prepare(float sampleRate) override {
        outputRate_ = sampleRate;
        reset();
    }

    /** The bank to draw from on the next noteOn. Set by the engine on the audio thread. */
    void setBank(const SampleBank *bank) { bank_ = bank; }

    void noteOn(float frequencyHz, float velocity) override {
        sample_ = bank_ ? bank_->closestTo(frequencyHz) : nullptr;
        if (!sample_ || sample_->data.empty()) {
            active_ = false;
            return;
        }
        // Resampling step: pitch ratio times the sample-rate ratio.
        step_ = (frequencyHz / sample_->rootHz) * (sample_->sampleRate / outputRate_);
        position_ = 0.0;
        gain_ = 0.9f * std::pow(std::clamp(velocity, 0.05f, 1.0f), 1.4f);
        env_ = 1.0f;
        damping_ = false;
        active_ = true;
        // Softer notes are darker, as with the synth voice.
        tone_.setCutoff(1500.0f + 9000.0f * velocity, outputRate_);
        tone_.reset();
    }

    void damp() override { damping_ = active_; }

    void render(float *out, int frames) override {
        if (!active_) {
            std::fill(out, out + frames, 0.0f);
            return;
        }
        const float *data = sample_->data.data();
        const size_t length = sample_->data.size();
        const float envStep = damping_ ? std::exp(-1.0f / (0.02f * outputRate_)) : 1.0f;
        for (int i = 0; i < frames; ++i) {
            const auto index = static_cast<size_t>(position_);
            if (index + 1 >= length || env_ < 1e-4f) {
                std::fill(out + i, out + frames, 0.0f);
                active_ = false;
                return;
            }
            const auto frac = static_cast<float>(position_ - static_cast<double>(index));
            const float s = data[index] + frac * (data[index + 1] - data[index]);
            env_ *= envStep;
            out[i] = tone_.process(s) * gain_ * env_;
            position_ += step_;
        }
    }

    bool isActive() const override { return active_; }
    bool isDamping() const override { return damping_; }

    void reset() override {
        active_ = false;
        damping_ = false;
        sample_ = nullptr;
    }

private:
    const SampleBank *bank_ = nullptr;
    const Sample *sample_ = nullptr;
    OnePole tone_;
    float outputRate_ = 48000.0f;
    double position_ = 0.0;
    double step_ = 1.0;
    float gain_ = 1.0f;
    float env_ = 1.0f;
    bool active_ = false;
    bool damping_ = false;
};

}  // namespace strumwise
