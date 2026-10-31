package site.liweike.watchai;

import android.view.Gravity;
import android.view.View;
import android.text.TextUtils;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ProjectsActivity extends BaseActivity {
    @Override protected void build() {
        EdgeScrollView sv = new EdgeScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(0, Ui.px(54), 0, Ui.px(120));

        TextView h = Ui.text(this, "项目与对话", 26, Ui.TEXT, "sans-serif-medium");
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-2, -2);
        hp.bottomMargin = Ui.px(20);
        col.addView(h, hp);

        col.addView(label("项目"));
        col.addView(item("官网改版", "3 段对话 · 今天", IconView.FOLDER));
        col.addView(item("论文润色", "5 段对话 · 昨天", IconView.FOLDER));
        col.addView(item("旅行计划", "2 段对话 · 周二", IconView.FOLDER));

        col.addView(label("最近对话"));
        col.addView(item("周末行程规划", "2 分钟前", IconView.BUBBLE));
        col.addView(item("英文邮件润色", "1 小时前", IconView.BUBBLE));
        col.addView(item("代码问题排查", "昨天", IconView.BUBBLE));

        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        stage.addView(sv, new FrameLayout.LayoutParams(-1, -1));
    }

    private View label(String s) {
        TextView t = Ui.text(this, s, 19, Ui.SUB, "sans-serif-medium");
        t.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(330), -2);
        lp.topMargin = Ui.px(6);
        lp.bottomMargin = Ui.px(10);
        lp.leftMargin = Ui.px(10);
        t.setLayoutParams(lp);
        return t;
    }

    private View item(String title, String sub, int icon) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.px(16), Ui.px(12), Ui.px(16), Ui.px(12));
        row.setBackground(Ui.card(28));

        IconView iv = new IconView(this, icon, Ui.A1);
        iv.setPadding(Ui.px(9), Ui.px(9), Ui.px(9), Ui.px(9));
        iv.setBackground(Ui.circle(Ui.CHIP));
        row.addView(iv, new LinearLayout.LayoutParams(Ui.px(42), Ui.px(42)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, title, 24, Ui.TEXT, "sans-serif-medium");
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        TextView s = Ui.text(this, sub, 19, Ui.SUB, "sans-serif");
        s.setSingleLine(true);
        col.addView(t);
        View sp = new View(this);
        col.addView(sp, new LinearLayout.LayoutParams(1, Ui.px(4)));
        col.addView(s);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        cp.leftMargin = Ui.px(12);
        row.addView(col, cp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(330), -2);
        lp.bottomMargin = Ui.px(10);
        row.setLayoutParams(lp);
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { open(ChatActivity.class); }
        });
        Ui.pressable(row);
        return row;
    }
}
