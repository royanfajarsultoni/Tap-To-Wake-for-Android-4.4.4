package com.royan.taptowake;

/**
 * Mendeteksi ketukan pada bodi/layar HP dari lonjakan accelerometer
 * (perubahan percepatan antar sampel), lalu mencari dua ketukan berurutan.
 */
final class KnockDetector {

    interface Listener {
        /** Setiap ketukan yang lolos filter (dipakai juga untuk layar uji). */
        void onTap(float jerk);

        void onDoubleKnock();
    }

    private static final long MIN_GAP_NS = 100000000L;    // 100 ms: abaikan getaran sisa ketukan yang sama
    private static final long MAX_GAP_NS = 500000000L;    // 500 ms: jeda terlama antar dua ketukan
    private static final long RING_NS = 150000000L;       // 150 ms: lama getaran sisa sebuah ketukan
    private static final long COOLDOWN_NS = 1200000000L;  // 1,2 dtk jeda setelah terpicu
    private static final float QUIET_MAX = 1.2f;          // di atas ini HP dianggap sedang digerakkan

    private final Listener listener;
    private float threshold = 3.0f;
    private boolean hasLast;
    private float lx, ly, lz;
    private float noise;
    private boolean waitingSecond;
    private long firstTapNs;
    private long lastFireNs;

    KnockDetector(Listener listener) {
        this.listener = listener;
    }

    /** percent 0..100; makin tinggi makin sensitif (ambang 6.0 .. 1.5 m/s^2). */
    void setSensitivity(int percent) {
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;
        threshold = 6.0f - 4.5f * percent / 100f;
    }

    void reset() {
        hasLast = false;
        noise = 0f;
        waitingSecond = false;
    }

    void onSample(float x, float y, float z, long tNs) {
        if (!hasLast) {
            lx = x;
            ly = y;
            lz = z;
            hasLast = true;
            return;
        }
        float dx = x - lx;
        float dy = y - ly;
        float dz = z - lz;
        lx = x;
        ly = y;
        lz = z;

        float jerk = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float quiet = noise;                       // tingkat gerakan SEBELUM sampel ini
        // Getaran sisa dari ketukan pertama tidak dihitung sebagai "HP sedang digerakkan",
        // kalau tidak ketukan keras akan menolak ketukan kedua.
        boolean ringing = waitingSecond && (tNs - firstTapNs) < RING_NS;
        if (!ringing) {
            noise = noise * 0.95f + jerk * 0.05f;
        }

        if (jerk < threshold || quiet > QUIET_MAX) return;
        if (tNs - lastFireNs < COOLDOWN_NS) return;

        if (waitingSecond) {
            long gap = tNs - firstTapNs;
            if (gap < MIN_GAP_NS) return;          // masih getaran dari ketukan pertama
            if (gap <= MAX_GAP_NS) {
                waitingSecond = false;
                lastFireNs = tNs;
                listener.onTap(jerk);
                listener.onDoubleKnock();
                return;
            }
        }
        waitingSecond = true;
        firstTapNs = tNs;
        listener.onTap(jerk);
    }
}
