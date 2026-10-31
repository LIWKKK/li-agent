package site.liweike.watchai;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Design space is 466x466 (round watch face). All sizes below are in that space. */
final class Ui {
    static final int BG = 0xFF1F1E1D, CARD = 0xFF2B2A28, STROKE = 0xFF3A3835, CHIP = 0xFF383633;
    static final int TEXT = 0xFFF5F4ED, SUB = 0xFF9C9A92, INK = 0xFF1F1E1D;
    static final int A1 = 0xFFD97757, A2 = 0xFFE3906F;
    static final String SERIF = "serif", SANS = "sans-serif", SANS_M = "sans-serif-medium";
    static final float C = 233f;
    static float u = 1f;

    static void init(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        u = Math.min(m.widthPixels, m.heightPixels) / 466f;
    }

    static int px(float v) { return Math.round(v * u); }

    static int dim(float v) { return v < 0 ? (int) v : px(v); }

    /** Half-width of the horizontal chord of the circle that fits a band between top and bottom. */
    static float chordHalf(float top, float bottom, float inset) {
        float r = C - inset;
        float dy = Math.max(Math.abs(top - C), Math.abs(bottom - C));
        float v = r * r - dy * dy;
        return v > 0 ? (float) Math.sqrt(v) : 0f;
    }

    static GradientDrawable card(float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(px(r));
        g.setStroke(Math.max(1, px(1)), STROKE);
        return g;
    }

    static GradientDrawable accent(float r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{A1, A2});
        g.setCornerRadius(px(r));
        return g;
    }

    static GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    static GradientDrawable accentCircle() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{A1, A2});
        g.setShape(GradientDrawable.OVAL);
        return g;
    }

    static TextView text(Context c, String s, float size, int color, String family) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        // Keep font padding on and add a little vertical room: CJK glyphs are taller than the Latin
        // primary font, and TextView clips drawing to its own bounds, which cut the top/bottom of Chinese text.
        t.setIncludeFontPadding(true);
        t.setPadding(0, px(3), 0, px(3));
        return t;
    }

    static FrameLayout.LayoutParams fl(float w, float h, int gravity, float left, float top) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dim(w), dim(h), gravity);
        lp.leftMargin = px(left);
        lp.topMargin = px(top);
        return lp;
    }

    static void pressable(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View x, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    x.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    x.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                }
                return false;
            }
        });
    }

    static void rise(View v, int delay) {
        v.setAlpha(0f);
        v.setTranslationY(px(14));
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(300).start();
    }
}
