package com.royan.taptowake;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Baris pengaturan dengan indikator [ ON ] / [ OFF ]; seluruh baris bisa diketuk. */
final class ToggleRow extends LinearLayout {

    interface OnChange {
        void onChange(boolean on);
    }

    private final TextView state;
    private boolean on;

    ToggleRow(Context c, String label, boolean initial, final OnChange callback) {
        super(c);
        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(0, UiKit.dp(c, 9), 0, UiKit.dp(c, 9));

        TextView name = UiKit.label(c, label, 13f, UiKit.TEXT, false);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        state = UiKit.label(c, "", 13f, UiKit.DIM, true);
        state.setPadding(UiKit.dp(c, 10), 0, 0, 0);
        addView(name);
        addView(state);

        on = initial;
        render();
        setClickable(true);
        setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                on = !on;
                render();
                callback.onChange(on);
            }
        });
    }

    private void render() {
        state.setText(on ? "[ ON ]" : "[ OFF ]");
        state.setTextColor(on ? UiKit.CYAN : UiKit.DIM);
    }
}
