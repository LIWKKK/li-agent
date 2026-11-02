package site.liweike.watchai;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/* JADX INFO: loaded from: classes.dex */
public class AboutActivity extends BaseActivity {
    @Override // site.liweike.watchai.BaseActivity
    protected void build() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(17);
        IconView iconView = new IconView(this, 11, -2525353);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Ui.px(76.0f), Ui.px(76.0f));
        layoutParams.gravity = 1;
        linearLayout.addView(iconView, layoutParams);
        linearLayout.addView(space(20));
        linearLayout.addView(line("LI Agent", 42.0f, -658195, "serif"));
        linearLayout.addView(space(14));
        linearLayout.addView(line("本软件由 liweike 制作", 22.0f, -6514030, "sans-serif"));
        linearLayout.addView(space(10));
        linearLayout.addView(line("v0.4", 18.0f, -9474713, "sans-serif"));
        this.stage.addView(linearLayout, new FrameLayout.LayoutParams(-1, -1));
        Ui.rise(linearLayout, 0);
    }

    private TextView line(String str, float f, int i, String str2) {
        TextView textViewText = Ui.text(this, str, f, i, str2);
        textViewText.setGravity(17);
        textViewText.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return textViewText;
    }

    private View space(int i) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.px(i)));
        return view;
    }
}
