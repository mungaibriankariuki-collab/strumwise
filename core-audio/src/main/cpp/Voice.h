// Common interface for everything that can sound a single string: the Karplus-Strong synth voice and
// the sample-playback voice. The mixer only talks to this interface, so recorded samples can replace
// synthesis without touching the engine.
#pragma once

namespace strumwise {

class Voice {
public:
    virtual ~Voice() = default;

    /** Called (off the audio thread) whenever the stream opens with a new sample rate. */
    virtual void prepare(float sampleRate) = 0;

    /** Start a note. Runs on the audio thread: must not allocate or lock. */
    virtual void noteOn(float frequencyHz, float velocity) = 0;

    /** Quickly damp the note, like a fretting hand or palm resting on the string. */
    virtual void damp() = 0;

    /** Write (not add) `frames` mono samples into `out`. */
    virtual void render(float *out, int frames) = 0;

    virtual bool isActive() const = 0;
    virtual bool isDamping() const = 0;

    /** Hard stop, used when the voice is stolen or the stream restarts. */
    virtual void reset() = 0;
};

}  // namespace strumwise
