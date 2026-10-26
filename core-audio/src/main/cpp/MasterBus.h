// Master bus: acoustic body resonance, a gentle air lowpass, master volume and a soft limiter.
#pragma once

#include <algorithm>
#include <cmath>

#include "DspUtils.h"

namespace strumwise {

/**
 * Approximates the hollow body of an acoustic guitar with a few parallel resonant bandpass filters
 * mixed under the dry string signal. Real bodies have dozens of modes; these four carry most of the
 * character: the air (Helmholtz) resonance, the main top-plate mode, a lower-mid mode and a soft
 * presence lift.
 */
class BodyResonance {
public:
    void prepare(float sampleRate) {
        for (int i = 0; i < kModes; ++i) {
            modes_[i].setBandpass(kFrequencies[i], kQs[i], sampleRate);
            modes_[i].reset();
        }
        // Rolls off the fizzy top end that pure KS can have.
        air_.setLowpass(std::min(9000.0f, sampleRate * 0.45f), 0.707f, sampleRate);
        air_.reset();
    }

    float process(float x) {
        float y = x * kDry;
        for (int i = 0; i < kModes; ++i) y += modes_[i].process(x) * kGains[i];
        return air_.process(y);
    }

private:
    static constexpr int kModes = 4;
    static constexpr float kFrequencies[kModes] = {98.0f, 196.0f, 390.0f, 2800.0f};
    static constexpr float kQs[kModes] = {4.0f, 3.5f, 3.0f, 1.0f};
    static constexpr float kGains[kModes] = {0.45f, 0.35f, 0.18f, 0.10f};
    static constexpr float kDry = 0.85f;

    Biquad modes_[kModes];
    Biquad air_;
};

/**
 * Peak limiter with instant attack and smooth release, followed by a soft knee clipper so the output
 * can never exceed full scale even on the first sample of a hard six-string strum.
 */
class SoftLimiter {
public:
    void prepare(float sampleRate) {
        release_ = std::exp(-1.0f / (0.15f * sampleRate));
        gainSmooth_ = 1.0f - std::exp(-1.0f / (0.002f * sampleRate));
        envelope_ = 0.0f;
        gain_ = 1.0f;
    }

    /** Processes one stereo frame in place. */
    void process(float &left, float &right) {
        const float peak = std::max(std::fabs(left), std::fabs(right));
        envelope_ = peak > envelope_ ? peak : envelope_ * release_;
        const float target = envelope_ > kThreshold ? kThreshold / envelope_ : 1.0f;
        // Clamp down instantly, recover smoothly.
        gain_ = target < gain_ ? target : gain_ + gainSmooth_ * (target - gain_);
        left = softClip(left * gain_);
        right = softClip(right * gain_);
    }

private:
    static constexpr float kThreshold = 0.8f;
    static constexpr float kKnee = 0.9f;

    /** Linear below the knee, then a tanh-shaped approach to 1.0. */
    static float softClip(float x) {
        const float a = std::fabs(x);
        if (a <= kKnee) return x;
        const float over = (a - kKnee) / (1.0f - kKnee);
        // Rational tanh approximation, accurate enough here and much cheaper than std::tanh.
        const float t = std::min(over, 3.0f);
        const float shaped = t * (27.0f + t * t) / (27.0f + 9.0f * t * t);
        return std::copysign(kKnee + (1.0f - kKnee) * shaped, x);
    }

    float release_ = 0.9999f;
    float gainSmooth_ = 0.01f;
    float envelope_ = 0.0f;
    float gain_ = 1.0f;
};

/** Short pitched click for the metronome / calibration track; accented on the downbeat. */
class ClickVoice {
public:
    void prepare(float sampleRate) {
        sampleRate_ = sampleRate;
        decay_ = std::exp(-1.0f / (0.018f * sampleRate));
        amp_ = 0.0f;
    }

    void trigger(bool accent) {
        phaseStep_ = 2.0f * kPi * (accent ? 2400.0f : 1600.0f) / sampleRate_;
        phase_ = 0.0f;
        amp_ = accent ? 0.55f : 0.4f;
    }

    float next() {
        if (amp_ < 1e-4f) return 0.0f;
        const float y = std::sin(phase_) * amp_;
        phase_ += phaseStep_;
        if (phase_ > 2.0f * kPi) phase_ -= 2.0f * kPi;
        amp_ *= decay_;
        return y;
    }

private:
    float sampleRate_ = 48000.0f;
    float phase_ = 0.0f, phaseStep_ = 0.0f;
    float amp_ = 0.0f, decay_ = 0.999f;
};

}  // namespace strumwise
