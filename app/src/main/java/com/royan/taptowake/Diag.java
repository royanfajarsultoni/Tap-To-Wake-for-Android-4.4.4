package com.royan.taptowake;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/** Catatan diagnosis di memori (ditampilkan di kartu DIAG_LOG pada layar utama). */
final class Diag {
    private static final int MAX_LINES = 80;
    private static final ArrayDeque<String> LINES = new ArrayDeque<String>();

    static volatile boolean listening;
    static volatile long accelCount;
    static volatile long lastAccelMs;   // SystemClock.elapsedRealtime() saat sampel terakhir
    static volatile long restarts;
    static volatile long lockRefreshes;
    static volatile long lockLost;
    static volatile long pocketBlocked;

    private Diag() {
    }

    static synchronized void log(String msg) {
        String t = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        LINES.addLast(t + " " + msg);
        while (LINES.size() > MAX_LINES) {
            LINES.removeFirst();
        }
    }

    static synchronized String tail(int n) {
        String[] all = LINES.toArray(new String[0]);
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, all.length - n); i < all.length; i++) {
            sb.append(all[i]).append('\n');
        }
        return sb.toString();
    }
}
