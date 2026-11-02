package site.liweike.watchai;

import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;
import java.util.Random;

/* JADX INFO: loaded from: classes.dex */
public class ChatActivity extends ListScreen {
    static final int CTX_MAX = 64000;
    private boolean busy;
    private EditText input;
    private boolean isNew;
    private long lastDurMs;
    private long lastFirstMs;
    private float lastSpeed;
    private int model;
    private TextView modelTxt;
    private LinearLayout msgs;
    private FrameLayout panel;
    private boolean saved;
    private String scope;
    private String mode = "ds";
    private String sessionId = "";
    private String chatId = "";
    private int shownCount;
    private String pendingUserText = "";
    private boolean polling;
    private long pollStart;
    private static final long POLL_BUSY_MS = 2000;
    private static final long POLL_IDLE_MS = 6000;
    private IconView send;
    private TextView speedTxt;
    private ScrollView sv;
    private int totalIn;
    private int totalOut;
    // 真实 DSH 模型目录：从 /api/models 拉取，不再内置假模型
    static final String[] FALLBACK_MODELS = {"glm-5.3-flash", "glm-5.3"};
    static final String[] FALLBACK_SHORT = {"glm-flash", "glm"};
    static final String[] FALLBACK_DESC = {"CodeBuddy · GLM-5.3-Flash", "CodeBuddy · GLM-5.3"};
    String[] modelIds = FALLBACK_MODELS;
    String[] modelShorts = FALLBACK_SHORT;
    String[] modelDescs = FALLBACK_DESC;
    String[] modelProviders = {"codebuddy", "codebuddy"};
    String[] modelEfforts = {"high", "high"};
    String currentProvider = "";
    String currentModel = "";
    String currentEffort = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();

    static /* synthetic */ int access$1312(ChatActivity chatActivity, int i) {
        int i2 = chatActivity.totalOut + i;
        chatActivity.totalOut = i2;
        return i2;
    }

    @Override // site.liweike.watchai.BaseActivity
    protected void build() {
        this.model = getSharedPreferences("li", 0).getInt("model", 0);
        if (this.model < 0) {
            this.model = 0;
        }
        this.scope = getIntent().getStringExtra("scope");
        this.mode = getIntent().getStringExtra("mode");
        this.sessionId = getIntent().getStringExtra("sessionId");
        this.chatId = getIntent().getStringExtra("chatId");
        if (this.mode == null) this.mode = "ds";
        this.isNew = getIntent().getBooleanExtra("isNew", false);
        String stringExtra = getIntent().getStringExtra("title");
        if (stringExtra == null) {
            stringExtra = "Chat";
        }
        TextView textViewText = Ui.text(this, "‹  " + stringExtra, 21.0f, -6514030, "sans-serif-medium");
        textViewText.setSingleLine(true);
        textViewText.setEllipsize(TextUtils.TruncateAt.END);
        textViewText.setMaxWidth(Ui.px(230.0f));
        textViewText.setPadding(Ui.px(14.0f), Ui.px(4.0f), Ui.px(14.0f), Ui.px(4.0f));
        textViewText.setOnClickListener(new View.OnClickListener() { // from class: site.liweike.watchai.ChatActivity.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                ChatActivity.this.finish();
            }
        });
        this.stage.addView(textViewText, Ui.fl(-2.0f, -2.0f, 49, 0.0f, 24.0f));
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        LinearLayout linearLayoutChip = chip();
        this.modelTxt = Ui.text(this, "harness".equals(this.mode) ? "model" : (this.model == 1 ? "R1" : "V3"), 19.0f, -658195, "sans-serif-medium");
        linearLayoutChip.addView(this.modelTxt);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Ui.px(16.0f), Ui.px(16.0f));
        layoutParams.leftMargin = Ui.px(4.0f);
        linearLayoutChip.addView(new IconView(this, 8, -6514030), layoutParams);
        linearLayoutChip.setOnClickListener(new View.OnClickListener() { // from class: site.liweike.watchai.ChatActivity.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                ChatActivity.this.showModels();
            }
        });
        linearLayout.addView(linearLayoutChip, chipLp(8));
        LinearLayout linearLayoutChip2 = chip();
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(Ui.px(16.0f), Ui.px(16.0f));
        layoutParams2.rightMargin = Ui.px(5.0f);
        linearLayoutChip2.addView(new IconView(this, 9, -2525353), layoutParams2);
        this.speedTxt = Ui.text(this, "— t/s", 19.0f, -658195, "sans-serif-medium");
        linearLayoutChip2.addView(this.speedTxt);
        linearLayoutChip2.setOnClickListener(new View.OnClickListener() { // from class: site.liweike.watchai.ChatActivity.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                ChatActivity.this.showUsage();
            }
        });
        linearLayout.addView(linearLayoutChip2, chipLp(0));
        this.stage.addView(linearLayout, Ui.fl(-2.0f, -2.0f, 49, 0.0f, 62.0f));
        this.sv = new ScrollView(this);
        this.sv.setVerticalScrollBarEnabled(false);
        this.sv.setOverScrollMode(2);
        this.sv.setVerticalFadingEdgeEnabled(true);
        this.sv.setFadingEdgeLength(Ui.px(44.0f));
        this.msgs = new LinearLayout(this);
        this.msgs.setOrientation(1);
        this.msgs.setPadding(Ui.px(64.0f), Ui.px(30.0f), Ui.px(64.0f), Ui.px(22.0f));
        this.sv.addView(this.msgs, new FrameLayout.LayoutParams(-1, -2));
        this.stage.addView(this.sv, Ui.fl(-1.0f, 258.0f, 51, 0.0f, 98.0f));
        this.input = new EditText(this);
        this.input.setHint("说点什么…");
        this.input.setHintTextColor(-9474713);
        this.input.setTextColor(-658195);
        this.input.setTextSize(0, 22.0f * Ui.u);
        this.input.setBackground(Ui.card(26.0f));
        this.input.setPadding(Ui.px(22.0f), 0, Ui.px(18.0f), 0);
        this.input.setGravity(16);
        this.input.setSingleLine(true);
        this.input.setImeOptions(268435460);
        this.input.setOnEditorActionListener(new TextView.OnEditorActionListener() { // from class: site.liweike.watchai.ChatActivity.4
            @Override // android.widget.TextView.OnEditorActionListener
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i != 4) {
                    return false;
                }
                ChatActivity.this.send();
                return true;
            }
        });
        this.stage.addView(this.input, Ui.fl(232.0f, 52.0f, 51, 89.0f, 364.0f));
        this.send = new IconView(this, 4, -14737891);
        this.send.setPadding(Ui.px(12.0f), Ui.px(12.0f), Ui.px(12.0f), Ui.px(12.0f));
        this.send.setBackground(Ui.accentCircle());
        this.send.setAlpha(0.35f);
        this.send.setClickable(true);
        this.send.setOnClickListener(new View.OnClickListener() { // from class: site.liweike.watchai.ChatActivity.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                ChatActivity.this.send();
            }
        });
        Ui.pressable(this.send);
        this.stage.addView(this.send, Ui.fl(48.0f, 48.0f, 51, 329.0f, 366.0f));
        this.input.addTextChangedListener(new TextWatcher() { // from class: site.liweike.watchai.ChatActivity.6
            @Override // android.text.TextWatcher
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override // android.text.TextWatcher
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override // android.text.TextWatcher
            public void afterTextChanged(Editable editable) {
                ChatActivity.this.send.animate().alpha(editable.toString().trim().length() > 0 ? 1.0f : 0.35f).setDuration(120L).start();
            }
        });
        String welcome = getIntent().getStringExtra("welcome");
        if ("harness".equals(this.mode)) {
            // Harness：先全量拉一次会话消息渲染，再进入常驻轮询（增量追加）
            Net.get(this).api("/api/session?id=" + enc(this.sessionId), (ok, value, error) -> {
                if (isFinishing() || isDestroyed()) return;
                if (ok && value != null && value.optBoolean("ok", false)) {
                    JSONArray list = value.optJSONArray("messages");
                    int n = list == null ? 0 : list.length();
                    for (int i = 0; i < n; i++) {
                        JSONObject m = list.optJSONObject(i);
                        if (m == null) continue;
                        String t = m.optString("text", "");
                        if (!t.isEmpty()) addMsg(t, "user".equals(m.optString("role", "")));
                    }
                    this.shownCount = n;
                    scrollDown();
                    startPolling();
                } else {
                    addMsg(error == null || error.isEmpty() ? "读取会话失败" : error, false);
                }
            });
        } else {
            int historyCount = loadHistory();
            if (historyCount == 0) {
                addMsg(welcome == null ? "有什么可以帮你的吗？" : welcome, false);
            }
        }
    }

    private LinearLayout chip() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        linearLayout.setPadding(Ui.px(14.0f), 0, Ui.px(12.0f), 0);
        linearLayout.setBackground(Ui.card(17.0f));
        linearLayout.setClickable(true);
        Ui.pressable(linearLayout);
        return linearLayout;
    }

    private LinearLayout.LayoutParams chipLp(int i) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, Ui.px(34.0f));
        layoutParams.rightMargin = Ui.px(i);
        return layoutParams;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refreshChips() {
        if (!"harness".equals(this.mode)) {
            this.modelTxt.setText(this.model == 1 ? "R1" : "V3");
        } else {
            this.modelTxt.setText(modelShorts.length > 0 ? modelShorts[Math.min(this.model, modelShorts.length - 1)] : "model");
        }
        this.speedTxt.setText(this.lastSpeed > 0.0f ? String.format(Locale.US, "%.0f t/s", Float.valueOf(this.lastSpeed)) : "— t/s");
    }

    private FrameLayout openPanel(String str) {
        closePanel();
        this.panel = new FrameLayout(this);
        this.panel.setBackgroundColor(-14737891);
        this.panel.setClickable(true);
        this.stage.addView(this.panel, new FrameLayout.LayoutParams(-1, -1));
        this.input.clearFocus();
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService("input_method");
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(this.input.getWindowToken(), 0);
        }
        return this.panel;
    }

    private void addPanelHeader(String str) {
        TextView textViewText = Ui.text(this, "‹  " + str, 21.0f, -6514030, "sans-serif-medium");
        textViewText.setPadding(Ui.px(14.0f), Ui.px(4.0f), Ui.px(14.0f), Ui.px(4.0f));
        textViewText.setOnClickListener(new View.OnClickListener() { // from class: site.liweike.watchai.ChatActivity.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                ChatActivity.this.closePanel();
            }
        });
        this.panel.addView(textViewText, Ui.fl(-2.0f, -2.0f, 49, 0.0f, 24.0f));
        this.panel.setAlpha(0.0f);
        this.panel.animate().alpha(1.0f).setDuration(160L).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void closePanel() {
        if (this.panel != null) {
            this.stage.removeView(this.panel);
            this.panel = null;
        }
    }

    @Override // site.liweike.watchai.ListScreen, site.liweike.watchai.BaseActivity
    protected void handleBack() {
        if (this.panel == null) {
            finish();
        } else {
            closePanel();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showModels() {
        LinearLayout list = makeList(openPanel("选择模型"), null, null, 74);
        addPanelHeader("选择模型");
        if (!"harness".equals(this.mode)) {
            renderDsModelList(list);
            return;
        }
        renderModelList(list);
        String qs = "harness".equals(this.mode) && this.sessionId.length() > 0 ? "?sessionId=" + this.sessionId : "";
        Net.get(this).api("/api/models" + qs, (ok, value, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (!ok || value == null) return;
            java.util.List<String> ids = new java.util.ArrayList<>();
            java.util.List<String> shorts = new java.util.ArrayList<>();
            java.util.List<String> descs = new java.util.ArrayList<>();
            java.util.List<String> providers = new java.util.ArrayList<>();
            java.util.List<String> efforts = new java.util.ArrayList<>();
            JSONArray groups = value.optJSONArray("groups");
            int gcount = groups == null ? 0 : groups.length();
            for (int g = 0; g < gcount; g++) {
                JSONObject grp = groups.optJSONObject(g);
                if (grp == null) continue;
                String provider = grp.optString("provider", "");
                String gname = grp.optString("name", provider);
                JSONArray models = grp.optJSONArray("models");
                int mcount = models == null ? 0 : models.length();
                for (int m = 0; m < mcount; m++) {
                    JSONObject mo = models.optJSONObject(m);
                    if (mo == null) continue;
                    String id = mo.optString("id", "");
                    if (id.isEmpty()) continue;
                    ids.add(id);
                    providers.add(provider);
                    String shortName = mo.optString("name", id);
                    if (shortName.length() > 18) shortName = shortName.substring(0, 18) + "…";
                    shorts.add(shortName);
                    descs.add(gname);
                    JSONArray ef = mo.optJSONArray("efforts");
                    String def = mo.optString("defaultEffort", "");
                    efforts.add(!def.isEmpty() ? def : (ef != null && ef.length() > 0 ? ef.optString(0, "high") : "high"));
                }
            }
            if (ids.isEmpty()) return;
            modelIds = ids.toArray(new String[0]);
            modelShorts = shorts.toArray(new String[0]);
            modelDescs = descs.toArray(new String[0]);
            modelProviders = providers.toArray(new String[0]);
            modelEfforts = efforts.toArray(new String[0]);
            JSONObject cur = value.optJSONObject("current");
            if (cur != null) {
                this.currentProvider = cur.optString("provider", "");
                this.currentModel = cur.optString("model", "");
                this.currentEffort = cur.optString("effort", "");
                for (int i = 0; i < modelIds.length; i++) {
                    if (modelIds[i].equals(this.currentModel) && modelProviders[i].equals(this.currentProvider)) {
                        this.model = i;
                        break;
                    }
                }
            }
            list.removeAllViews();
            renderModelList(list);
        });
    }

    /** Chat 模式的真实模型选项（来自 /api/ds/status，网页版只有这两个）。 */
    private void renderDsModelList(LinearLayout list) {
        Net.get(this).api("/api/ds/status", (ok, value, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (!ok || value == null) return;
            JSONArray models = value.optJSONArray("models");
            int n = models == null ? 0 : models.length();
            if (n == 0) return;
            String[] ids = new String[n];
            String[] names = new String[n];
            String[] descs = new String[n];
            for (int i = 0; i < n; i++) {
                JSONObject m = models.optJSONObject(i);
                if (m == null) continue;
                ids[i] = m.optString("id", "");
                names[i] = m.optString("name", ids[i]);
                descs[i] = "deepseek-reasoner".equals(ids[i]) ? "联网 · 深度思考" : "联网 · 快速回答";
            }
            for (int i = 0; i < n; i++) {
                final int idx = i;
                boolean current = ("deepseek-reasoner".equals(ids[i])) == (this.model == 1);
                list.addView(item(names[i], descs[i], current ? 3 : 2, current, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        ChatActivity.this.model = idx;
                        ChatActivity.this.getSharedPreferences("li", 0).edit().putInt("model", idx).apply();
                        refreshChips();
                        closePanel();
                    }
                }));
            }
        });
    }

    private void renderModelList(LinearLayout list) {
        for (int i = 0; i < modelIds.length; i++) {
            final int idx = i;
            boolean current = modelIds[i].equals(this.currentModel) && modelProviders[i].equals(this.currentProvider);
            list.addView(item(modelShorts[i], modelDescs[i], current ? 3 : 2, current, new View.OnClickListener() {
                @Override public void onClick(View view) { selectModel(idx); }
            }));
        }
    }

    private void selectModel(int idx) {
        this.model = idx;
        getSharedPreferences("li", 0).edit().putInt("model", idx).apply();
        refreshChips();
        closePanel();
        if (!"harness".equals(this.mode) || this.sessionId.isEmpty()) return;
        JSONObject body = new JSONObject();
        try {
            body.put("sessionId", this.sessionId);
            body.put("provider", modelProviders[idx]);
            body.put("model", modelIds[idx]);
            body.put("effort", modelEfforts[idx]);
        } catch (Exception ignored) { }
        Net.get(this).apiPost("/api/model", body, (ok, value, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (ok && value != null && value.optBoolean("ok", false)) {
                this.currentModel = value.optString("model", modelIds[idx]);
                this.currentProvider = value.optString("provider", "");
                toast("已切换 " + modelShorts[idx]);
            } else {
                toast(error == null || error.isEmpty() ? "切换失败" : error);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showUsage() {
        LinearLayout linearLayoutMakePlain = makePlain(openPanel("用量与速度"), 70);
        linearLayoutMakePlain.addView(label("速度"));
        LinearLayout linearLayoutCard = card();
        linearLayoutCard.addView(Ui.text(this, this.lastSpeed > 0.0f ? String.format(Locale.US, "%.0f", Float.valueOf(this.lastSpeed)) + " t/s" : "— t/s", 44.0f, -658195, "serif"));
        TextView textViewText = Ui.text(this, "输出速度 · " + modelIds[Math.min(this.model, modelIds.length - 1)], 18.0f, -6514030, "sans-serif");
        textViewText.setSingleLine(true);
        textViewText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
        layoutParams.topMargin = Ui.px(4.0f);
        layoutParams.bottomMargin = Ui.px(12.0f);
        linearLayoutCard.addView(textViewText, layoutParams);
        linearLayoutCard.addView(kv("首字延迟", this.lastFirstMs > 0 ? fmtSec(this.lastFirstMs) : "—", 120));
        linearLayoutCard.addView(kv("本次耗时", this.lastDurMs > 0 ? fmtSec(this.lastDurMs) : "—", 120));
        linearLayoutMakePlain.addView(linearLayoutCard);
        linearLayoutMakePlain.addView(label("本次对话"));
        LinearLayout linearLayoutCard2 = card();
        linearLayoutCard2.addView(kv("输入", fmtTok(this.totalIn), 90));
        linearLayoutCard2.addView(kv("输出", fmtTok(this.totalOut), 90));
        int i = this.totalIn + this.totalOut;
        linearLayoutCard2.addView(kv("上下文", fmtTok(i) + " / 64K", 100));
        BarView barView = new BarView(this).set(new float[]{Math.min(1.0f, i / 64000.0f)}, new int[]{-2525353});
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, Ui.px(8.0f));
        layoutParams2.bottomMargin = Ui.px(14.0f);
        linearLayoutCard2.addView(barView, layoutParams2);
        linearLayoutMakePlain.addView(linearLayoutCard2);
        linearLayoutMakePlain.addView(label("本月"));
        LinearLayout linearLayoutCard3 = card();
        linearLayoutCard3.addView(kv("已用", "1.24M", 90));
        linearLayoutCard3.addView(kv("今日", "38.2K", 90));
        linearLayoutMakePlain.addView(linearLayoutCard3);
        linearLayoutMakePlain.addView(Ui.text(this, "演示数据", 17.0f, -6514030, "sans-serif"));
        addPanelHeader("用量与速度");
    }

    private static String fmtTok(int i) {
        if (i >= 1000) {
            return String.format(Locale.US, "%.1fK", Double.valueOf(((double) i) / 1000.0d));
        }
        return i == 0 ? "—" : String.valueOf(i);
    }

    private static String fmtSec(long j) {
        return String.format(Locale.US, "%.1f s", Double.valueOf(j / 1000.0d));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int tokens(String str) {
        return Math.max(1, Math.round(str.length() / 1.5f));
    }

    private TextView addMsg(String str, boolean z) {
        GradientDrawable gradientDrawable;
        TextView textViewText = Ui.text(this, str, 23.0f, z ? -14737891 : -658195, z ? "sans-serif" : "serif");
        textViewText.setLineSpacing(0.0f, 1.12f);
        textViewText.setPadding(Ui.px(16.0f), Ui.px(11.0f), Ui.px(16.0f), Ui.px(12.0f));
        textViewText.setMaxWidth(Ui.px(300.0f));
        float fPx = Ui.px(22.0f);
        float fPx2 = Ui.px(6.0f);
        if (z) {
            gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{-2525353, -1863569});
            gradientDrawable.setCornerRadii(new float[]{fPx, fPx, fPx, fPx, fPx2, fPx2, fPx, fPx});
        } else {
            gradientDrawable = new GradientDrawable();
            gradientDrawable.setColor(-13948376);
            gradientDrawable.setStroke(Math.max(1, Ui.px(1.0f)), -12961739);
            gradientDrawable.setCornerRadii(new float[]{fPx, fPx, fPx, fPx, fPx, fPx, fPx2, fPx2});
        }
        textViewText.setBackground(gradientDrawable);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
        layoutParams.gravity = z ? 8388613 : 8388611;
        layoutParams.bottomMargin = Ui.px(10.0f);
        this.msgs.addView(textViewText, layoutParams);
        Ui.rise(textViewText, 0);
        scrollDown();
        return textViewText;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void scrollDown() {
        this.sv.post(new Runnable() { // from class: site.liweike.watchai.ChatActivity.9
            @Override // java.lang.Runnable
            public void run() {
                ChatActivity.this.sv.smoothScrollTo(0, ChatActivity.this.msgs.getBottom());
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void send() {
        String strTrim = this.input.getText().toString().trim();
        if (strTrim.length() != 0 && !this.busy) {
            this.busy = true;
            this.input.setText("");
            addMsg(strTrim, true);
            this.totalIn += tokens(strTrim);
            if (this.isNew && !this.saved && this.scope != null) {
                String str = this.scope;
                if (strTrim.length() > 12) {
                    strTrim = strTrim.substring(0, 12) + "…";
                }
                Store.addConvo(this, str, strTrim);
                this.saved = true;
            }
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService("input_method");
            if (inputMethodManager != null) {
                inputMethodManager.hideSoftInputFromWindow(this.input.getWindowToken(), 0);
            }
            this.stage.requestFocus();
            if ("harness".equals(this.mode)) {
            final String text = strTrim;
            this.pendingUserText = text;
            JSONObject body = new JSONObject();
            try { body.put("sessionId", this.sessionId); body.put("text", text); } catch (Exception ignored) { }
            final long t0 = SystemClock.elapsedRealtime();
            final TextView tv = addMsg("已发送，等待回复…", false);
            Net.get(this).apiPost("/api/send", body, (ok, value, error) -> {
                if (isFinishing() || isDestroyed()) return;
                this.busy = false;
                if (!ok || value == null || !value.optBoolean("ok", false)) {
                    tv.setText(error == null || error.isEmpty() ? "发送失败" : error);
                    return;
                }
                tv.setText("已发送，正在处理…");
                this.lastDurMs = SystemClock.elapsedRealtime() - t0;
                startPolling();
            });
        } else {
            JSONObject body = new JSONObject();
            try {
                body.put("chatId", this.chatId);
                body.put("text", strTrim);
                body.put("thinking", this.model == 1);
                body.put("search", true);
            } catch (Exception ignored) { }
            final long t0 = SystemClock.elapsedRealtime();
            final String text = strTrim;
            final TextView tv = addMsg(this.model == 1 ? "正在思考…" : "···", false);
            final StringBuilder acc = new StringBuilder();
            Net.get(this).stream("/api/ds/send", body, new Net.Stream() {
                @Override public void onDelta(String delta) {
                    if (acc.length() == 0) {
                        ChatActivity.this.lastFirstMs = SystemClock.elapsedRealtime() - t0;
                        if ("正在思考…".contentEquals(tv.getText())) tv.setText("");
                    }
                    acc.append(delta);
                    tv.setText(acc.toString());
                    long el = Math.max(80L, SystemClock.elapsedRealtime() - t0);
                    ChatActivity.this.lastSpeed = (acc.length() / 1.5f) / (el / 1000.0f);
                    ChatActivity.this.scrollDown();
                    ChatActivity.this.refreshChips();
                }
                @Override public void onThink(String think) {
                    if (acc.length() == 0 && tv.length() == 0) {
                        tv.setText("正在思考…");
                        ChatActivity.this.scrollDown();
                    }
                }
                @Override public void onDone() {
                    ChatActivity.this.busy = false;
                    ChatActivity.this.totalOut += ChatActivity.tokens(acc.toString());
                    ChatActivity.this.lastDurMs = SystemClock.elapsedRealtime() - t0;
                    ChatActivity.this.appendLocal("user", text);
                    ChatActivity.this.appendLocal("assistant", acc.toString());
                    ChatActivity.this.refreshChips();
                }
                @Override public void onError(String error) {
                    ChatActivity.this.busy = false;
                    tv.setText(acc.length() == 0 ? "出错：" + error : ((Object) acc) + "\n（" + error + "）");
                }
            });
        }
    }
    }

    private void startPolling() {
        if (this.polling) return;
        this.polling = true;
        this.pollStart = SystemClock.elapsedRealtime();
        this.handler.post(this.pollRunnable);
    }

    private void stopPolling() {
        this.polling = false;
        this.handler.removeCallbacks(this.pollRunnable);
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            if (!ChatActivity.this.polling || ChatActivity.this.isFinishing()) return;
            Net.get(ChatActivity.this).api("/api/session?id=" + ChatActivity.this.sessionId,
                    (ok, value, error) -> {
                        if (!ChatActivity.this.polling || ChatActivity.this.isFinishing()) return;
                        boolean runningNow = false;
                        if (ok && value != null && value.optBoolean("ok", false)) {
                            appendNewMessages(value.optJSONArray("messages"));
                            runningNow = value.optBoolean("running", false);
                            if (!runningNow) {
                                ChatActivity.this.busy = false;
                                ChatActivity.this.clearPlaceholder();
                                ChatActivity.this.refreshChips();
                            }
                        }
                        if (ChatActivity.this.polling) {
                            ChatActivity.this.handler.postDelayed(this,
                                    runningNow ? POLL_BUSY_MS : POLL_IDLE_MS);
                        }
                    });
        }
    };

    private void appendNewMessages(JSONArray list) {
        if (list == null || list.length() <= this.shownCount) return;
        for (int i = this.shownCount; i < list.length(); i++) {
            JSONObject m = list.optJSONObject(i);
            if (m == null) continue;
            boolean user = "user".equals(m.optString("role", ""));
            String text = m.optString("text", "");
            if (text.isEmpty()) continue;
            if (user && !this.pendingUserText.isEmpty() && this.pendingUserText.equals(text.trim())) {
                this.pendingUserText = "";
                continue;
            }
            clearPlaceholder();
            addMsg(text, user);
            if (!user) this.totalOut += tokens(text);
        }
        this.shownCount = list.length();
        refreshChips();
    }

    private void clearPlaceholder() {
        for (int i = this.msgs.getChildCount() - 1; i >= 0; i--) {
            View child = this.msgs.getChildAt(i);
            if (child instanceof TextView && ("···".contentEquals(((TextView) child).getText())
                    || "正在思考…".contentEquals(((TextView) child).getText())
                    || "已发送，正在处理…".contentEquals(((TextView) child).getText()))) {
                this.msgs.removeViewAt(i);
                return;
            }
        }
    }

    /** 消息写入本地（ds 模式），key 是 chatId。 */
    private void appendLocal(String role, String text) {
        if (text == null || text.isEmpty() || chatId.isEmpty()) return;
        try {
            JSONArray list = new JSONArray(getSharedPreferences("li", 0)
                    .getString("ds_msg_" + chatId, "[]"));
            JSONObject m = new JSONObject();
            m.put("role", role);
            m.put("text", text);
            list.put(m);
            getSharedPreferences("li", 0).edit().putString("ds_msg_" + chatId, list.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** 加载本地历史消息，返回条数。 */
    private int loadHistory() {
        if (chatId.isEmpty()) return 0;
        try {
            JSONArray list = new JSONArray(getSharedPreferences("li", 0)
                    .getString("ds_msg_" + chatId, "[]"));
            for (int i = 0; i < list.length(); i++) {
                JSONObject m = list.optJSONObject(i);
                if (m == null) continue;
                String text = m.optString("text", "");
                if (!text.isEmpty()) addMsg(text, "user".equals(m.optString("role", "")));
            }
            return list.length();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String enc(String s) {
        try { return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8").replace("+", "%20"); }
        catch (Exception e) { return s == null ? "" : s; }
    }

    private void toast(String message) {
        android.widget.Toast.makeText(this, message == null ? "操作失败" : message,
                android.widget.Toast.LENGTH_SHORT).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void stream(final TextView textView, final String str, final float f, final long j, final long j2) {
        final long jElapsedRealtime = SystemClock.elapsedRealtime();
        final int[] iArr = {0};
        this.handler.post(new Runnable() { // from class: site.liweike.watchai.ChatActivity.11
            @Override // java.lang.Runnable
            public void run() {
                iArr[0] = Math.min(str.length(), Math.max(1, Math.round(f * 1.5f * 0.08f)) + iArr[0]);
                textView.setText(str.substring(0, iArr[0]));
                ChatActivity.this.scrollDown();
                long jElapsedRealtime2 = SystemClock.elapsedRealtime();
                ChatActivity.this.lastSpeed = (iArr[0] / 1.5f) / Math.max(0.08f, (jElapsedRealtime2 - jElapsedRealtime) / 1000.0f);
                if (iArr[0] < str.length()) {
                    ChatActivity.this.refreshChips();
                    ChatActivity.this.handler.postDelayed(this, 80L);
                    return;
                }
                ChatActivity.access$1312(ChatActivity.this, ChatActivity.tokens(str));
                ChatActivity.this.lastFirstMs = j2;
                ChatActivity.this.lastDurMs = jElapsedRealtime2 - j;
                ChatActivity.this.busy = false;
                ChatActivity.this.refreshChips();
            }
        });
    }

    @Override protected void onDestroy() {
        stopPolling();
        this.handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

        @Override protected void onPause() {
        super.onPause();
        stopPolling();
    }

    @Override protected void onResume() {
        super.onResume();
        if ("harness".equals(this.mode)) startPolling();
    }

}