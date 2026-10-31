package site.liweike.watchai;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AboutActivity extends BaseActivity {
    @Override protected void build() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);

        IconView logo = new IconView(this, IconView.SPARKLE, Ui.A1);
        c.addView(logo, new LinearLayout.LayoutParams(Ui.px(84), Ui.px(84)));
        c.addView(space(18));
        TextView name = Ui.text(this, "LI Agent", 42, Ui.TEXT, Ui.SERIF);
        name.setGravity(Gravity.CENTER);
        c.addView(name, new LinearLayout.LayoutParams(-1, -2));
        c.addView(space(12));
        TextView by = Ui.text(this, "本软件由 liweike 制作", 22, Ui.SUB, Ui.SANS);
        by.setGravity(Gravity.CENTER);
        c.addView(by, new LinearLayout.LayoutParams(-1, -2));
        c.addView(space(8));
        TextView ver = Ui.text(this, "v0.3", 18, 0xFF6F6D67, Ui.SANS);
        ver.setGravity(Gravity.CENTER);
        c.addView(ver, new LinearLayout.LayoutParams(-1, -2));

        stage.addView(c, new android.widget.FrameLayout.LayoutParams(-1, -1));
        Ui.rise(c, 0);
    }

    private View space(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.px(h)));
        return v;
    }
}