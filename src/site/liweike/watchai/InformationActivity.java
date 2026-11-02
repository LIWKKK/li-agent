package site.liweike.watchai;

import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
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

public class InformationActivity extends ListScreen {
    private TextView sub;
    private LinearLayout liveBody;
    private LinearLayout usageBody;
    private Ticker ticker;
    private long usageAt;

    @Override // site.liweike.watchai.BaseActivity
    protected void build() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(2);
        scrollView.setVerticalFadingEdgeEnabled(true);
        scrollView.setFadingEdgeLength(Ui.px(56.0f));
        this.col = new LinearLayout(this);
        this.col.setOrientation(1);
        this.col.setGravity(1);
        this.col.setPadding(0, Ui.px(54.0f), 0, Ui.px(120.0f));
        scrollView.addView(this.col, new FrameLayout.LayoutParams(-1, -2));
        this.stage.addView(scrollView, new FrameLayout.LayoutParams(-1, -1));
        // 标题：占满整行宽 + 文字居中。col 是 wrap_content 的，wrap 标题会跟着
        // 内容宽走，在圆形屏幕上视觉偏移；撑满行宽后文字才真正落在屏幕中轴。
        TextView infoTitle = Ui.text(this, "info", 26.0f, Ui.TEXT, "serif");
        infoTitle.setGravity(Gravity.CENTER);
        this.col.addView(infoTitle, new LinearLayout.LayoutParams(-1, -2));
        this.sub = Ui.text(this, "运行电脑 · 读取中", 19.0f, Ui.SUB, "sans-serif");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.px(6.0f);
        lp.bottomMargin = Ui.px(20.0f);
        this.col.addView(this.sub, lp);
        this.liveBody = column();
        this.usageBody = column();
        ticker = new Ticker(15_000, this::refresh);
    }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(1);
        c.setGravity(1);
        this.col.addView(c, new FrameLayout.LayoutParams(-1, -2));
        return c;
    }

    @Override protected void onResume() {
        super.onResume();
        ticker.start();
    }

    @Override protected void onPause() {
        super.onPause();
        ticker.stop();
    }

    private void refresh() {
        Net.get(this).apiCached(this, "/api/live", (ok, value, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (!ok || value == null || !value.optBoolean("ok", false)) {
                if (liveBody.getChildCount() == 0) {
                    sub.setText(error == null || error.isEmpty() ? "连不上中继" : error);
                }
                return;
            }
            sub.setText("运行电脑 · 实时");
            liveBody.removeAllViews();
            renderLive(value);
            if (SystemClock.elapsedRealtime() - usageAt > 60_000) {
                usageAt = SystemClock.elapsedRealtime();
                Net.get(this).apiCached(this, "/api/usage?days=7300", (ok2, v2, e2) -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (!ok2 || v2 == null || !v2.optBoolean("known", false)) return;
                    usageBody.removeAllViews();
                    renderUsage(v2);
                });
            }
        });
    }

    // ---------- 实时数据（结构沿用壳：系统/硬件/磁盘 卡片） ----------

    private void renderLive(JSONObject value) {
        JSONObject host = value.optJSONObject("host");
        JSONObject dsh = value.optJSONObject("dsh");
        if (host == null) return;
        this.col = liveBody;
        this.col.addView(label("系统"));
        LinearLayout sys = card();
        sys.addView(kv("系统", str(host, "platform") + " " + str(host, "release"), 80));
        sys.addView(kv("内核", str(host, "arch"), 80));
        sys.addView(kv("主机", str(host, "hostname"), 80));
        long up = host.optLong("uptimeSec");
        if (up > 0) sys.addView(kv("运行", duration(up), 80));
        this.col.addView(sys);

        this.col.addView(label("硬件"));
        JSONObject mem = host.optJSONObject("mem");
        LinearLayout hw = card();
        int load = host.optInt("loadPct");
        String cpu = str(host, "cpuModel");
        hw.addView(metric("CPU", load + "%", cpu, load / 100f));
        if (mem != null && mem.optLong("total") > 0) {
            hw.addView(metric("内存", bytes(mem.optLong("used")) + " / " + bytes(mem.optLong("total")), null,
                    (float) mem.optLong("used") / mem.optLong("total")));
        }
        if (host.has("tempC")) {
            hw.addView(metric("温度", host.optDouble("tempC") + "°C", null, (float) (host.optDouble("tempC") / 100.0)));
        }
        this.col.addView(hw);

        JSONArray disks = host.optJSONArray("disks");
        if (disks != null && disks.length() > 0) {
            this.col.addView(label("磁盘"));
            LinearLayout dc = card();
            for (int i = 0; i < disks.length(); i++) {
                JSONObject d = disks.optJSONObject(i);
                if (d == null) continue;
                long total = d.optLong("total");
                if (total <= 0) continue;
                dc.addView(metric(str(d, "mount"), d.optInt("usedPct") + "%",
                        bytes(d.optLong("used")) + " / " + bytes(total), (float) d.optLong("used") / total));
            }
            this.col.addView(dc);
        }

        if (dsh != null) {
            this.col.addView(label("Harness"));
            LinearLayout c = card();
            boolean authOk = dsh.optBoolean("authOk", false);
            c.addView(kv("连接", authOk ? "正常 · " + dsh.optInt("latencyMs") + "ms" : "不可用", 80));
            JSONObject stats = dsh.optJSONObject("sessions");
            if (stats != null) {
                c.addView(kv("会话", stats.optInt("total") + " · 跑 " + stats.optInt("running"), 80));
                c.addView(kv("待答", String.valueOf(stats.optInt("waiting")), 80));
                c.addView(kv("子任务", String.valueOf(stats.optInt("subagents")), 80));
            }
            this.col.addView(c);
        }
        this.col = usageBody;
    }

    // ---------- 用量（结构沿用壳：总量 + BarView + 热力图） ----------

    private void renderUsage(JSONObject value) {
        JSONArray days = value.optJSONArray("days");
        if (days == null || days.length() == 0) return;
        long total = value.optLong("totalTokens", 0);
        long input = value.optLong("inputTokens", 0);
        long output = value.optLong("outputTokens", 0);
        long peak = 0;
        long today = 0;
        for (int i = 0; i < days.length(); i++) {
            JSONObject d = days.optJSONObject(i);
            if (d == null) continue;
            long t = d.optLong("tokens");
            if (t > peak) peak = t;
            if (i == days.length() - 1) today = t;
        }

        this.col = usageBody;
        this.col.addView(label("模型 TOKEN 用量"));
        LinearLayout c3 = card();
        c3.addView(Ui.text(this, shortTokens(total), 44.0f, Ui.TEXT, "serif"));
        TextView cap = Ui.text(this, "全部总量", 19.0f, Ui.SUB, "sans-serif");
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-2, -2);
        lp2.topMargin = Ui.px(4.0f);
        lp2.bottomMargin = Ui.px(12.0f);
        c3.addView(cap, lp2);
        long in2 = input, out2 = output;
        float fIn = total > 0 ? (float) in2 / total : 0f;
        BarView bar = new BarView(this).set(new float[]{fIn, Math.max(0f, 1f - fIn)}, new int[]{Ui.A1, Ui.A2});
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(-1, Ui.px(10.0f));
        lp3.bottomMargin = Ui.px(12.0f);
        c3.addView(bar, lp3);
        c3.addView(heatLegend());
        c3.addView(kv("输入", shortTokens(input), 150));
        c3.addView(kv("输出", shortTokens(output), 150));
        c3.addView(kv("今日", shortTokens(today), 150));
        this.col.addView(c3);

        this.col = liveBody;
    }


    private View metric(String str, String str2, String str3, float f) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.addView(Ui.text(this, str, 19.0f, -6514030, "sans-serif"), new LinearLayout.LayoutParams(Ui.px(70.0f), -2));
        TextView textViewText = Ui.text(this, str2, 21.0f, -658195, "sans-serif");
        textViewText.setGravity(8388613);
        textViewText.setSingleLine(true);
        linearLayout2.addView(textViewText, new LinearLayout.LayoutParams(0, -2, 1.0f));
        linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(-1, -2));
        if (str3 != null) {
            TextView textViewText2 = Ui.text(this, str3, 17.0f, -6514030, "sans-serif");
            textViewText2.setSingleLine(true);
            textViewText2.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            layoutParams.topMargin = Ui.px(4.0f);
            linearLayout.addView(textViewText2, layoutParams);
        }
        View view = new BarView(this).set(new float[]{f}, new int[]{-2525353});
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, Ui.px(8.0f));
        layoutParams2.topMargin = Ui.px(8.0f);
        linearLayout.addView(view, layoutParams2);
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams3.bottomMargin = Ui.px(14.0f);
        linearLayout.setLayoutParams(layoutParams3);
        return linearLayout;
    }

    private View legend() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.addView(dot(-2525353));
        linearLayout.addView(Ui.text(this, "输入 0.86M", 18.0f, -6514030, "sans-serif"));
        linearLayout.addView(new View(this), new LinearLayout.LayoutParams(Ui.px(18.0f), 1));
        linearLayout.addView(dot(-1514280));
        linearLayout.addView(Ui.text(this, "输出 0.38M", 18.0f, -6514030, "sans-serif"));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
        layoutParams.bottomMargin = Ui.px(14.0f);
        linearLayout.setLayoutParams(layoutParams);
        linearLayout.setGravity(16);
        return linearLayout;
    }

    private static String str(JSONObject o, String k) {
        String v = o.optString(k, "");
        return v.isEmpty() ? "—" : v;
    }

    private static String duration(long sec) {
        long d = sec / 86400, h = (sec % 86400) / 3600, m = (sec % 3600) / 60;
        if (d > 0) return d + " 天 " + h + " 时";
        if (h > 0) return h + " 时 " + m + " 分";
        return m + " 分";
    }

    private static String bytes(long b) {
        if (b >= 1L << 30) return String.format(Locale.US, "%.1f GB", b / 1073741824f);
        if (b >= 1L << 20) return String.format(Locale.US, "%.0f MB", b / 1048576f);
        return String.format(Locale.US, "%.0f KB", b / 1024f);
    }

    static String shortTokens(long n) {
        if (n >= 1_000_000L) return String.format(Locale.US, "%.2fM", n / 1000000f);
        if (n >= 1_000L) return String.format(Locale.US, "%.1fK", n / 1000f);
        return String.valueOf(n);
    }

    private View dot(int i) {
        View view = new View(this);
        view.setBackground(Ui.circle(i));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Ui.px(10.0f), Ui.px(10.0f));
        layoutParams.rightMargin = Ui.px(7.0f);
        view.setLayoutParams(layoutParams);
        return view;
    }

    private View heatLegend() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(8388629);
        TextView textViewText = Ui.text(this, "少", 17.0f, -6514030, "sans-serif");
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
        layoutParams.rightMargin = Ui.px(6.0f);
        linearLayout.addView(textViewText, layoutParams);
        int[] legendColors = {0xFF353330, 0xFF5C3B30, 0xFF8A4A37, 0xFFB85F43, 0xFFD97757};
        for (int i = 0; i < legendColors.length; i++) {
            View view = new View(this);
            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setColor(legendColors[i]);
            gradientDrawable.setCornerRadius(Ui.px(4.0f));
            view.setBackground(gradientDrawable);
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(Ui.px(16.0f), Ui.px(16.0f));
            layoutParams2.rightMargin = Ui.px(4.0f);
            linearLayout.addView(view, layoutParams2);
        }
        TextView textViewText2 = Ui.text(this, "多", 17.0f, -6514030, "sans-serif");
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-2, -2);
        layoutParams3.leftMargin = Ui.px(4.0f);
        linearLayout.addView(textViewText2, layoutParams3);
        LinearLayout.LayoutParams layoutParams4 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams4.topMargin = Ui.px(12.0f);
        layoutParams4.bottomMargin = Ui.px(8.0f);
        linearLayout.setLayoutParams(layoutParams4);
        return linearLayout;
    }
}