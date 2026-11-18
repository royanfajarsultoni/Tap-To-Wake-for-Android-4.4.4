package com.royan.taptowake;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Gaya visual "cyberpunk minimalis": palet, grid, panel bersudut potong, tombol. */
final class UiKit {

    static final int BG = 0xFF070B10;
    static final int PANEL = 0xFF0D141C;
    static final int CYAN = 0xFF00E5FF;
    static final int MAGENTA = 0xFFFF2E88;
    static final int TEXT = 0xFFD6F4FF;
    static final int DIM = 0xFF6F8C99;
    static final int LINE = 0x5500E5FF;

    private UiKit() {
    }

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    // ------------------------------------------------------------ drawable

    /** Latar gelap dengan garis grid tipis dan cahaya cyan samar di bagian atas. */
    static final class GridBackground extends Drawable {
        private final Paint fill = new Paint();
        private final Paint line = new Paint();
        private final Paint glow = new Paint();
        private final float cell;
        private final float glowHeight;

        GridBackground(Context c) {
            cell = dp(c, 28);
            glowHeight = dp(c, 220);
            fill.setColor(BG);
            line.setColor(0x1500E5FF);
            line.setStrokeWidth(1f);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            canvas.drawRect(b, fill);
            for (float x = b.left; x <= b.right; x += cell) {
                canvas.drawLine(x, b.top, x, b.bottom, line);
            }
            for (float y = b.top; y <= b.bottom; y += cell) {
                canvas.drawLine(b.left, y, b.right, y, line);
            }
            glow.setShader(new LinearGradient(0, b.top, 0, b.top + glowHeight,
                    0x3300E5FF, 0x0000E5FF, Shader.TileMode.CLAMP));
            canvas.drawRect(b.left, b.top, b.right, b.top + glowHeight, glow);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    /** Persegi dengan sudut kiri-atas dan kanan-bawah dipotong miring. */
    static final class Chamfer extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float cut;

        Chamfer(int fillColor, int strokeColor, float strokePx, float cutPx) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(fillColor);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setColor(strokeColor);
            stroke.setStrokeWidth(strokePx);
            cut = cutPx;
        }

        @Override
        protected void onBoundsChange(Rect r) {
            float h = stroke.getStrokeWidth() / 2f;
            float l = r.left + h;
            float t = r.top + h;
            float rr = r.right - h;
            float b = r.bottom - h;
            path.reset();
            path.moveTo(l + cut, t);
            path.lineTo(rr, t);
            path.lineTo(rr, b - cut);
            path.lineTo(rr - cut, b);
            path.lineTo(l, b);
            path.lineTo(l, t + cut);
            path.close();
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawPath(path, fill);
            canvas.drawPath(path, stroke);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // ------------------------------------------------------------- widget

    @SuppressWarnings("deprecation")
    static void setBg(View v, Drawable d) {
        v.setBackgroundDrawable(d);
    }

    static TextView label(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    static LinearLayout.LayoutParams matchWrap(Context c, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, topDp);
        return p;
    }

    static View divider(Context c, int color) {
        View v = new View(c);
        v.setBackgroundColor(color);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 1));
        p.topMargin = dp(c, 6);
        p.bottomMargin = dp(c, 6);
        v.setLayoutParams(p);
        return v;
    }

    /** Kartu bertajuk, mis. "> WAKE_TRIGGER". */
    static LinearLayout card(Context c, String title) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        setBg(l, new Chamfer(PANEL, LINE, dp(c, 1), dp(c, 12)));
        l.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 14));
        l.setLayoutParams(matchWrap(c, 14));
        l.addView(label(c, "> " + title, 13f, CYAN, true));
        l.addView(divider(c, LINE));
        return l;
    }

    /** Kotak "layar terminal" kecil di dalam kartu. */
    static LinearLayout inset(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        setBg(l, new Chamfer(0xFF05090D, 0x3300E5FF, dp(c, 1), dp(c, 8)));
        l.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        l.setLayoutParams(matchWrap(c, 10));
        return l;
    }

    static TextView button(Context c, String text, final int accent, View.OnClickListener click) {
        TextView t = label(c, text, 13f, accent, true);
        t.setGravity(Gravity.CENTER);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed},
                new Chamfer((accent & 0x00FFFFFF) | 0x55000000, accent, dp(c, 1), dp(c, 8)));
        sl.addState(new int[]{}, new Chamfer(0xFF0A1118, accent, dp(c, 1), dp(c, 8)));
        setBg(t, sl);
        t.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        t.setLayoutParams(matchWrap(c, 10));
        t.setClickable(true);
        t.setOnClickListener(click);
        return t;
    }

    static void styleSeek(Context c, SeekBar s) {
        GradientDrawable track = new GradientDrawable();
        track.setColor(0xFF16232D);
        track.setStroke(dp(c, 1), LINE);
        track.setSize(1, dp(c, 6));

        GradientDrawable fillBar = new GradientDrawable();
        fillBar.setColor(CYAN);
        fillBar.setSize(1, dp(c, 6));
        ClipDrawable clip = new ClipDrawable(fillBar, Gravity.LEFT, ClipDrawable.HORIZONTAL);

        LayerDrawable layers = new LayerDrawable(new Drawable[]{track, clip});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        s.setProgressDrawable(layers);

        GradientDrawable thumb = new GradientDrawable();
        thumb.setColor(MAGENTA);
        thumb.setSize(dp(c, 10), dp(c, 22));
        s.setThumb(thumb);
        s.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
    }
}
