package com.royan.taptowake;

import android.content.Context;
import android.content.SharedPreferences;

/** Pengaturan aplikasi (SharedPreferences). */
final class Prefs {
    static final String K_ENABLED = "enabled";
    static final String K_KNOCK = "knock";
    static final String K_WAVE = "wave";
    static final String K_POCKET = "pocket_guard";
    static final String K_BUBBLE = "bubble";
    static final String K_SENS = "sensitivity";
    static final String K_BX = "bubble_x";
    static final String K_BY = "bubble_y";

    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    boolean enabled() { return sp.getBoolean(K_ENABLED, true); }
    boolean knock() { return sp.getBoolean(K_KNOCK, true); }
    boolean wave() { return sp.getBoolean(K_WAVE, true); }
    boolean pocketGuard() { return sp.getBoolean(K_POCKET, true); }
    boolean bubble() { return sp.getBoolean(K_BUBBLE, true); }
    /** 0..100, makin tinggi makin sensitif. */
    int sensitivity() { return sp.getInt(K_SENS, 60); }

    int getInt(String k, int def) { return sp.getInt(k, def); }

    void setBool(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
    void setInt(String k, int v) { sp.edit().putInt(k, v).apply(); }
}
