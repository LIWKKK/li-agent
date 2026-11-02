package site.liweike.watchai;

import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared round-screen building blocks: lists, cards, labels, key/value rows. */
public abstract class ListScreen extends BaseActivity {
    protected LinearLayout col;

    protected void startList(String heading, String sub) {
        col = makeList(stage, heading, sub, 52);
    }

    /** Scrolling list whose rows shrink/fade toward the screen edges. heading may be null. */
    protected LinearLayout makeList(FrameLayout parent, String heading, String sub, int topPad) {
        EdgeScrollView sv = new EdgeScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setGravity(Gravity.CENTER_HORIZONTAL);
        list.setPadding(0, Ui.px(topPad), 0, Ui.px(120));
        if (heading != null) {
            TextView h = Ui.text(this, heading, 26, Ui.TEXT, Ui.SERIF);
            h.setSingleLine(true);
            h.setEllipsize(TextUtils.TruncateAt.END);
            h.setMaxWidth(Ui.px(300));
            h.setGravity(Gravity.CENTER);
            list.addView(h, new LinearLayout.LayoutParams(-1, -2));
            if (sub != null) {
                TextView s = Ui.text(this, sub, 19, Ui.SUB, Ui.SANS);
                s.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
                sp.topMargin = Ui.px(6);
                list.addView(s, sp);
            }
            View gap = new View(this);
            list.addView(gap, new LinearLayout.LayoutParams(1, Ui.px(18)));
        }
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        parent.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        return list;
    }

    /** Plain scrolling column (no per-row scaling) with soft top/bottom fade, for tall cards. */
    protected LinearLayout makePlain(FrameLayout parent, int topPad) {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(Ui.px(56));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setGravity(Gravity.CENTER_HORIZONTAL);
        list.setPadding(0, Ui.px(topPad), 0, Ui.px(120));
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        parent.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        return list;
    }

    protected View label(String s) {
        TextView t = Ui.text(this, s, 18, Ui.SUB, Ui.SANS_M);
        t.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(330), -2);
        lp.topMargin = Ui.px(6);
        lp.bottomMargin = Ui.px(10);
        lp.leftMargin = Ui.px(10);
        t.setLayoutParams(lp);
        return t;
    }

    protected View item(String title, String sub, int icon, boolean accent, View.OnClickListener click) {
        return item(title, sub, icon, accent, false, click);
    }

    protected View item(String title, String sub, int icon, boolean accent, boolean checked,
                        View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.px(16), Ui.px(12), Ui.px(16), Ui.px(12));
        row.setBackground(accent ? Ui.accent(28) : Ui.card(28));

        IconView iv = new IconView(this, icon, accent ? Ui.INK : Ui.A1);
        iv.setPadding(Ui.px(9), Ui.px(9), Ui.px(9), Ui.px(9));
        iv.setBackground(Ui.circle(accent ? 0x26000000 : Ui.CHIP));
        row.addView(iv, new LinearLayout.LayoutParams(Ui.px(42), Ui.px(42)));

        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.text(this, title, 24, accent ? Ui.INK : Ui.TEXT, Ui.SERIF);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(t);
        if (sub != null) {
            View sp = new View(this);
            c.addView(sp, new LinearLayout.LayoutParams(1, Ui.px(2)));
            TextView s = Ui.text(this, sub, 18, accent ? 0xB31F1E1D : Ui.SUB, Ui.SANS);
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            c.addView(s);
        }
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        cp.leftMargin = Ui.px(12);
        row.addView(c, cp);

        if (checked) {
            IconView ck = new IconView(this, IconView.CHECK, Ui.A1);
            LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(Ui.px(24), Ui.px(24));
            kp.leftMargin = Ui.px(8);
            row.addView(ck, kp);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(330), -2);
        lp.bottomMargin = Ui.px(10);
        row.setLayoutParams(lp);
        row.setClickable(true);
        row.setOnClickListener(click);
        Ui.pressable(row);
        return row;
    }

    protected LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(Ui.px(18), Ui.px(16), Ui.px(18), Ui.px(8));
        c.setBackground(Ui.card(28));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(330), -2);
        lp.bottomMargin = Ui.px(12);
        c.setLayoutParams(lp);
        return c;
    }

    // ---------------------------------------------------------------- 确认浮层

    /** 当前开着的确认浮层，同一时刻只允许一个。 */
    private FrameLayout confirmLayer;

    protected boolean confirmOpen() { return confirmLayer != null; }

    protected void closeConfirm() {
        if (confirmLayer == null) return;
        stage.removeView(confirmLayer);
        confirmLayer = null;
    }

    /**
     * 一句确认再动手。
     *
     * 手表屏幕小、删除不可恢复，长按后直接执行太容易误触；这里给"取消/删除"
     * 两个大按钮，点空白也等于取消。
     */
    protected void confirmDialog(String title, String subtitle, String warn, Runnable onOk) {
        closeConfirm();
        confirmLayer = new FrameLayout(this);
        confirmLayer.setBackgroundColor(0xF21F1E1D);
        confirmLayer.setClickable(true);
        confirmLayer.setOnClickListener(v -> closeConfirm());
        stage.addView(confirmLayer, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setBackground(Ui.card(30));
        box.setPadding(Ui.px(22), Ui.px(20), Ui.px(22), Ui.px(18));

        TextView t = Ui.text(this, title, 24, Ui.TEXT, Ui.SERIF);
        t.setGravity(Gravity.CENTER);
        box.addView(t);

        if (subtitle != null) {
            TextView name = Ui.text(this, subtitle, 20, Ui.SUB, Ui.SANS);
            name.setGravity(Gravity.CENTER);
            name.setMaxWidth(Ui.px(250));
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
            np.topMargin = Ui.px(10);
            box.addView(name, np);
        }

        if (warn != null) {
            TextView w = Ui.text(this, warn, 17, Ui.A1, Ui.SANS);
            w.setGravity(Gravity.CENTER);
            w.setMaxWidth(Ui.px(250));
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, -2);
            wp.topMargin = Ui.px(8);
            box.addView(w, wp);
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = Ui.px(18);
        box.addView(buttons, bp);

        View cancel = confirmButton("取消", false);
        cancel.setOnClickListener(v -> closeConfirm());
        buttons.addView(cancel);

        View ok = confirmButton("删除", true);
        ok.setOnClickListener(v -> {
            closeConfirm();
            if (onOk != null) onOk.run();
        });
        LinearLayout.LayoutParams okp = new LinearLayout.LayoutParams(-2, -2);
        okp.leftMargin = Ui.px(12);
        buttons.addView(ok, okp);

        confirmLayer.addView(box, new FrameLayout.LayoutParams(Ui.px(300), -2, Gravity.CENTER));
        Ui.rise(box, 0);
    }

    private View confirmButton(String text, boolean danger) {
        TextView t = Ui.text(this, text, 21, danger ? 0xFFFFFFFF : Ui.TEXT, Ui.SANS_M);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.px(24), Ui.px(13), Ui.px(24), Ui.px(13));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.px(22));
        if (danger) {
            g.setColor(0xFFB4462F);
        } else {
            g.setColor(Ui.CHIP);
            g.setStroke(Math.max(1, Ui.px(1)), Ui.STROKE);
        }
        t.setBackground(g);
        t.setClickable(true);
        Ui.pressable(t);
        return t;
    }

    protected View kv(String k, String v, int keyW) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView kt = Ui.text(this, k, 19, Ui.SUB, Ui.SANS);
        kt.setSingleLine(true);
        kt.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(kt, new LinearLayout.LayoutParams(Ui.px(keyW), -2));
        TextView vt = Ui.text(this, v, 21, Ui.TEXT, Ui.SANS);
        vt.setSingleLine(true);
        vt.setEllipsize(TextUtils.TruncateAt.END);
        vt.setGravity(Gravity.END);
        r.addView(vt, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.px(10);
        r.setLayoutParams(lp);
        return r;
    }
}