// Single-producer / single-consumer ring buffer used to pass events from Kotlin to the audio thread.
//
// The audio thread is the only consumer and never blocks: pop() is wait-free. Several Kotlin threads
// may call into the engine, so the engine serialises *producers* with a mutex on the JNI side; that
// mutex is never touched by the audio callback.
#pragma once

#include <atomic>
#include <cstddef>
#include <cstdint>

namespace strumwise {

template <typename T, size_t Capacity>
class LockFreeQueue {
    static_assert((Capacity & (Capacity - 1)) == 0, "Capacity must be a power of two");

public:
    /** Returns false (dropping the item) if the queue is full. Never blocks. */
    bool push(const T &item) {
        const size_t write = write_.load(std::memory_order_relaxed);
        const size_t read = read_.load(std::memory_order_acquire);
        if (write - read == Capacity) return false;
        buffer_[write & (Capacity - 1)] = item;
        write_.store(write + 1, std::memory_order_release);
        return true;
    }

    /** Returns false if empty. Wait-free; safe on the audio thread. */
    bool pop(T &out) {
        const size_t read = read_.load(std::memory_order_relaxed);
        const size_t write = write_.load(std::memory_order_acquire);
        if (read == write) return false;
        out = buffer_[read & (Capacity - 1)];
        read_.store(read + 1, std::memory_order_release);
        return true;
    }

private:
    T buffer_[Capacity];
    alignas(64) std::atomic<size_t> write_{0};
    alignas(64) std::atomic<size_t> read_{0};
};

/** Everything Kotlin can ask the audio thread to do. Plain data, trivially copyable. */
struct EngineEvent {
    enum class Type : uint8_t {
        NoteOn,
        MuteString,
        MuteAll,
        SetVolume,
        SetTuning,
        SetSoundType,
        SetMetronome,
    };
    Type type = Type::NoteOn;
    int32_t string = 0;         // string index, or beats per bar for SetMetronome
    int32_t fret = 0;
    float value = 0.0f;         // velocity, volume, bpm, or sound type
    int64_t delayFrames = 0;    // NoteOn: frames to wait after the event is dequeued (strum spacing)
    int64_t atNanos = 0;        // SetMetronome: audio-clock time of the first click
    float tuning[6] = {0, 0, 0, 0, 0, 0};  // SetTuning: open-string frequencies in Hz
};

}  // namespace strumwise
