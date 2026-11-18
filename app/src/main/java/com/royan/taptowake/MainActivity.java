package com.royan.taptowake;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Layar pengaturan sederhana. Seluruh UI dibuat lewat kode (tanpa file layout). */
public class MainActivity extends Activity implements SensorEventListener, KnockDetector.Listener {

    private Prefs prefs;
    private SensorManager sm;
    private Sensor accel;
    private KnockDetector testDetector;

    private LinearLayout content;
    private TextView sensLabel;
    private TextView testView;
    private TextView adminView;
    private int taps;
    private int doubles;
    private float lastJerk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        testDetector = new KnockDetector(this);
        testDetector.setSensitivity(prefs.sensitivity());
        buildUi();
        if (prefs.enabled()) startService(new Intent(this, WakeService.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAdminStatus();
        testDetector.reset();
        if (accel != null) sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        sm.unregisterListener(this);
        super.onPause();
    }

    // ------------------------------------------------------------------- UI

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String s, float sizeSp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sizeSp);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private CheckBox check(String label, boolean value, final String key) {
        CheckBox c = new CheckBox(this);
        c.setText(label);
        c.setChecked(value);
        c.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean on) {
                prefs.setBool(key, on);
                notifyService();
            }
        });
        content.addView(c);
        return c;
    }

    private void notifyService() {
        if (prefs.enabled()) {
            startService(new Intent(this, WakeService.class).setAction(WakeService.ACTION_RELOAD));
        }
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(24));
        sv.addView(content);

        content.addView(text("Tap to Wake", 22f, true));
        content.addView(text("Pengganti tombol Power & Volume yang rusak.", 14f, false));

        CheckBox enabled = new CheckBox(this);
        enabled.setText("Layanan aktif (wajib agar semua fitur jalan)");
        enabled.setChecked(prefs.enabled());
        enabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean on) {
                prefs.setBool(Prefs.K_ENABLED, on);
                Intent svc = new Intent(MainActivity.this, WakeService.class);
                if (on) startService(svc);
                else stopService(svc);
            }
        });
        content.addView(enabled);

        content.addView(text("Menyalakan layar", 16f, true));
        check("Ketuk 2x pada HP (accelerometer)", prefs.knock(), Prefs.K_KNOCK);
        check("Lambai 2x di atas sensor jarak (paling andal)", prefs.wave(), Prefs.K_WAVE);
        check("Abaikan ketukan bila sensor jarak tertutup (anti nyala di saku)",
                prefs.pocketGuard(), Prefs.K_POCKET);

        sensLabel = text("", 14f, false);
        content.addView(sensLabel);
        SeekBar sens = new SeekBar(this);
        sens.setMax(100);
        sens.setProgress(prefs.sensitivity());
        updateSensLabel(prefs.sensitivity());
        sens.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateSensLabel(progress);
                testDetector.setSensitivity(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                prefs.setInt(Prefs.K_SENS, bar.getProgress());
                notifyService();
            }
        });
        content.addView(sens);

        testView = text("", 14f, false);
        content.addView(text("Uji ketukan (layar harus menyala, ketuk 2x pada HP):", 14f, true));
        content.addView(testView);
        updateTestView();

        content.addView(text("Pengganti tombol", 16f, true));
        check("Tampilkan tombol melayang (V+, V-, OFF)", prefs.bubble(), Prefs.K_BUBBLE);
        content.addView(text("Tombol OFF mengunci layar dan butuh Device Admin. "
                + "Aksi Kunci / Vol+ / Vol- juga ada di notifikasi.", 13f, false));

        adminView = text("", 14f, false);
        content.addView(adminView);
        Button adminOn = new Button(this);
        adminOn.setText("Aktifkan Device Admin");
        adminOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                        new ComponentName(MainActivity.this, AdminReceiver.class));
                i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Hanya dipakai untuk mengunci layar (pengganti tombol Power).");
                startActivity(i);
            }
        });
        content.addView(adminOn);
        Button adminOff = new Button(this);
        adminOff.setText("Nonaktifkan Device Admin (sebelum uninstall)");
        adminOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DevicePolicyManager dpm =
                        (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
                dpm.removeActiveAdmin(new ComponentName(MainActivity.this, AdminReceiver.class));
                refreshAdminStatus();
            }
        });
        content.addView(adminOff);

        content.addView(text("Agar tidak dimatikan sistem (MIUI)", 16f, true));
        content.addView(text(
                "1. Keamanan > Izin > Autostart: izinkan Tap to Wake.\n"
                        + "2. Buka daftar Recent Apps, kunci kartu Tap to Wake (tarik / tekan lama kartu).\n"
                        + "3. Kecualikan Tap to Wake dari penghemat baterai.\n"
                        + "4. Jika tombol melayang tidak muncul, izinkan \"jendela melayang\" untuk aplikasi ini.",
                13f, false));

        setContentView(sv);
    }

    private void updateSensLabel(int p) {
        sensLabel.setText("Sensitivitas ketukan: " + p + "%  (naikkan bila ketukan tidak terdeteksi)");
    }

    private void updateTestView() {
        testView.setText("Ketukan terdeteksi: " + taps
                + "  |  Ketuk ganda: " + doubles
                + "  |  Kekuatan terakhir: " + String.format("%.1f", lastJerk));
    }

    private void refreshAdminStatus() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        boolean on = dpm.isAdminActive(new ComponentName(this, AdminReceiver.class));
        adminView.setText(on ? "Device Admin: AKTIF (tombol OFF siap dipakai)"
                : "Device Admin: belum aktif (tombol OFF belum berfungsi)");
    }

    // ------------------------------------------------------- sensor uji

    @Override
    public void onSensorChanged(SensorEvent e) {
        testDetector.onSample(e.values[0], e.values[1], e.values[2], e.timestamp);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onTap(float jerk) {
        taps++;
        lastJerk = jerk;
        updateTestView();
    }

    @Override
    public void onDoubleKnock() {
        doubles++;
        updateTestView();
    }
}
