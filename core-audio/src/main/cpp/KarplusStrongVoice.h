// Extended Karplus-Strong plucked string (after Jaffe & Smith, "Extensions of the Karplus-Strong
// Plucked-String Algorithm", 1983).
//
// Signal flow, one sample:
//
//     ┌──────────────── delay line (N samples) ◄────────────────┐
//     │                                                          │
//     └─► loop lowpass  ──► decay gain ──► tuning allpass ──────┘
//           (S, 1-S)          (g)            (fractional delay)
//
// The delay line is pre-loaded with the excitation ("pluck"): a blend of a triangular string shape
// and a short burst of filtered noise, darkened at low velocity and passed through a pick-position
// comb filter. Each trip around the loop the lowpass removes a little high-frequency energy, so the
// tone starts bright and mellows, just like a real string.
#pragma once

#include <algorithm>
#include <cmath>

#include "DspUtils.h"
#include "Voice.h"

namespace strumwise {

class KarplusStrongVoice final : public Voice {
public:
    // Large enough for ~47 Hz at 192 kHz; Drop D's low D2 (73 Hz) at 96 kHz needs ~1300.
    static constexpr int kMaxDelay = 4096;
    static constexpr int kMask = kMaxDelay - 1;

    explicit KarplusStrongVoice(uint32_t seed = 1u) : noise_(seed) {}

    /** Give each voice its own noise sequence so simultaneous strings don't pluck identically. */
    void seed(uint32_t value) { noise_ = NoiseSource(value); }

    void prepare(float sampleRate) override {
        sampleRate_ = sampleRate;
        reset();
    }

    void reset() override {
        std::fill(std::begin(line_), std::end(line_), 0.0f);
        active_ = false;
        damping_ = false;
        prevX_ = apX1_ = apY1_ = 0.0f;
        env_ = 1.0f;
        level_.reset();
        dc_.reset();
    }

    void noteOn(float frequencyHz, float velocity) override {
        velocity = std::clamp(velocity, 0.05f, 1.0f);
        frequencyHz = std::clamp(frequencyHz, 30.0f, sampleRate_ * 0.25f);

        // ---- Tuning ---------------------------------------------------------------------------
        // Total loop delay must equal one period P. The loop lowpass contributes S samples of phase
        // delay and the allpass contributes d in [0.5, 1.5); the integer delay line provides the rest.
        const float period = sampleRate_ / frequencyHz;
        length_ = std::clamp(static_cast<int>(std::floor(period - kBrightness - 0.5f)), 2, kMaxDelay - 1);
        const float d = period - kBrightness - static_cast<float>(length_);
        apCoef_ = (1.0f - d) / (1.0f + d);

        // ---- Decay ----------------------------------------------------------------------------
        // Low strings ring longer than high ones; T60 roughly follows 1/sqrt(f).
        const float t60 = std::clamp(4.5f * std::sqrt(110.0f / frequencyHz), 0.9f, 7.0f);
        loopGain_ = loopGainFor(t60, frequencyHz);
        dampedGain_ = loopGainFor(0.07f, frequencyHz);  // a resting hand kills it in ~70 ms

        // ---- Excitation -----------------------------------------------------------------------
        // Pick position as a fraction of the string length; ~1/8 is a typical strumming spot near
        // the sound hole. A touch of randomness keeps repeated notes from sounding identical.
        const float pickPos = 0.12f + 0.03f * (noise_.next() * 0.5f + 0.5f);
        const int pickOffset = std::max(1, static_cast<int>(std::lround(pickPos * length_)));

        // Softer plucks are darker: the noise burst is lowpassed with a velocity-dependent cutoff.
        OnePole tone;
        tone.setCutoff(900.0f + 7000.0f * velocity * velocity, sampleRate_);

        // 1) Triangle (string displaced at the pick point) + filtered noise into a scratch period.
        float sum = 0.0f;
        for (int i = 0; i < length_; ++i) {
            const float tri = (i < pickOffset)
                    ? static_cast<float>(i) / static_cast<float>(pickOffset)
                    : static_cast<float>(length_ - i) / static_cast<float>(length_ - pickOffset);
            const float n = tone.process(noise_.next());
            scratch_[i] = 0.55f * tri + 0.45f * n * 1.6f;
        }
        // Run the tone filter over the period a second time so the start and end join smoothly.
        for (int i = 0; i < length_; ++i) {
            scratch_[i] = tone.process(scratch_[i]);
        }
        // 2) Pick-position comb: x[n] - x[n - M] puts notches at harmonics the pick point can't excite.
        float peak = 1e-6f;
        for (int i = 0; i < length_; ++i) {
            const int j = (i - pickOffset + length_) % length_;
            line_[i] = scratch_[i] - scratch_[j];
        }
        // 3) Remove DC so the loop doesn't carry an offset, then normalise.
        for (int i = 0; i < length_; ++i) sum += line_[i];
        const float mean = sum / static_cast<float>(length_);
        for (int i = 0; i < length_; ++i) {
            line_[i] -= mean;
            peak = std::max(peak, std::fabs(line_[i]));
        }
        const float amplitude = 0.55f * std::pow(velocity, 1.4f) / peak;
        for (int i = 0; i < length_; ++i) line_[i] *= amplitude;

        writeIndex_ = length_;
        prevX_ = apX1_ = apY1_ = 0.0f;
        env_ = 1.0f;
        damping_ = false;
        active_ = true;
        ageFrames_ = 0;
        level_.setCutoff(20.0f, sampleRate_);
        level_.reset(amplitude);
        dc_.reset();
    }

    void damp() override {
        if (!active_) return;
        damping_ = true;
    }

    void render(float *out, int frames) override {
        if (!active_) {
            std::fill(out, out + frames, 0.0f);
            return;
        }
        const float gain = damping_ ? dampedGain_ : loopGain_;
        // When damped, also fade the output so even long low notes stop within ~80 ms.
        const float envStep = damping_ ? std::exp(-1.0f / (0.025f * sampleRate_)) : 1.0f;

        for (int i = 0; i < frames; ++i) {
            const float x = line_[(writeIndex_ - length_) & kMask];
            // Loop lowpass: weighted two-point average (phase delay ≈ kBrightness samples).
            const float lp = gain * ((1.0f - kBrightness) * x + kBrightness * prevX_);
            prevX_ = x;
            // First-order allpass for the fractional part of the period: y = C·x + x1 − C·y1.
            const float ap = apCoef_ * lp + apX1_ - apCoef_ * apY1_;
            apX1_ = lp;
            apY1_ = ap;
            line_[writeIndex_ & kMask] = ap;
            ++writeIndex_;

            env_ *= envStep;
            const float y = dc_.process(x) * env_;
            out[i] = y;
            level_.process(std::fabs(y));
        }
        ageFrames_ += frames;

        // Retire the voice once it is inaudible (but give the attack time to build up first).
        if (ageFrames_ > static_cast<int>(0.05f * sampleRate_) && level_.value() < 2e-5f) {
            active_ = false;
        }
    }

    bool isActive() const override { return active_; }
    bool isDamping() const override { return damping_; }

private:
    // Weight of the previous sample in the loop lowpass. 0.5 is the classic (darkest) KS filter;
    // a little less keeps more sparkle in the upper harmonics.
    static constexpr float kBrightness = 0.42f;

    /** Per-period gain that makes the fundamental decay by 60 dB in t60 seconds. */
    float loopGainFor(float t60, float f0) const {
        const float perPeriod = std::pow(10.0f, -3.0f / (t60 * f0));
        // The loop lowpass already attenuates the fundamental; compensate so T60 stays accurate.
        const float w = 2.0f * kPi * f0 / sampleRate_;
        const float s = kBrightness;
        const float lpMag = std::sqrt((1.0f - s) * (1.0f - s) + s * s + 2.0f * s * (1.0f - s) * std::cos(w));
        return std::min(perPeriod / lpMag, 0.99995f);
    }

    float line_[kMaxDelay] = {};
    float scratch_[kMaxDelay] = {};
    NoiseSource noise_;
    OnePole level_;
    DcBlocker dc_;

    float sampleRate_ = 48000.0f;
    int length_ = 100;
    int writeIndex_ = 0;
    int ageFrames_ = 0;
    float apCoef_ = 0.0f;
    float loopGain_ = 0.99f;
    float dampedGain_ = 0.9f;
    float prevX_ = 0.0f, apX1_ = 0.0f, apY1_ = 0.0f;
    float env_ = 1.0f;
    bool active_ = false;
    bool damping_ = false;
};

}  // namespace strumwise
