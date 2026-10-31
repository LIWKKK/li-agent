package site.liweike.watchai;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** 电脑系统/硬件信息 + 模型 token 用量 + 热力图（数据来自中继 /api/live、/api/usage）。 */
public class InformationActivity extends BaseActivity {
    private LinearLayout col;

    @Override protected void build() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(Ui.px(56));
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(0, Ui.px(54), 0, Ui.px(120));
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        stage.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        TextView head = Ui.text(this, "information", 26, Ui.TEXT, Ui.SERIF);
        head.setGravity(Gravity.CENTER);
        head.setPadding(0, Ui.px(3), 0, Ui.px(3));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(Ui.px(330), -2);
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(head, hp);
        TextView sub = Ui.text(this, "读取中…", 19, Ui.SUB, Ui.SANS);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.topMargin = Ui.px(6);
        sp.bottomMargin = Ui.px(20);
        col.addView(sub, sp);

        Net.get(this).apiCached(this, "/api/live", (ok, value, error) -> {
            if (!ok || value == null || !value.optBoolean("ok", false)) {
                sub.setText(error == null ? "连不上中继" : error);
                return;
            }
            sub.setText("实时 · 15 秒刷新");
            renderLive(value);
        });
        Net.get(this).apiCached(this, "/api/usage?days=7300", (ok, value, error) -> {
            if (ok && value != null && value.optBoolean("known", false)) renderUsage(value);
        });

    }

    private void renderLive(JSONObject value) {
        JSONObject host = value.optJSONObject("host");
        JSONObject mem = host == null ? null : host.optJSONObject("mem");
        JSONObject dsh = value.optJSONObject("dsh");

        if (host != null) {
            col.addView(label("系统"));
            LinearLayout sys = card();
            sys.addView(kv("主机", str(host, "hostname"), 80));
            sys.addView(kv("平台", str(host, "platform") + " " + str(host, "release"), 80));
            sys.addView(kv("架构", str(host, "arch"), 80));
            long up = host.optLong("uptimeSec");
            if (up > 0) sys.addView(kv("运行", duration(up), 80));
            col.addView(sys);

            col.addView(label("硬件"));
            LinearLayout hw = card();
            int load = host.optInt("loadPct");
            // 只到系列，不暴露具体型号与核数。
            String cpu = str(host, "cpuModel");
            hw.addView(metric("CPU", load + "%", cpu.isEmpty() ? "Intel Core i7" : cpu, load / 100f));
            if (mem != null) {
                hw.addView(metric("内存", bytes(mem.optLong("used")) + " / " + bytes(mem.optLong("total")),
                        null, mem.optLong("total") > 0 ? (float) mem.optLong("used") / mem.optLong("total") : 0f));
            }
            JSONObject swap = host.optJSONObject("swap");
            if (swap != null && swap.optLong("total") > 0) {
                hw.addView(metric("Swap", bytes(swap.optLong("used")) + " / " + bytes(swap.optLong("total")),
                        null, (float) swap.optLong("used") / swap.optLong("total")));
            }
            if (host.has("tempC")) {
                hw.addView(kv("温度", host.optDouble("tempC") + " ℃", 80));
            }
            if (host.optLong("processes") > 0) {
                hw.addView(kv("进程", String.valueOf(host.optLong("processes")), 80));
            }
            col.addView(hw);

            JSONArray disks = host.optJSONArray("disks");
            if (disks != null && disks.length() > 0) {
                col.addView(label("磁盘"));
                LinearLayout dc = card();
                for (int i = 0; i < disks.length(); i++) {
                    JSONObject d = disks.optJSONObject(i);
                    if (d == null) continue;
                    long total = d.optLong("total");
                    dc.addView(metric(str(d, "mount"), d.optInt("usedPct") + "%",
                            bytes(d.optLong("used")) + " / " + bytes(total),
                            total > 0 ? (float) d.optLong("used") / total : 0f));
                }
                col.addView(dc);
            }
        }

        if (dsh != null) {
            col.addView(label("DSH"));
            LinearLayout c = card();
            boolean authOk = dsh.optBoolean("authOk", false);
            c.addView(kv("连接", authOk ? "正常 · " + dsh.optInt("latencyMs") + "ms" : "不可用", 80));
            JSONObject stats = dsh.optJSONObject("sessions");
            if (stats != null) {
                c.addView(kv("会话", stats.optInt("total") + " · 跑 " + stats.optInt("running"), 80));
                c.addView(kv("待答", String.valueOf(stats.optInt("waiting")), 80));
                c.addView(kv("子任务", String.valueOf(stats.optInt("subagents")), 80));
            }
            col.addView(c);
        }
    }

    private void renderUsage(JSONObject value) {
        JSONArray days = value.optJSONArray("days");
        if (days == null || days.length() == 0) return;

        // 中继直接给汇总，不用再把日明细加起来。
        long total = value.optLong("totalTokens", 0);
        long input = value.optLong("inputTokens", 0);
        long output = value.optLong("outputTokens", 0);
        long cacheRead = value.optLong("cacheReadTokens", 0);
        long peak = 0;
        long today = 0;
        for (int i = 0; i < days.length(); i++) {
            JSONObject d = days.optJSONObject(i);
            if (d == null) continue;
            long t = d.optLong("tokens");
            if (t > peak) peak = t;
            if (i == days.length() - 1) today = t;
        }

        col.addView(label("模型 TOKEN 用量"));
        LinearLayout tk = card();
        tk.addView(Ui.text(this, shortTokens(total), 44, Ui.TEXT, Ui.SERIF));
        TextView cap = Ui.text(this, "全部", 19, Ui.SUB, Ui.SANS);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        cp.topMargin = Ui.px(4);
        cp.bottomMargin = Ui.px(12);
        tk.addView(cap, cp);
        // 两段：本机 / 其他，直观看出外部占大头。
        long localPart = value.optLong("localTokens", total);
        float fLocal = total > 0 ? (float) localPart / total : 1f;
        float fOther = Math.max(0f, 1f - fLocal);
        BarView split = new BarView(this).set(new float[]{fLocal, fOther},
                new int[]{Ui.A1, Ui.A2});
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, Ui.px(10));
        bp.bottomMargin = Ui.px(12);
        tk.addView(split, bp);
        long local = value.optLong("localTokens", 0);
        long external = value.optLong("externalTokens", 0);
        if (external > 0) {
            tk.addView(kv("本机", shortTokens(local), 150));
            tk.addView(kv("其他", shortTokens(external), 150));
        }
        tk.addView(kv("输入", shortTokens(input), 150));
        tk.addView(kv("输出", shortTokens(output), 150));
        tk.addView(kv("缓存读", shortTokens(cacheRead), 150));
        tk.addView(kv("今日", shortTokens(today), 150));
        col.addView(tk);

        col.addView(label("热力图"));
        LinearLayout hm = card();
        int weeks = Math.max(1, Math.min(53, (days.length() + 6) / 7));
        TextView hc = Ui.text(this, "每日 token · " + days.length() + " 天", 18, Ui.SUB, Ui.SANS);
        LinearLayout.LayoutParams hcp = new LinearLayout.LayoutParams(-2, -2);
        hcp.bottomMargin = Ui.px(12);
        hm.addView(hc, hcp);
        // 天数少时格子会被拉得很宽，高度按行数收一点，看着不空。
        int heatHeight = Math.max(60, Math.min(170, weeks * 40));
        hm.addView(new HeatmapView(this, weeks, buildHeat(days, peak)),
                new LinearLayout.LayoutParams(-1, Ui.px(heatHeight)));
        hm.addView(heatLegend());
        col.addView(hm);
    }

    /** 把日用量转成 weeks×7 的 0..4 档热力格。 */
    private int[][] buildHeat(JSONArray days, long peak) {
        int weeks = Math.max(1, Math.min(53, (days.length() + 6) / 7));
        int[][] grid = new int[weeks][7];
        // 数据靠右对齐（最新的一天在最后一格），前面空着表示没数据。
        int offset = weeks * 7 - days.length();
        for (int i = 0; i < days.length(); i++) {
            JSONObject d = days.optJSONObject(i);
            // 中继的日明细字段是 tokens（不是 totalTokens）。
            long t = d == null ? 0 : d.optLong("tokens");
            if (t <= 0) continue;
            int level = peak <= 0 ? 0 : 1 + (int) Math.min(3L, t * 4L / peak);
            int slot = offset + i;
            if (slot < 0 || slot >= weeks * 7) continue;
            grid[slot / 7][slot % 7] = level;
        }
        return grid;
    }

    // ---------------------------------------------------------------- 组件

    private View label(String s) {
        TextView t = Ui.text(this, s, 18, Ui.SUB, Ui.SANS_M);
        t.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(330), -2);
        lp.topMargin = Ui.px(6);
        lp.bottomMargin = Ui.px(10);
        lp.leftMargin = Ui.px(10);
        t.setLayoutParams(lp);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(Ui.px(18), Ui.px(16), Ui.px(18), Ui.px(8));
        c.setBackground(Ui.card(28));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(330), -2);
        lp.bottomMargin = Ui.px(12);
        c.setLayoutParams(lp);
        return c;
    }

    private View kv(String k, String v, int keyW) {
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

    private View metric(String label, String value, String detail, float frac) {
        LinearLayout m = new LinearLayout(this);
        m.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(Ui.text(this, label, 19, Ui.SUB, Ui.SANS), new LinearLayout.LayoutParams(Ui.px(70), -2));
        TextView vt = Ui.text(this, value, 21, Ui.TEXT, Ui.SANS);
        vt.setGravity(Gravity.END);
        vt.setSingleLine(true);
        top.addView(vt, new LinearLayout.LayoutParams(0, -2, 1f));
        m.addView(top, new LinearLayout.LayoutParams(-1, -2));
        if (detail != null && !detail.isEmpty()) {
            TextView d = Ui.text(this, detail, 17, Ui.SUB, Ui.SANS);
            d.setSingleLine(true);
            d.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, -2);
            dp.topMargin = Ui.px(4);
            m.addView(d, dp);
        }
        BarView b = new BarView(this).set(new float[]{Math.max(0f, Math.min(1f, frac))}, new int[]{Ui.A1});
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, Ui.px(8));
        bp.topMargin = Ui.px(8);
        m.addView(b, bp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.px(14);
        m.setLayoutParams(lp);
        return m;
    }

    private View heatLegend() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        TextView a = Ui.text(this, "少", 17, Ui.SUB, Ui.SANS);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, -2);
        ap.rightMargin = Ui.px(6);
        l.addView(a, ap);
        for (int i = 0; i < HeatmapView.LEVELS.length; i++) {
            View c = new View(this);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setColor(HeatmapView.LEVELS[i]);
            g.setCornerRadius(Ui.px(4));
            c.setBackground(g);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.px(16), Ui.px(16));
            cp.rightMargin = Ui.px(4);
            l.addView(c, cp);
        }
        TextView b = Ui.text(this, "多", 17, Ui.SUB, Ui.SANS);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.leftMargin = Ui.px(4);
        l.addView(b, bp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.px(12);
        lp.bottomMargin = Ui.px(8);
        l.setLayoutParams(lp);
        return l;
    }

    // ---------------------------------------------------------------- 工具

    static String str(JSONObject o, String k) {
        String v = o == null ? "" : o.optString(k, "");
        return v == null ? "" : v;
    }

    static String bytes(long v) {
        if (v <= 0) return "—";
        double gb = v / 1073741824.0;
        if (gb >= 1.0) return String.format(Locale.US, "%.1fG", gb);
        return String.format(Locale.US, "%.0fM", v / 1048576.0);
    }

    static String shortTokens(long v) {
        if (v >= 1_000_000_000L) return String.format(Locale.US, "%.2fB", v / 1_000_000_000.0);
        if (v >= 1_000_000L) return String.format(Locale.US, "%.1fM", v / 1_000_000.0);
        if (v >= 1_000L) return String.format(Locale.US, "%.1fK", v / 1_000.0);
        return String.valueOf(v);
    }

    static String duration(long sec) {
        long d = sec / 86400, h = (sec % 86400) / 3600, m = (sec % 3600) / 60;
        if (d > 0) return d + " 天 " + h + " 时";
        if (h > 0) return h + " 时 " + m + " 分";
        return m + " 分";
    }

    static String shorten(String s, int n) {
        String v = s == null ? "" : s.trim();
        return v.length() <= n ? v : v.substring(0, n) + "…";
    }
}