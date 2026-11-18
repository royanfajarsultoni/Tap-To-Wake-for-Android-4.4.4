package com.royan.taptowake;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Tombol melayang yang bisa digeser. Ketuk untuk membuka panel: Vol+, Vol-, Kunci, Tutup.
 * Pengganti tombol fisik Volume dan Power (sisi "matikan layar").
 */
final class OverlayController {

    interface Actions {
        void onLock();

        void onVolumeUp();

        void onVolumeDown();
    }

    private final Context ctx;
    private final Prefs prefs;
    private final Actions actions;
    private final WindowManager wm;
    private final float density;

    private LinearLayout root;
    private LinearLayout panel;
    private WindowManager.LayoutParams lp;

    OverlayController(Context ctx, Prefs prefs, Actions actions) {
        this.ctx = ctx;
        this.prefs = prefs;
        this.actions = actions;
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        this.density = ctx.getResources().getDisplayMetrics().density;
    }

    private int dp(int v) {
        return (int) (v * density + 0.5f);
    }

    @SuppressWarnings("deprecation")
    private TextView button(String text, int accentArgb) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextColor(accentArgb);
        t.setTextSize(12f);
        t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xE6070B10);
        g.setStroke(dp(2), accentArgb);
        t.setBackgroundDrawable(g);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(46), dp(46));
        p.leftMargin = dp(4);
        t.setLayoutParams(p);
        return t;
    }

    void show() {
        if (root != null) return;

        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setAlpha(0.85f);

        TextView bubble = button("///", UiKit.CYAN);
        root.addView(bubble);

        panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setVisibility(View.GONE);

        TextView volUp = button("V+", UiKit.CYAN);
        volUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actions.onVolumeUp();
            }
        });
        TextView volDown = button("V-", UiKit.CYAN);
        volDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actions.onVolumeDown();
            }
        });
        TextView lock = button("OFF", UiKit.MAGENTA);
        lock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                panel.setVisibility(View.GONE);
                actions.onLock();
            }
        });
        panel.addView(volUp);
        panel.addView(volDown);
        panel.addView(lock);
        root.addView(panel);

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = prefs.getInt(Prefs.K_BX, 0);
        lp.y = prefs.getInt(Prefs.K_BY, dp(240));

        final int slop = dp(8);
        bubble.setOnTouchListener(new View.OnTouchListener() {
            private int startX;
            private int startY;
            private float downX;
            private float downY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = lp.x;
                        startY = lp.y;
                        downX = e.getRawX();
                        downY = e.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (e.getRawX() - downX);
                        int dy = (int) (e.getRawY() - downY);
                        if (Math.abs(dx) > slop || Math.abs(dy) > slop) moved = true;
                        if (moved) {
                            lp.x = startX + dx;
                            lp.y = startY + dy;
                            update();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            prefs.setInt(Prefs.K_BX, lp.x);
                            prefs.setInt(Prefs.K_BY, lp.y);
                        } else {
                            panel.setVisibility(panel.getVisibility() == View.VISIBLE
                                    ? View.GONE : View.VISIBLE);
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });

        try {
            wm.addView(root, lp);
        } catch (Exception ex) {
            // Izin "jendela melayang" ditolak (mis. oleh MIUI): jalan terus tanpa tombol melayang.
            root = null;
            panel = null;
        }
    }

    private void update() {
        try {
            wm.updateViewLayout(root, lp);
        } catch (Exception ignored) {
        }
    }

    void hide() {
        if (root == null) return;
        try {
            wm.removeView(root);
        } catch (Exception ignored) {
        }
        root = null;
        panel = null;
    }
}
