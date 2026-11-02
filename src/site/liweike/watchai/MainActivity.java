package site.liweike.watchai;

import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/* JADX INFO: loaded from: classes.dex */
public class MainActivity extends BaseActivity {
    @Override // site.liweike.watchai.BaseActivity
    protected boolean swipeBack() {
        return false;
    }

    @Override // site.liweike.watchai.BaseActivity
    protected void build() {
        TextClock textClock = new TextClock(this);
        textClock.setFormat24Hour("HH:mm");
        textClock.setFormat12Hour("h:mm");
        textClock.setTextSize(0, 23.0f * Ui.u);
        textClock.setTextColor(-6514030);
        textClock.setTypeface(Typeface.create("sans-serif-medium", 0));
        textClock.setLetterSpacing(0.1f);
        textClock.setIncludeFontPadding(true);
        textClock.setPadding(0, Ui.px(3.0f), 0, Ui.px(3.0f));
        this.stage.addView(textClock, Ui.fl(-2.0f, -2.0f, 49, 0.0f, 34.0f));
        Ui.rise(textClock, 0);
        addRow("Harness", "工作区 · 对话", 5, false, WorkspacesActivity.class, 86.0f, 72.0f, 380.0f, 1);
        addRow("Chat", "历史 · 新对话", 2, true, ConversationsActivity.class, 172.0f, 72.0f, 380.0f, 2);
        addRow("info", "系统 · 用量", 1, false, InformationActivity.class, 258.0f, 72.0f, 380.0f, 3);
        addRow("about", null, 6, false, AboutActivity.class, 344.0f, 48.0f, 200.0f, 4);
    }

    private void addRow(String str, String str2, int i, boolean z, final Class<?> cls, float f, float f2, float f3, int i2) {
        float fMin = Math.min(Ui.chordHalf(f, f + f2, 6.0f), f3 / 2.0f);
        boolean z2 = str2 == null;
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(z2 ? 17 : 16);
        linearLayout.setPadding(Ui.px(z2 ? 10.0f : 15.0f), 0, Ui.px(14.0f), 0);
        linearLayout.setBackground(z ? Ui.accent(40.0f) : Ui.card(40.0f));
        int i3 = z2 ? 30 : 46;
        IconView iconView = new IconView(this, i, z ? -14737891 : -2525353);
        int i4 = z2 ? 4 : 11;
        iconView.setPadding(Ui.px(i4), Ui.px(i4), Ui.px(i4), Ui.px(i4));
        if (!z2) {
            iconView.setBackground(Ui.circle(z ? 637534208 : -13093325));
        }
        linearLayout.addView(iconView, new LinearLayout.LayoutParams(Ui.px(i3), Ui.px(i3)));
        if (z2) {
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
            layoutParams.leftMargin = Ui.px(8.0f);
            linearLayout.addView(Ui.text(this, str, 22.0f, -6514030, "serif"), layoutParams);
        } else {
            LinearLayout linearLayout2 = new LinearLayout(this);
            linearLayout2.setOrientation(1);
            linearLayout2.setGravity(16);
            TextView textViewText = Ui.text(this, str, 27.0f, z ? -14737891 : -658195, "serif");
            textViewText.setPadding(0, 0, 0, 0);
            linearLayout2.addView(textViewText);
            linearLayout2.addView(new View(this), new LinearLayout.LayoutParams(1, Ui.px(3.0f)));
            linearLayout2.addView(Ui.text(this, str2, 18.0f, z ? -1289806307 : -6514030, "sans-serif"));
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(0, -2, 1.0f);
            layoutParams2.leftMargin = Ui.px(13.0f);
            linearLayout.addView(linearLayout2, layoutParams2);
        }
        linearLayout.setClickable(true);
        linearLayout.setOnClickListener(new View.OnClickListener() { // from class: site.liweike.watchai.MainActivity.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.open(cls);
            }
        });
        Ui.pressable(linearLayout);
        this.stage.addView(linearLayout, Ui.fl(2.0f * fMin, f2, 49, 0.0f, f));
        Ui.rise(linearLayout, i2 * 60);
    }
}
