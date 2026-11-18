package com.royan.taptowake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Menjalankan layanan otomatis setelah HP menyala. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (new Prefs(context).enabled()) {
            context.startService(new Intent(context, WakeService.class));
        }
    }
}
