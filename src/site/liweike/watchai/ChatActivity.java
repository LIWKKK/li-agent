package site.liweike.watchai;

import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * 对话页。两种模式：
 *   mode=harness  发到 DSH 会话（/api/send）
 *   mode=ds       发到 DeepSeek 网页版（/api/ds/send，SSE 流式）
 * UI 结构、气泡样式、模型芯片、用量面板全部沿用原设计，只把数据换成真实中继。
 */
public class ChatActivity extends ListScreen {
    static final String[] MODELS = {"deepseek-chat", "deepseek-reasoner"};
    static final String[] SHORT = {"chat", "reasoner"};
    static final String[] DESC = {"通用对话 · 响应快", "深度推理 · 先思考再回答"};

    private ScrollView sv;
    private LinearLayout msgs;
    private EditText input;
    private IconView send;
    private TextView modelTxt, speedTxt;
    private FrameLayout panel;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int model;
    private int totalIn, totalOut;
    private float lastSpeed;
    private long lastFirstMs, lastDurMs;
    private boolean busy;
    private String currentModelId = "";
    private String currentProvider = "";
    private String mode = "ds";
    private String sessionId = "";
    private String chatId = "";

    @Override protected void build() {
        model = getSharedPreferences("li", MODE_PRIVATE).getInt("model", 0);
        if (model < 0 || model >= MODELS.length) model = 0;
        currentModelId = getSharedPreferences("li", MODE_PRIVATE)
                .getString("model_id", "deepseek-flash");
        currentProvider = getSharedPreferences("li", MODE_PRIVATE)
                .getString("model_provider", "deepseek-official");

        mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "ds";
        sessionId = getIntent().getStringExtra("sessionId");
        if (sessionId == null) sessionId = "";
        chatId = getIntent().getStringExtra("chatId");
        if (chatId == null) chatId = "";

        String initial = getIntent().getStringExtra("title");
        final String title = initial == null ? "DeepSeek Chat" : initial;
        TextView hdr = Ui.text(this, "‹  " + title, 21, Ui.SUB, Ui.SANS_M);
        hdr.setSingleLine(true);
        hdr.setEllipsize(TextUtils.TruncateAt.END);
        hdr.setMaxWidth(Ui.px(230));
        hdr.setPadding(Ui.px(14), Ui.px(4), Ui.px(14), Ui.px(4));
        hdr.setOnClickListener(v -> finish());
        // 长按标题删掉当前这个会话（点一下还是返回）。
        Ui.longPress(hdr, () -> {
            boolean active = busy || polling;
            confirmDialog("删除会话", title,
                    active ? "正在运行，删除会立即中断" : null,
                    this::deleteThisChat);
        });
        stage.addView(hdr, Ui.fl(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 24));

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout mc = chip();
        modelTxt = Ui.text(this, SHORT[model], 19, Ui.TEXT, Ui.SANS_M);
        mc.addView(modelTxt);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.px(16), Ui.px(16));
        dp.leftMargin = Ui.px(4);
        mc.addView(new IconView(this, IconView.CHEVRON, Ui.SUB), dp);
        mc.setOnClickListener(v -> showModels());
        chips.addView(mc, chipLp(8));

        LinearLayout sc = chip();
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.px(16), Ui.px(16));
        bp.rightMargin = Ui.px(5);
        sc.addView(new IconView(this, IconView.BOLT, Ui.A1), bp);
        speedTxt = Ui.text(this, "— t/s", 19, Ui.TEXT, Ui.SANS_M);
        sc.addView(speedTxt);
        sc.setOnClickListener(v -> showUsage());
        chips.addView(sc, chipLp(0));
        stage.addView(chips, Ui.fl(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 62));

        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(Ui.px(44));
        msgs = new LinearLayout(this);
        msgs.setOrientation(LinearLayout.VERTICAL);
        msgs.setPadding(Ui.px(64), Ui.px(30), Ui.px(64), Ui.px(22));
        sv.addView(msgs, new FrameLayout.LayoutParams(-1, -2));
        stage.addView(sv, Ui.fl(-1, 258, Gravity.TOP | Gravity.LEFT, 0, 98));

        input = new EditText(this);
        input.setHint("说点什么…");
        input.setHintTextColor(0xFF6F6D67);
        input.setTextColor(Ui.TEXT);
        input.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22 * Ui.u);
        input.setBackground(Ui.card(26));
        input.setPadding(Ui.px(22), 0, Ui.px(18), 0);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_SEND) { send(); return true; }
            return false;
        });
        stage.addView(input, Ui.fl(232, 52, Gravity.TOP | Gravity.LEFT, 89, 364));

        send = new IconView(this, IconView.ARROW, Ui.INK);
        send.setPadding(Ui.px(12), Ui.px(12), Ui.px(12), Ui.px(12));
        send.setBackground(Ui.accentCircle());
        send.setAlpha(0.35f);
        send.setClickable(true);
        send.setOnClickListener(v -> send());
        Ui.pressable(send);
        stage.addView(send, Ui.fl(48, 48, Gravity.TOP | Gravity.LEFT, 329, 366));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                send.animate().alpha(s.toString().trim().length() > 0 ? 1f : 0.35f).setDuration(120).start();
            }
        });

        loadHistory();
    }

    // ---------------------------------------------------------------- 历史

    /** 已渲染的消息条数，用于增量追加（避免整页重绘闪烁）。 */
    private int shownCount;

    /** harness 拉会话消息，ds 读本地记录。 */
    private void loadHistory() {
        if ("harness".equals(mode)) {
            // 这里不用缓存：需要拿到真实的 running 状态，否则进会话时若正在跑就不会开始轮询。
            Net.get(this).api("/api/session?id=" + enc(sessionId), (ok, value, error) -> {
                if (!ok || value == null || !value.optBoolean("ok", false)) {
                    addMsg(error == null ? "读取失败" : error, false);
                    return;
                }
                JSONArray list = value.optJSONArray("messages");
                if (list == null || list.length() == 0) {
                    addMsg("还没有消息，说点什么开始吧。", false);
                    return;
                }
                for (int i = 0; i < list.length(); i++) {
                    JSONObject m = list.optJSONObject(i);
                    if (m == null) continue;
                    boolean user = "user".equals(m.optString("role", ""));
                    String text = m.optString("text", "");
                    if (text.isEmpty()) continue;
                    addMsg(text, user);
                    if (user) totalIn += tokens(text); else totalOut += tokens(text);
                }
                shownCount = list.length();
                // 进来时 agent 还在跑，直接开始盯着，不用等用户再发消息。
                if (value.optBoolean("running", false)) startPolling();
            });
        } else {
            JSONArray list;
            try { list = new JSONArray(getSharedPreferences("li", MODE_PRIVATE).getString("ds_msg_" + chatId, "[]")); }
            catch (Exception e) { list = new JSONArray(); }
            if (list.length() == 0) {
                addMsg("你好，我是 DeepSeek。有什么可以帮你？", false);
            } else {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject m = list.optJSONObject(i);
                    if (m == null) continue;
                    boolean user = "user".equals(m.optString("role", ""));
                    String text = m.optString("text", "");
                    if (!text.isEmpty()) addMsg(text, user);
                    if (user) totalIn += tokens(text); else totalOut += tokens(text);
                }
            }
            refreshChips();
        }
    }

    // ---------------------------------------------------------------- chips

    private LinearLayout chip() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(Ui.px(14), 0, Ui.px(12), 0);
        c.setBackground(Ui.card(17));
        c.setClickable(true);
        Ui.pressable(c);
        return c;
    }

    private LinearLayout.LayoutParams chipLp(int rightMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.px(34));
        lp.rightMargin = Ui.px(rightMargin);
        return lp;
    }

    private void refreshChips() {
        modelTxt.setText(shortModel(currentModelId));
        speedTxt.setText(lastSpeed > 0 ? String.format(Locale.US, "%.0f t/s", lastSpeed) : "— t/s");
    }

    // ---------------------------------------------------------------- 面板

    private FrameLayout openPanel(String title) {
        closePanel();
        panel = new FrameLayout(this);
        panel.setBackgroundColor(Ui.BG);
        panel.setClickable(true);
        stage.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        input.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        return panel;
    }

    private void addPanelHeader(String title) {
        TextView h = Ui.text(this, "‹  " + title, 21, Ui.SUB, Ui.SANS_M);
        h.setPadding(Ui.px(14), Ui.px(4), Ui.px(14), Ui.px(4));
        h.setOnClickListener(v -> closePanel());
        panel.addView(h, Ui.fl(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 24));
        panel.setAlpha(0f);
        panel.animate().alpha(1f).setDuration(160).start();
    }

    private void closePanel() {
        if (panel != null) { stage.removeView(panel); panel = null; }
    }

    @Override protected void handleBack() {
        if (confirmOpen()) { closeConfirm(); return; }
        if (panel != null) closePanel(); else finish();
    }

    /** 模型清单（provider:id 形式），从 /api/models 拉，失败回落内置两个。 */
    private void showModels() {
        FrameLayout p = openPanel("选择模型");
        LinearLayout list = makeList(p, null, null, 74);
        addPanelHeader("选择模型");

        Net.get(this).apiCached(this, "/api/models", (ok, value, error) -> {
            if (!ok || value == null || !value.optBoolean("ok", false)) {
                for (int i = 0; i < MODELS.length; i++) addModelRow(list, "deepseek-official",
                        MODELS[i], MODELS[i], i == model);
                return;
            }
            org.json.JSONArray groups = value.optJSONArray("groups");
            int total = 0;
            for (int g = 0; groups != null && g < groups.length(); g++) {
                org.json.JSONObject group = groups.optJSONObject(g);
                if (group == null) continue;
                String provider = group.optString("provider", "");
                org.json.JSONArray models = group.optJSONArray("models");
                for (int m = 0; models != null && m < models.length(); m++) {
                    org.json.JSONObject mm = models.optJSONObject(m);
                    if (mm == null) continue;
                    String id = mm.optString("id", "");
                    String name = mm.optString("name", id);
                    addModelRow(list, provider, id, name, id.equals(currentModelId));
                    total++;
                    if (total >= 40) return;
                }
            }
        });
    }

    private void addModelRow(LinearLayout list, String provider, String id, String name, boolean active) {
        list.addView(item(name, provider, active ? IconView.SPARKLE : IconView.BUBBLE, false, active,
                v -> {
                    currentModelId = id;
                    currentProvider = provider;
                    getSharedPreferences("li", MODE_PRIVATE).edit()
                            .putString("model_id", id).putString("model_provider", provider).apply();
                    refreshChips();
                    closePanel();
                    if ("harness".equals(mode)) applyHarnessModel();
                }));
    }

    /** harness 模式真正切模型。 */
    private void applyHarnessModel() {
        JSONObject body = new JSONObject();
        try {
            body.put("sessionId", sessionId);
            body.put("provider", currentProvider.isEmpty() ? "deepseek-official" : currentProvider);
            body.put("model", currentModelId);
        } catch (Exception ignored) { }
        Net.get(this).apiPost("/api/model", body, (ok, value, error) -> { });
    }

    private void showUsage() {
        FrameLayout p = openPanel("用量与速度");
        LinearLayout c = makePlain(p, 70);

        c.addView(label("速度"));
        LinearLayout a = card();
        a.addView(Ui.text(this, lastSpeed > 0 ? String.format(Locale.US, "%.0f", lastSpeed) + " t/s" : "— t/s",
                44, Ui.TEXT, Ui.SERIF));
        TextView cap = Ui.text(this, "输出速度 · " + MODELS[model], 18, Ui.SUB, Ui.SANS);
        cap.setSingleLine(true);
        cap.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        cp.topMargin = Ui.px(4);
        cp.bottomMargin = Ui.px(12);
        a.addView(cap, cp);
        a.addView(kv("首字延迟", lastFirstMs > 0 ? fmtSec(lastFirstMs) : "—", 120));
        a.addView(kv("本次耗时", lastDurMs > 0 ? fmtSec(lastDurMs) : "—", 120));
        c.addView(a);

        c.addView(label("本次对话"));
        LinearLayout b = card();
        b.addView(kv("输入", fmtTok(totalIn), 90));
        b.addView(kv("输出", fmtTok(totalOut), 90));
        int used = totalIn + totalOut;
        b.addView(kv("上下文", fmtTok(used), 100));
        c.addView(b);

        addPanelHeader("用量与速度");
    }

    private static String fmtTok(int n) {
        if (n >= 1000) return String.format(Locale.US, "%.1fK", n / 1000.0);
        return n == 0 ? "—" : String.valueOf(n);
    }

    private static String fmtSec(long ms) { return String.format(Locale.US, "%.1f s", ms / 1000.0); }

    private static String shortModel(String id) {
        if (id == null || id.isEmpty()) return "model";
        String v = id;
        int slash = v.lastIndexOf('/');
        if (slash >= 0) v = v.substring(slash + 1);
        v = v.replace("deepseek-", "").replace("-preview", "");
        return v.length() > 10 ? v.substring(0, 10) : v;
    }

    private static int tokens(String s) { return Math.max(1, Math.round(s.length() / 1.5f)); }

    // ---------------------------------------------------------------- 消息

    private TextView addMsg(String s, boolean user) {
        TextView t = Ui.text(this, s, 23, user ? Ui.INK : Ui.TEXT, user ? Ui.SANS : Ui.SERIF);
        t.setLineSpacing(0f, 1.12f);
        t.setPadding(Ui.px(16), Ui.px(11), Ui.px(16), Ui.px(12));
        t.setMaxWidth(Ui.px(300));
        float r = Ui.px(22), q = Ui.px(6);
        GradientDrawable g;
        if (user) {
            g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Ui.A1, Ui.A2});
            g.setCornerRadii(new float[]{r, r, r, r, q, q, r, r});
        } else {
            g = new GradientDrawable();
            g.setColor(Ui.CARD);
            g.setStroke(Math.max(1, Ui.px(1)), Ui.STROKE);
            g.setCornerRadii(new float[]{r, r, r, r, r, r, q, q});
        }
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.gravity = user ? Gravity.END : Gravity.START;
        lp.bottomMargin = Ui.px(10);
        msgs.addView(t, lp);
        Ui.rise(t, 0);
        scrollDown();
        return t;
    }

    private void scrollDown() {
        sv.post(() -> sv.smoothScrollTo(0, msgs.getBottom()));
    }

    private void send() {
        final String s = input.getText().toString().trim();
        if (s.length() == 0 || busy) return;
        busy = true;
        input.setText("");
        addMsg(s, true);
        totalIn += tokens(s);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        stage.requestFocus();

        final long t0 = SystemClock.elapsedRealtime();
        final TextView tv = addMsg("···", false);

        if ("harness".equals(mode)) {
            pendingUserText = s;
            JSONObject body = new JSONObject();
            try { body.put("sessionId", sessionId); body.put("text", s); } catch (Exception ignored) { }
            Net.get(this).apiPost("/api/send", body, (ok, value, error) -> {
                busy = false;
                if (!ok || value == null || !value.optBoolean("ok", false)) {
                    tv.setText(error == null ? "发送失败" : error);
                    return;
                }
                tv.setText("已发送，正在处理…");
                lastDurMs = SystemClock.elapsedRealtime() - t0;
                startPolling();
            });
            return;
        }

        // DeepSeek 流式
        JSONObject body = new JSONObject();
        try {
            body.put("chatId", chatId);
            body.put("text", s);
            body.put("thinking", model == 1);
        } catch (Exception ignored) { }
        final StringBuilder acc = new StringBuilder();
        Net.get(this).stream("/api/ds/send", body, new Net.Stream() {
            @Override public void onDelta(String text) {
                if (acc.length() == 0) lastFirstMs = SystemClock.elapsedRealtime() - t0;
                acc.append(text);
                tv.setText(acc.toString());
                long el = Math.max(80, SystemClock.elapsedRealtime() - t0);
                lastSpeed = acc.length() / 1.5f / (el / 1000f);
                scrollDown();
                refreshChips();
            }
            @Override public void onThink(String text) { }
            @Override public void onDone() {
                busy = false;
                totalOut += tokens(acc.toString());
                lastDurMs = SystemClock.elapsedRealtime() - t0;
                appendLocal("assistant", acc.toString());
                refreshChips();
            }
            @Override public void onError(String error) {
                busy = false;
                tv.setText(acc.length() == 0 ? "出错：" + error : acc + "\n（" + error + "）");
            }
        });
        appendLocal("user", s);
    }

    // ------------------------------------------------------------ 实时同步

    /**
     * 轮询会话，把新回复增量追加到界面。
     *
     * 之前的做法是发完消息后 1.5 秒重载一次就结束——可那时 agent 往往还没开始回，
     * 之后界面就再也不刷新，必须退出重进才能看到。现在改成一直盯着，直到会话跑完。
     */
    private void startPolling() {
        if (polling) return;
        polling = true;
        pollStart = SystemClock.elapsedRealtime();
        handler.post(pollRunnable);
    }

    private void stopPolling() {
        polling = false;
        handler.removeCallbacks(pollRunnable);
    }

    private boolean polling;
    private long pollStart;
    /**
     * 空闲时也一直轮询。
     *
     * 之前只在"自己刚发完消息"时才轮询，并且 3 分钟就放弃——于是电脑上手动
     * 发起的回合、或者超过 3 分钟的长任务，手表上永远看不到，只能退出重进。
     * 现在分两档：跑着的时候 2 秒一次，空闲时 6 秒一次，都不设上限，
     * 只在退到后台或离开页面时停。
     */
    private static final long POLL_BUSY_MS = 2000;
    private static final long POLL_IDLE_MS = 6000;

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            if (!polling || activityGone()) return;
            Net.get(ChatActivity.this).api("/api/session?id=" + enc(sessionId),
                    (ok, value, error) -> {
                        if (!polling || activityGone()) return;
                        boolean serverRunning = false;
                        if (ok && value != null && value.optBoolean("ok", false)) {
                            appendNewMessages(value.optJSONArray("messages"));
                            serverRunning = value.optBoolean("running", false);
                            if (!serverRunning) {
                                busy = false;
                                clearPlaceholder();
                                refreshChips();
                            }
                        }
                        if (polling) {
                            handler.postDelayed(pollRunnable,
                                    serverRunning ? POLL_BUSY_MS : POLL_IDLE_MS);
                        }
                    });
        }
    };

    private boolean activityGone() { return isFinishing() || isDestroyed(); }

    /** 刚发出去、界面上已经显示过的那条用户消息（防止轮询时重复渲染）。 */
    private String pendingUserText = "";

    /** 只追加界面上还没有的消息。 */
    private void appendNewMessages(JSONArray list) {
        if (list == null) return;
        if (list.length() <= shownCount) return;
        for (int i = shownCount; i < list.length(); i++) {
            JSONObject m = list.optJSONObject(i);
            if (m == null) continue;
            boolean user = "user".equals(m.optString("role", ""));
            String text = m.optString("text", "");
            if (text.isEmpty()) continue;
            // 自己刚发的那条已经在界面上，跳过，别显示两遍。
            if (user && !pendingUserText.isEmpty() && pendingUserText.equals(text.trim())) {
                pendingUserText = "";
                continue;
            }
            // 占位气泡「···」先清掉再写真实内容
            clearPlaceholder();
            addMsg(text, user);
            if (!user) totalOut += tokens(text);
        }
        shownCount = list.length();
        refreshChips();
    }

    /** 去掉等待中的占位气泡。 */
    private void clearPlaceholder() {
        for (int i = msgs.getChildCount() - 1; i >= 0; i--) {
            View child = msgs.getChildAt(i);
            if (child instanceof TextView && "···".contentEquals(((TextView) child).getText())) {
                msgs.removeViewAt(i);
                return;
            }
        }
    }

    /** 把消息写进本地记录（DeepSeek 没有历史列表接口，只能自己存）。 */
    private void appendLocal(String role, String text) {
        if (text == null || text.isEmpty()) return;
        try {
            JSONArray list = new JSONArray(getSharedPreferences("li", MODE_PRIVATE).getString("ds_msg_" + chatId, "[]"));
            JSONObject m = new JSONObject();
            m.put("role", role);
            m.put("text", text);
            list.put(m);
            getSharedPreferences("li", MODE_PRIVATE).edit().putString("ds_msg_" + chatId, list.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** 删掉当前会话，成功后退出这一页。 */
    private void deleteThisChat() {
        if ("harness".equals(mode)) {
            Net.get(this).deleteSession(sessionId, (ok, value, error) -> {
                if (isFinishing() || isDestroyed()) return;
                if (!ok || value == null || !value.optBoolean("ok", false)) {
                    android.widget.Toast.makeText(this,
                            error == null || error.isEmpty() ? "删除失败" : error,
                            android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                stopPolling();
                finish();
            });
        } else {
            // DeepSeek 会话只在本地：删记录 + 删消息体。
            getSharedPreferences("li", MODE_PRIVATE).edit()
                    .remove("ds_msg_" + chatId).apply();
            JSONArray chats = new JSONArray();
            try { chats = new JSONArray(getSharedPreferences("li", MODE_PRIVATE)
                    .getString("ds_chats", "[]")); } catch (Exception ignored) { }
            JSONArray kept = new JSONArray();
            for (int i = 0; i < chats.length(); i++) {
                JSONObject c = chats.optJSONObject(i);
                if (c == null || chatId.equals(c.optString("id", ""))) continue;
                kept.put(c);
            }
            getSharedPreferences("li", MODE_PRIVATE).edit()
                    .putString("ds_chats", kept.toString()).apply();
            finish();
        }
    }

    private static String enc(String s) {
        try { return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8").replace("+", "%20"); }
        catch (Exception e) { return s == null ? "" : s; }
    }

    @Override protected void onDestroy() {
        stopPolling();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override protected void onPause() {
        super.onPause();
        // 退到后台就不再轮询，回来时由 onResume 重新拉起。
        stopPolling();
    }

    @Override protected void onResume() {
        super.onResume();
        // harness 会话一进来就常驻轮询：不管是谁在电脑上触发的回合，都要能看见。
        if ("harness".equals(mode)) startPolling();
    }
}