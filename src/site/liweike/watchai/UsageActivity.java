package site.liweike.watchai;

import android.animation.ValueAnimator;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** 用量信息：环形图 = 今日占全部历史总量的比例。 */
public class UsageActivity extends BaseActivity {
    private RingView ring;
    private TextView pct, note;
    private LinearLayout stats;
    private Ticker ticker;
    private boolean loading;

    @Override protected void build() {
        TextView title = Ui.text(this, "用量信息", 25, Ui.TEXT, "sans-serif-medium");
        title.setGravity(Gravity.CENTER);
        stage.addView(title, Ui.fl(360, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 38));

        FrameLayout box = new FrameLayout(this);
        ring = new RingView(this);
        box.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        pct = Ui.text(this, "0%", 62, Ui.TEXT, "sans-serif-light");
        pct.setGravity(Gravity.CENTER);
        mid.addView(pct);
        View sp = new View(this);
        mid.addView(sp, new LinearLayout.LayoutParams(1, Ui.px(6)));
        TextView cap = Ui.text(this, "今日占比", 21, Ui.SUB, "sans-serif");
        mid.addView(cap);
        box.addView(mid, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(box, Ui.fl(228, 228, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 84));

        stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setGravity(Gravity.CENTER);
        stage.addView(stats, Ui.fl(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 326));

        note = Ui.text(this, "读取中…", 18, Ui.SUB, "sans-serif");
        stage.addView(note, Ui.fl(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 394));

        // 中继侧用量有 60 秒缓存，取 15 秒一次的轮询节奏没有意义，但用户进来
        // 待着不动时数字自己会跳，比"退出重进"好。开会话时立即拉一次。
        ticker = new Ticker(20_000, this::load);
    }

    @Override protected void onResume() {
        super.onResume();
        ticker.start();
    }

    @Override protected void onPause() {
        super.onPause();
        ticker.stop();
    }

    private void load() {
        if (loading) return;
        loading = true;
        Net.get(this).apiCached(this, "/api/usage?days=7300", (ok, value, error) -> {
            loading = false;
            if (isFinishing() || isDestroyed()) return;
            if (!ok || value == null || !value.optBoolean("known", false)) {
                note.setText(error == null ? "暂无用量数据" : error);
                return;
            }
            JSONArray days = value.optJSONArray("days");
            long today = 0;
            long local = 0;
            for (int i = 0; days != null && i < days.length(); i++) {
                JSONObject d = days.optJSONObject(i);
                if (d != null) local += d.optLong("inputTokens") + d.optLong("outputTokens");
            }
            long total = value.optLong("totalTokens", local);
            stats.removeAllViews();
            if (days != null && days.length() > 0) {
                JSONObject last = days.optJSONObject(days.length() - 1);
                if (last != null) today = last.optLong("totalTokens");
            }
            final float ratio = total > 0 ? Math.max(0f, Math.min(1f, (float) today / total)) : 0f;

            ValueAnimator va = ValueAnimator.ofFloat(0f, ratio);
            va.setDuration(900);
            va.setStartDelay(150);
            va.setInterpolator(new DecelerateInterpolator());
            va.addUpdateListener(a -> {
                float v = (Float) a.getAnimatedValue();
                ring.setProgress(v);
                pct.setText(Math.round(v * 100f) + "%");
            });
            va.start();

            stats.addView(stat(InformationActivity.shortTokens(total),
                    "全部"));
            View div = new View(this);
            div.setBackgroundColor(Ui.STROKE);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Math.max(1, Ui.px(1)), Ui.px(36));
            dp.leftMargin = Ui.px(26);
            dp.rightMargin = Ui.px(26);
            stats.addView(div, dp);
            stats.addView(stat(InformationActivity.shortTokens(today), "今日"));
            note.setText("数据来自电脑上的中继");
        });
    }

    private View stat(String value, String label) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(Ui.text(this, value, 27, Ui.TEXT, "sans-serif-medium"));
        View sp = new View(this);
        c.addView(sp, new LinearLayout.LayoutParams(1, Ui.px(4)));
        c.addView(Ui.text(this, label, 18, Ui.SUB, "sans-serif"));
        return c;
    }
}