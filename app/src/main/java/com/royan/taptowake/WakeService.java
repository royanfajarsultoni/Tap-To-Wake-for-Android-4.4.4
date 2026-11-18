package com.royan.taptowake;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.Toast;

/**
 * Layanan foreground:
 *  - Saat layar MATI: menahan CPU tetap hidup (partial wake lock) dan mendengarkan
 *    accelerometer (ketuk 2x) / proximity (lambai 2x), lalu menyalakan layar.
 *  - Saat layar HIDUP: semua sensor dilepas supaya tidak boros baterai.
 *  - Menyediakan aksi Kunci layar / Volume + / Volume - lewat notifikasi dan tombol melayang.
 */
public class WakeService extends Service implements SensorEventListener,
        KnockDetector.Listener, WaveDetector.Listener, OverlayController.Actions {

    static final String ACTION_LOCK = "com.royan.taptowake.LOCK";
    static final String ACTION_VOL_UP = "com.royan.taptowake.VOL_UP";
    static final String ACTION_VOL_DOWN = "com.royan.taptowake.VOL_DOWN";
    static final String ACTION_RELOAD = "com.royan.taptowake.RELOAD";

    private static final int NOTIF_ID = 1;
    private static final long WARMUP_MS = 700L;

    private Prefs prefs;
    private PowerManager pm;
    private SensorManager sm;
    private Sensor accel;
    private Sensor prox;
    private PowerManager.WakeLock cpuLock;
    private KnockDetector knock;
    private WaveDetector wave;
    private OverlayController overlay;
    private boolean listening;
    private long ignoreUntil;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                startListening();
            } else if (Intent.ACTION_SCREEN_ON.equals(a)) {
                stopListening();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        pm = (PowerManager) getSystemService(POWER_SERVICE);
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        prox = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "taptowake:cpu");
        cpuLock.setReferenceCounted(false);

        knock = new KnockDetector(this);
        wave = new WaveDetector(this);
        overlay = new OverlayController(this, prefs, this);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, f);

        startForeground(NOTIF_ID, buildNotification());
        applyPrefs();
        if (!isScreenOn()) {
            startListening();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String a = intent.getAction();
            if (ACTION_LOCK.equals(a)) {
                onLock();
            } else if (ACTION_VOL_UP.equals(a)) {
                onVolumeUp();
            } else if (ACTION_VOL_DOWN.equals(a)) {
                onVolumeDown();
            } else if (ACTION_RELOAD.equals(a)) {
                applyPrefs();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Dibersihkan dari recent apps -> hidupkan lagi sebentar kemudian.
        Intent restart = new Intent(getApplicationContext(), WakeService.class);
        PendingIntent p = PendingIntent.getService(getApplicationContext(), 99, restart,
                PendingIntent.FLAG_ONE_SHOT);
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000L, p);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        stopListening();
        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception ignored) {
        }
        overlay.hide();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------------------------------------------------------------- sensor

    private void applyPrefs() {
        knock.setSensitivity(prefs.sensitivity());
        if (prefs.bubble()) {
            overlay.show();
        } else {
            overlay.hide();
        }
        if (listening) {
            stopListening();
            startListening();
        }
    }

    private void startListening() {
        if (listening || !prefs.enabled()) return;
        boolean useAccel = prefs.knock() && accel != null;
        boolean useProx = (prefs.wave() || prefs.pocketGuard()) && prox != null;
        if (!useAccel && !useProx) return;

        listening = true;
        if (!cpuLock.isHeld()) cpuLock.acquire();
        knock.reset();
        wave.reset();
        ignoreUntil = SystemClock.elapsedRealtime() + WARMUP_MS;
        if (useProx) sm.registerListener(this, prox, SensorManager.SENSOR_DELAY_NORMAL);
        if (useAccel) sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
    }

    private void stopListening() {
        if (!listening) return;
        listening = false;
        sm.unregisterListener(this);
        if (cpuLock.isHeld()) cpuLock.release();
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        int type = e.sensor.getType();
        if (type == Sensor.TYPE_ACCELEROMETER) {
            if (SystemClock.elapsedRealtime() < ignoreUntil) {
                knock.reset();
                return;
            }
            if (prefs.pocketGuard() && wave.isNear()) {
                knock.reset();
                return;
            }
            knock.onSample(e.values[0], e.values[1], e.values[2], e.timestamp);
        } else if (type == Sensor.TYPE_PROXIMITY) {
            wave.onValue(e.values[0], e.sensor.getMaximumRange(), SystemClock.elapsedRealtime());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onTap(float jerk) {
    }

    @Override
    public void onDoubleKnock() {
        if (prefs.knock()) wakeScreen();
    }

    @Override
    public void onDoubleWave() {
        if (prefs.wave()) wakeScreen();
    }

    // ----------------------------------------------------------------- aksi

    @SuppressWarnings("deprecation")
    private boolean isScreenOn() {
        return pm.isScreenOn();
    }

    @SuppressWarnings("deprecation")
    private void wakeScreen() {
        PowerManager.WakeLock wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP
                        | PowerManager.ON_AFTER_RELEASE,
                "taptowake:wake");
        wl.acquire(3000L);
    }

    @Override
    public void onLock() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, AdminReceiver.class);
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow();
        } else {
            Toast.makeText(this, "Aktifkan Device Admin dulu di aplikasi Tap to Wake",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onVolumeUp() {
        adjustVolume(AudioManager.ADJUST_RAISE);
    }

    @Override
    public void onVolumeDown() {
        adjustVolume(AudioManager.ADJUST_LOWER);
    }

    private void adjustVolume(int direction) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        am.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE,
                AudioManager.FLAG_SHOW_UI);
    }

    // ------------------------------------------------------------ notifikasi

    private PendingIntent action(int code, String action) {
        Intent i = new Intent(this, WakeService.class).setAction(action);
        return PendingIntent.getService(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("Tap to Wake aktif")
                .setContentText("Ketuk 2x atau lambai 2x untuk menyalakan layar")
                .setContentIntent(open)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .addAction(android.R.drawable.ic_lock_power_off, "Kunci", action(1, ACTION_LOCK))
                .addAction(android.R.drawable.ic_input_add, "Vol +", action(2, ACTION_VOL_UP))
                .addAction(android.R.drawable.ic_input_delete, "Vol -", action(3, ACTION_VOL_DOWN));
        return b.build();
    }
}
