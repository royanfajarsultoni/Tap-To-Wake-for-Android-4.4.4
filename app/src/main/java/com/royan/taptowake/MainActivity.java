package com.royan.taptowake;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Layar pengaturan bertema cyberpunk. Seluruh UI dibuat lewat kode (tanpa file layout). */
public class MainActivity extends Activity implements SensorEventListener, KnockDetector.Listener {

    private Prefs prefs;
    private SensorManager sm;
    private Sensor accel;
    private KnockDetector testDetector;

    private LinearLayout content;
    private TextView sensLabel;
    private TextView testView;
    private TextView adminView;
    private TextView diagView;
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
        getWindow().setBackgroundDrawable(new ColorDrawable(UiKit.BG));
        buildUi();
        if (prefs.enabled()) startService(new Intent(this, WakeService.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAdminStatus();
        refreshDiag();
        testDetector.reset();
        if (accel != null) sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        sm.unregisterListener(this);
        super.onPause();
    }

    // ------------------------------------------------------------------- UI

    private int dp(float v) {
        return UiKit.dp(this, v);
    }

    private void notifyService() {
        if (prefs.enabled()) {
            startService(new Intent(this, WakeService.class).setAction(WakeService.ACTION_RELOAD));
        }
    }

    private void addToggle(LinearLayout parent, String label, boolean value, final String key) {
        parent.addView(new ToggleRow(this, label, value, new ToggleRow.OnChange() {
            @Override
            public void onChange(boolean on) {
                prefs.setBool(key, on);
                notifyService();
            }
        }));
    }

    private TextView note(String s) {
        TextView t = UiKit.label(this, s, 12f, UiKit.DIM, false);
        t.setPadding(0, dp(4), 0, dp(2));
        return t;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        UiKit.setBg(root, new UiKit.GridBackground(this));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(20), dp(16), dp(28));
        sv.addView(content);
        root.addView(sv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        buildHeader();
        buildServiceCard();
        buildWakeCard();
        buildControlCard();
        buildTipsCard();
        buildDiagCard();

        setContentView(root);
    }

    private void buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);

        TextView title = UiKit.label(this, "TAP//WAKE", 30f, UiKit.CYAN, true);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView ver = UiKit.label(this, "v1.1.2", 12f, UiKit.MAGENTA, true);
        row.addView(title);
        row.addView(ver);
        content.addView(row);

        TextView by = UiKit.label(this, "", 13f, UiKit.TEXT, false);
        by.setText(Html.fromHtml("by <b><font color='#FF2E88'>ROYAN</font></b>"
                + "<font color='#6F8C99'>&nbsp;&nbsp;//&nbsp;&nbsp;power &amp; volume substitute</font>"));
        by.setPadding(0, dp(2), 0, 0);
        content.addView(by);
        content.addView(UiKit.divider(this, UiKit.CYAN));
    }

    private void buildServiceCard() {
        LinearLayout card = UiKit.card(this, "SYSTEM");
        card.addView(new ToggleRow(this, "Layanan aktif", prefs.enabled(),
                new ToggleRow.OnChange() {
                    @Override
                    public void onChange(boolean on) {
                        prefs.setBool(Prefs.K_ENABLED, on);
                        Intent svc = new Intent(MainActivity.this, WakeService.class);
                        if (on) {
                            startService(svc);
                        } else {
                            stopService(svc);
                        }
                    }
                }));
        card.addView(note("Wajib aktif agar semua fitur berjalan."));
        content.addView(card);
    }

    private void buildWakeCard() {
        LinearLayout card = UiKit.card(this, "WAKE_TRIGGER");
        addToggle(card, "Ketuk 2x pada HP", prefs.knock(), Prefs.K_KNOCK);
        addToggle(card, "Lambai 2x di sensor jarak", prefs.wave(), Prefs.K_WAVE);
        addToggle(card, "Anti nyala di saku", prefs.pocketGuard(), Prefs.K_POCKET);
        card.addView(note("Anti nyala: ketukan diabaikan saat sensor jarak tertutup."));

        sensLabel = UiKit.label(this, "", 13f, UiKit.TEXT, true);
        sensLabel.setPadding(0, dp(12), 0, 0);
        card.addView(sensLabel);
        SeekBar sens = new SeekBar(this);
        sens.setMax(100);
        sens.setProgress(prefs.sensitivity());
        UiKit.styleSeek(this, sens);
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
        card.addView(sens);

        LinearLayout box = UiKit.inset(this);
        box.addView(UiKit.label(this, "SENSOR_TEST", 11f, UiKit.DIM, true));
        testView = UiKit.label(this, "", 14f, UiKit.CYAN, true);
        testView.setPadding(0, dp(4), 0, dp(2));
        box.addView(testView);
        box.addView(note("Layar menyala, ketuk 2x pada HP. Naikkan sensitivitas bila TAP tetap 0."));
        card.addView(box);
        updateTestView();

        content.addView(card);
    }

    private void buildControlCard() {
        LinearLayout card = UiKit.card(this, "CONTROL");
        addToggle(card, "Tombol melayang", prefs.bubble(), Prefs.K_BUBBLE);
        card.addView(note("V+ / V- untuk volume, OFF untuk kunci layar. "
                + "Aksi yang sama ada di notifikasi."));

        adminView = UiKit.label(this, "", 13f, UiKit.TEXT, true);
        adminView.setPadding(0, dp(12), 0, 0);
        card.addView(adminView);

        card.addView(UiKit.button(this, "ADMIN :: AKTIFKAN", UiKit.CYAN,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                new ComponentName(MainActivity.this, AdminReceiver.class));
                        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "Hanya dipakai untuk mengunci layar (pengganti tombol Power).");
                        startActivity(i);
                    }
                }));
        card.addView(UiKit.button(this, "ADMIN :: NONAKTIFKAN (sebelum uninstall)", UiKit.MAGENTA,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        DevicePolicyManager dpm =
                                (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
                        dpm.removeActiveAdmin(
                                new ComponentName(MainActivity.this, AdminReceiver.class));
                        refreshAdminStatus();
                    }
                }));
        content.addView(card);
    }

    private void buildTipsCard() {
        LinearLayout card = UiKit.card(this, "MIUI_KEEP_ALIVE");
        TextView t = UiKit.label(this,
                "01  Keamanan > Izin > Autostart: izinkan Tap to Wake.\n"
                        + "02  Recent Apps: kunci kartu Tap to Wake.\n"
                        + "03  Kecualikan dari penghemat baterai.\n"
                        + "04  Tombol melayang tidak muncul? Izinkan \"jendela melayang\".",
                12f, UiKit.DIM, false);
        t.setLineSpacing(dp(3), 1f);
        card.addView(t);
        content.addView(card);
    }

    private void buildDiagCard() {
        LinearLayout card = UiKit.card(this, "DIAG_LOG");
        diagView = UiKit.label(this, "", 11f, UiKit.DIM, false);
        card.addView(diagView);
        card.addView(UiKit.button(this, "DIAG :: REFRESH", UiKit.CYAN,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        refreshDiag();
                    }
                }));
        card.addView(UiKit.button(this, "DIAG :: SALIN LOG", UiKit.MAGENTA,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                                getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(android.content.ClipData.newPlainText(
                                "taptowake", diagView.getText() + "\n" + Diag.tail(80)));
                        android.widget.Toast.makeText(MainActivity.this,
                                "Log disalin. Tempel ke chat.", android.widget.Toast.LENGTH_SHORT).show();
                    }
                }));
        content.addView(card);
    }

    private void refreshDiag() {
        long now = SystemClock.elapsedRealtime();
        String age = Diag.lastAccelMs == 0 ? "-" : ((now - Diag.lastAccelMs) / 1000) + "s";
        diagView.setText("listening=" + Diag.listening
                + "  accel=" + Diag.accelCount + " (terakhir " + age + " lalu)\n"
                + "restart=" + Diag.restarts
                + "  lockRefresh=" + Diag.lockRefreshes
                + "  lockHilang=" + Diag.lockLost
                + "  blokirSaku=" + Diag.pocketBlocked + "\n"
                + "----\n" + Diag.tail(18));
    }

    private void updateSensLabel(int p) {
        sensLabel.setText("SENSITIVITAS  " + p + "%");
    }

    private void updateTestView() {
        testView.setText("TAP " + taps + "   DBL " + doubles
                + "   PWR " + String.format("%.1f", lastJerk));
    }

    private void refreshAdminStatus() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        boolean on = dpm.isAdminActive(new ComponentName(this, AdminReceiver.class));
        adminView.setText(on ? "ADMIN :: [ AKTIF ]" : "ADMIN :: [ BELUM AKTIF ]");
        adminView.setTextColor(on ? UiKit.CYAN : UiKit.MAGENTA);
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
