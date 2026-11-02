package site.liweike.watchai;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/* JADX INFO: loaded from: classes.dex */
public class UsageActivity extends BaseActivity {
    private static final float DEMO = 0.38f;

    @Override // site.liweike.watchai.BaseActivity
    protected void build() {
        this.stage.addView(Ui.text(this, "用量信息", 25.0f, -658195, "sans-serif-medium"), Ui.fl(-2.0f, -2.0f, 49, 0.0f, 38.0f));
        FrameLayout frameLayout = new FrameLayout(this);
        final RingView ringView = new RingView(this);
        frameLayout.addView(ringView, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(17);
        final TextView textViewText = Ui.text(this, "0%", 62.0f, -658195, "sans-serif-light");
        textViewText.setGravity(17);
        linearLayout.addView(textViewText);
        linearLayout.addView(new View(this), new LinearLayout.LayoutParams(1, Ui.px(6.0f)));
        linearLayout.addView(Ui.text(this, "本月额度", 21.0f, -6514030, "sans-serif"));
        frameLayout.addView(linearLayout, new FrameLayout.LayoutParams(-1, -1));
        this.stage.addView(frameLayout, Ui.fl(228.0f, 228.0f, 49, 0.0f, 84.0f));
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(17);
        linearLayout2.addView(stat("3.8万", "已用"));
        View view = new View(this);
        view.setBackgroundColor(-12961739);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Math.max(1, Ui.px(1.0f)), Ui.px(36.0f));
        layoutParams.leftMargin = Ui.px(26.0f);
        layoutParams.rightMargin = Ui.px(26.0f);
        linearLayout2.addView(view, layoutParams);
        linearLayout2.addView(stat("6.2万", "剩余"));
        this.stage.addView(linearLayout2, Ui.fl(-2.0f, -2.0f, 49, 0.0f, 326.0f));
        this.stage.addView(Ui.text(this, "示例数据 · 11月1日重置", 18.0f, -6514030, "sans-serif"), Ui.fl(-2.0f, -2.0f, 49, 0.0f, 394.0f));
        ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(0.0f, DEMO);
        valueAnimatorOfFloat.setDuration(900L);
        valueAnimatorOfFloat.setStartDelay(150L);
        valueAnimatorOfFloat.setInterpolator(new DecelerateInterpolator());
        valueAnimatorOfFloat.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: site.liweike.watchai.UsageActivity.1
            @Override // android.animation.ValueAnimator.AnimatorUpdateListener
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fFloatValue = ((Float) valueAnimator.getAnimatedValue()).floatValue();
                ringView.setProgress(fFloatValue);
                textViewText.setText(Math.round(fFloatValue * 100.0f) + "%");
            }
        });
        valueAnimatorOfFloat.start();
    }

    private View stat(String str, String str2) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(1);
        linearLayout.addView(Ui.text(this, str, 27.0f, -658195, "sans-serif-medium"));
        linearLayout.addView(new View(this), new LinearLayout.LayoutParams(1, Ui.px(4.0f)));
        linearLayout.addView(Ui.text(this, str2, 18.0f, -6514030, "sans-serif"));
        return linearLayout;
    }
}
