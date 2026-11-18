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
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.Toast;

/**
 * Layanan foreground:
 *  - Saat layar MATI: menahan CPU tetap hidup (partial wake lock) dan mendengarkan
 *    accelerometer (ketuk 2x) / proximity (lambai 2x), lalu menyalakan layar.
 *  - Saat layar HIDUP: semua sensor dilepas supaya tidak boros baterai.
 *  - Selama layar mati, watchdog menjaga agar wake lock dan aliran sensor tidak "mati diam-diam".
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
    private static final long WATCHDOG_MS = 1000L;       // pemeriksaan kesehatan tiap 1 dtk
    private static final long STALL_MS = 2000L;          // tanpa data accelerometer selama ini = macet
    private static final long REARM_MS = 150L;           // sampel pertama setelah daftar ulang dibuang
    private static final long RETRY_MS = 5000L;          // jeda coba lagi bila aliran belum hidup

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
    private long lastAccelMs;
    private long lastRegisterMs;
    private long aliveSpanMs;   // berapa lama aliran data biasanya hidup sebelum macet
    private boolean pocketLogged;
    private boolean stallHandled;

    private final Handler handler = new Handler();
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (!listening) return;
            checkHealth();
            handler.postDelayed(this, WATCHDOG_MS);
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                Diag.log("SCREEN off");
                startListening();
            } else if (Intent.ACTION_SCREEN_ON.equals(a)) {
                Diag.log("SCREEN on");
                stopListening();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Diag.log("SERVICE create");
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
        Diag.log("TASK removed (swipe dari Recent Apps)");
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
        Diag.log("SERVICE destroy");
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

    private boolean wantAccel() {
        return prefs.knock() && accel != null;
    }

    private boolean wantProx() {
        return (prefs.wave() || prefs.pocketGuard()) && prox != null;
    }

    private void registerSensors() {
        if (wantProx()) sm.registerListener(this, prox, SensorManager.SENSOR_DELAY_NORMAL);
        if (wantAccel()) sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
    }

    private void startListening() {
        if (listening || !prefs.enabled()) return;
        if (!wantAccel() && !wantProx()) return;

        listening = true;
        Diag.listening = true;
        long now = SystemClock.elapsedRealtime();
        lastRegisterMs = now;
        lastAccelMs = now;
        pocketLogged = false;
        stallHandled = false;
        if (!cpuLock.isHeld()) cpuLock.acquire();
        knock.reset();
        wave.reset();
        ignoreUntil = now + WARMUP_MS;
        registerSensors();
        handler.postDelayed(watchdog, WATCHDOG_MS);
        Diag.log("LISTEN start accel=" + wantAccel() + " prox=" + wantProx());
    }

    private void stopListening() {
        if (!listening) return;
        listening = false;
        Diag.listening = false;
        handler.removeCallbacks(watchdog);
        sm.unregisterListener(this);
        if (cpuLock.isHeld()) cpuLock.release();
        Diag.log("LISTEN stop");
    }

    /** Memastikan wake lock tetap dipegang dan aliran data accelerometer tetap hidup. */
    private void checkHealth() {
        long now = SystemClock.elapsedRealtime();
        if (!cpuLock.isHeld()) {
            Diag.log("LOCK hilang -> acquire ulang");
            Diag.lockLost++;
            cpuLock.acquire();
        }
        if (!wantAccel()) return;

        long sinceReg = now - lastRegisterMs;
        boolean silent = now - lastAccelMs > STALL_MS;
        if (silent && (!stallHandled || sinceReg >= RETRY_MS)) {
            // Aliran data berhenti padahal CPU hidup. Catat berapa lama ia hidup sejak
            // didaftarkan, lalu hidupkan lagi dengan mendaftarkan ulang sensor.
            long alive = lastAccelMs - lastRegisterMs;
            if (!stallHandled && alive > 1000L) aliveSpanMs = alive;
            Diag.aliveMs = aliveSpanMs;
            Diag.log("ACCEL macet " + (now - lastAccelMs) + "ms (hidup " + alive
                    + "ms) -> daftar ulang");
            Diag.restarts++;
            stallHandled = true;
            reregisterSensors();
        } else if (!silent && aliveSpanMs > 0 && sinceReg >= aliveSpanMs * 6 / 10) {
            // Aliran biasanya mati setelah ~aliveSpanMs: daftar ulang lebih dulu, sebelum mati.
            Diag.periodic++;
            reregisterSensors();
        }
    }

    private void reregisterSensors() {
        sm.unregisterListener(this);
        knock.reset();
        wave.reset();
        lastRegisterMs = SystemClock.elapsedRealtime();
        // Sampel pertama setelah pendaftaran ulang nilainya palsu (terlihat sebagai "ketukan"
        // berkekuatan ~21 di log), jadi dibuang.
        ignoreUntil = lastRegisterMs + REARM_MS;
        registerSensors();
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        int type = e.sensor.getType();
        if (type == Sensor.TYPE_ACCELEROMETER) {
            long now = SystemClock.elapsedRealtime();
            long gap = now - lastAccelMs;
            if (gap > 2000L) {
                Diag.log("ACCEL lanjut setelah diam " + gap + "ms");
            }
            stallHandled = false;
            lastAccelMs = now;
            Diag.lastAccelMs = now;
            Diag.accelCount++;
            if (now < ignoreUntil) {
                knock.reset();
                return;
            }
            if (prefs.pocketGuard() && wave.isNear()) {
                Diag.pocketBlocked++;
                if (!pocketLogged) {
                    pocketLogged = true;
                    Diag.log("POCKET guard memblokir ketukan (sensor jarak = dekat)");
                }
                knock.reset();
                return;
            }
            knock.onSample(e.values[0], e.values[1], e.values[2], e.timestamp);
        } else if (type == Sensor.TYPE_PROXIMITY) {
            Diag.log("PROX " + e.values[0]);
            wave.onValue(e.values[0], e.sensor.getMaximumRange(), SystemClock.elapsedRealtime());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onTap(float jerk) {
        Diag.log("TAP kekuatan " + String.format("%.1f", jerk));
    }

    @Override
    public void onDoubleKnock() {
        if (prefs.knock()) {
            Diag.log("WAKE oleh ketukan");
            wakeScreen();
        }
    }

    @Override
    public void onDoubleWave() {
        if (prefs.wave()) {
            Diag.log("WAKE oleh lambaian");
            wakeScreen();
        }
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
                .setSmallIcon(R.drawable.ic_stat_wake)
                .setContentTitle("TAP//WAKE aktif")
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
