package site.liweike.watchai;

import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextClock;

public class MainActivity extends BaseActivity {

    @Override protected boolean swipeBack() { return false; }

    @Override protected void build() {
        // 顶部改为品牌名（衬线体，Claude 风格），不再显示时钟。
        android.widget.TextView brand = Ui.text(this, "LI Agent", 26, Ui.TEXT, Ui.SERIF);
        brand.setLetterSpacing(0.04f);
        brand.setIncludeFontPadding(true);
        brand.setPadding(0, Ui.px(3), 0, Ui.px(3));
        stage.addView(brand, Ui.fl(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 40));
        Ui.rise(brand, 0);

        // 恢复上次的地址记忆，再顺手问一次状态刷新 IPv6。
        Net.get(this).restore(this);
        Net.get(this).api("/api/status", (ok, value, error) -> { });

        addRow("DeepSeek Harness", "工作区 · 对话", IconView.TERMINAL, false, WorkspacesActivity.class, 84f, 70f, 392f, 1);
        addRow("DeepSeek Chat", "历史 · 新对话", IconView.SPARKLE, false, ConversationsActivity.class, 162f, 70f, 392f, 2);
        addRow("information", "系统 · 用量 · 热力图", IconView.MONITOR, false, InformationActivity.class, 240f, 70f, 392f, 3);
        addRow("about", null, IconView.INFO, false, AboutActivity.class, 318f, 52f, 200f, 4);
    }

    private void addRow(String title, String sub, int icon, boolean hero,
                        final Class<?> target, float top, float h, float cap, int index) {
        float half = Math.min(Ui.chordHalf(top, top + h, 6f), cap / 2f);
        boolean compact = sub == null;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(compact ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.px(compact ? 10 : 15), 0, Ui.px(14), 0);
        row.setBackground(hero ? Ui.accent(40) : Ui.card(40));

        int box = compact ? 30 : 46;
        IconView iv = new IconView(this, icon, hero ? Ui.INK : Ui.A1);
        int pad = compact ? 4 : 11;
        iv.setPadding(Ui.px(pad), Ui.px(pad), Ui.px(pad), Ui.px(pad));
        if (!compact) iv.setBackground(Ui.circle(hero ? 0x26000000 : Ui.CHIP));
        row.addView(iv, new LinearLayout.LayoutParams(Ui.px(box), Ui.px(box)));

        if (compact) {
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            tp.leftMargin = Ui.px(8);
            row.addView(Ui.text(this, title, 22, Ui.SUB, Ui.SERIF), tp);
        } else {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_VERTICAL);
            col.addView(Ui.text(this, title, 25, hero ? Ui.INK : Ui.TEXT, Ui.SERIF));
            View sp = new View(this);
            col.addView(sp, new LinearLayout.LayoutParams(1, 0));
            col.addView(Ui.text(this, sub, 18, hero ? 0xB31F1E1D : Ui.SUB, Ui.SANS));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
            clp.leftMargin = Ui.px(13);
            row.addView(col, clp);
        }

        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { open(target); }
        });
        Ui.pressable(row);
        stage.addView(row, Ui.fl(half * 2f, h, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, top));
        Ui.rise(row, 60 * index);
    }
}