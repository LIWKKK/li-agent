package site.liweike.watchai;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/* JADX INFO: loaded from: classes.dex */
final class Ui {
    static final int A1 = -2525353;
    static final int A2 = -1863569;
    static final int BG = -14737891;
    static final float C = 233.0f;
    static final int CARD = -13948376;
    static final int CHIP = -13093325;
    static final int INK = -14737891;
    static final String SANS = "sans-serif";
    static final String SANS_M = "sans-serif-medium";
    static final String SERIF = "serif";
    static final int STROKE = -12961739;
    static final int SUB = -6514030;
    static final int TEXT = -658195;
    static float u = 1.0f;

    Ui() {
    }

    static void init(Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        u = Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels) / 466.0f;
    }

    static int px(float f) {
        return Math.round(u * f);
    }

    static int dim(float f) {
        return f < 0.0f ? (int) f : px(f);
    }

    static float chordHalf(float f, float f2, float f3) {
        float f4 = C - f3;
        float fMax = Math.max(Math.abs(f - C), Math.abs(f2 - C));
        float f5 = (f4 * f4) - (fMax * fMax);
        if (f5 > 0.0f) {
            return (float) Math.sqrt(f5);
        }
        return 0.0f;
    }

    static GradientDrawable card(float f) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(CARD);
        gradientDrawable.setCornerRadius(px(f));
        gradientDrawable.setStroke(Math.max(1, px(1.0f)), STROKE);
        return gradientDrawable;
    }

    static GradientDrawable accent(float f) {
        GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{A1, A2});
        gradientDrawable.setCornerRadius(px(f));
        return gradientDrawable;
    }

    static GradientDrawable circle(int i) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(1);
        gradientDrawable.setColor(i);
        return gradientDrawable;
    }

    static GradientDrawable accentCircle() {
        GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{A1, A2});
        gradientDrawable.setShape(1);
        return gradientDrawable;
    }

    static TextView text(Context context, String str, float f, int i, String str2) {
        TextView textView = new TextView(context);
        textView.setText(str);
        textView.setTextSize(0, u * f);
        textView.setTextColor(i);
        textView.setTypeface(Typeface.create(str2, 0));
        textView.setIncludeFontPadding(true);
        textView.setPadding(0, px(3.0f), 0, px(3.0f));
        return textView;
    }

    static FrameLayout.LayoutParams fl(float f, float f2, int i, float f3, float f4) {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(dim(f), dim(f2), i);
        layoutParams.leftMargin = px(f3);
        layoutParams.topMargin = px(f4);
        return layoutParams;
    }

    static void pressable(View view) {
        view.setOnTouchListener(new View.OnTouchListener() { // from class: site.liweike.watchai.Ui.1
            @Override // android.view.View.OnTouchListener
            public boolean onTouch(View view2, MotionEvent motionEvent) {
                int actionMasked = motionEvent.getActionMasked();
                if (actionMasked == 0) {
                    view2.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90L).start();
                    return false;
                }
                if (actionMasked == 1 || actionMasked == 3) {
                    view2.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140L).start();
                    return false;
                }
                return false;
            }
        });
    }

    static void rise(View view, int i) {
        view.setAlpha(0.0f);
        view.setTranslationY(px(14.0f));
        view.animate().alpha(1.0f).translationY(0.0f).setStartDelay(i).setDuration(300L).start();
    }
}
