package com.royan.taptowake;

/** Mendeteksi dua kali lambaian tangan di atas sensor jarak (proximity). */
final class WaveDetector {

    interface Listener {
        void onDoubleWave();
    }

    private static final long WINDOW_MS = 1500L;

    private final Listener listener;
    private boolean init;
    private boolean far = true;
    private int count;
    private long firstNearMs;

    WaveDetector(Listener listener) {
        this.listener = listener;
    }

    void reset() {
        init = false;
        far = true;
        count = 0;
    }

    /** True bila sensor jarak sedang tertutup (di saku, menghadap meja, tertutup tangan). */
    boolean isNear() {
        return !far;
    }

    void onValue(float value, float maxRange, long nowMs) {
        boolean near = value < Math.min(maxRange, 5.0f);
        if (!init) {
            init = true;
            far = !near;
            return;
        }
        if (near && far) {
            far = false;
            if (count == 0 || nowMs - firstNearMs > WINDOW_MS) {
                count = 1;
                firstNearMs = nowMs;
            } else {
                count = 0;
                listener.onDoubleWave();
            }
        } else if (!near) {
            far = true;
        }
    }
}
