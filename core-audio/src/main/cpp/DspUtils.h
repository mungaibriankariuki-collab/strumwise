// Small, allocation-free DSP building blocks shared by the voices and the master bus.
// Everything here is safe to call on the audio thread.
#pragma once

#include <cmath>
#include <cstdint>

namespace strumwise {

constexpr float kPi = 3.14159265358979323846f;

/** Fast, deterministic white noise in [-1, 1] (xorshift32). Never allocates or locks. */
class NoiseSource {
public:
    explicit NoiseSource(uint32_t seed = 0x9E3779B9u) : state_(seed ? seed : 1u) {}

    float next() {
        state_ ^= state_ << 13;
        state_ ^= state_ >> 17;
        state_ ^= state_ << 5;
        // Map the top 24 bits to [-1, 1].
        return static_cast<float>(state_ >> 8) * (2.0f / 16777215.0f) - 1.0f;
    }

private:
    uint32_t state_;
};

/** One-pole lowpass: y += a * (x - y). Used for smoothing parameters and darkening noise. */
class OnePole {
public:
    void setCutoff(float hz, float sampleRate) {
        a_ = 1.0f - std::exp(-2.0f * kPi * hz / sampleRate);
    }
    void setCoefficient(float a) { a_ = a; }
    void reset(float value = 0.0f) { y_ = value; }
    float process(float x) {
        y_ += a_ * (x - y_);
        return y_;
    }
    float value() const { return y_; }

private:
    float a_ = 1.0f;
    float y_ = 0.0f;
};

/**
 * RBJ-cookbook biquad in transposed direct form II.
 * Only the shapes we need: bandpass (constant 0 dB peak gain) and lowpass.
 */
class Biquad {
public:
    void setBandpass(float centerHz, float q, float sampleRate) {
        const float w0 = 2.0f * kPi * centerHz / sampleRate;
        const float alpha = std::sin(w0) / (2.0f * q);
        const float a0 = 1.0f + alpha;
        b0_ = alpha / a0;
        b1_ = 0.0f;
        b2_ = -alpha / a0;
        a1_ = -2.0f * std::cos(w0) / a0;
        a2_ = (1.0f - alpha) / a0;
    }

    void setLowpass(float cutoffHz, float q, float sampleRate) {
        const float w0 = 2.0f * kPi * cutoffHz / sampleRate;
        const float cosw = std::cos(w0);
        const float alpha = std::sin(w0) / (2.0f * q);
        const float a0 = 1.0f + alpha;
        b0_ = (1.0f - cosw) * 0.5f / a0;
        b1_ = (1.0f - cosw) / a0;
        b2_ = b0_;
        a1_ = -2.0f * cosw / a0;
        a2_ = (1.0f - alpha) / a0;
    }

    void reset() { z1_ = z2_ = 0.0f; }

    float process(float x) {
        const float y = b0_ * x + z1_;
        z1_ = b1_ * x - a1_ * y + z2_;
        z2_ = b2_ * x - a2_ * y;
        return y;
    }

private:
    float b0_ = 1.0f, b1_ = 0.0f, b2_ = 0.0f, a1_ = 0.0f, a2_ = 0.0f;
    float z1_ = 0.0f, z2_ = 0.0f;
};

/** First-order DC blocker: removes any offset that builds up in the string loops. */
class DcBlocker {
public:
    float process(float x) {
        const float y = x - x1_ + r_ * y1_;
        x1_ = x;
        y1_ = y;
        return y;
    }
    void reset() { x1_ = y1_ = 0.0f; }

private:
    static constexpr float r_ = 0.995f;
    float x1_ = 0.0f, y1_ = 0.0f;
};

inline float midiToHz(float midi) { return 440.0f * std::exp2((midi - 69.0f) / 12.0f); }
inline float hzToMidi(float hz) { return 69.0f + 12.0f * std::log2(hz / 440.0f); }

/** Flush denormals to zero on the calling thread; decaying filters otherwise get very slow. */
inline void enableFlushToZero() {
#if defined(__aarch64__)
    uint64_t fpcr;
    asm volatile("mrs %0, fpcr" : "=r"(fpcr));
    asm volatile("msr fpcr, %0" ::"r"(fpcr | (1ull << 24)));
#elif defined(__arm__) && defined(__ARM_PCS_VFP)
    uint32_t fpscr;
    asm volatile("vmrs %0, fpscr" : "=r"(fpscr));
    asm volatile("vmsr fpscr, %0" ::"r"(fpscr | (1u << 24)));
#elif defined(__SSE__) || defined(__x86_64__)
    uint32_t csr;
    asm volatile("stmxcsr %0" : "=m"(csr));
    csr |= 0x8040;  // FTZ | DAZ
    asm volatile("ldmxcsr %0" ::"m"(csr));
#endif
}

}  // namespace strumwise
