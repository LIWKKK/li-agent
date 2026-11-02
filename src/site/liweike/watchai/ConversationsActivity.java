package site.liweike.watchai;

import android.content.Intent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

public class ConversationsActivity extends ListScreen {
    private String chatTitle;
    private boolean first = true;
    private String scope;
    private String workspace;
    private String projectPath;

    @Override // site.liweike.watchai.BaseActivity
    protected void build() {
        this.workspace = getIntent().getStringExtra("workspace");
        this.projectPath = getIntent().getStringExtra("path");
        this.scope = this.workspace != null ? Store.wsScope(this.workspace) : "chat";
        this.chatTitle = this.workspace != null ? this.workspace : "Chat";
        render();
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        if (!this.first) {
            render();
        } else {
            this.first = false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void render() {
        closeOverlay();
        this.stage.removeAllViews();
        startList(this.chatTitle, this.workspace != null ? "Harness" : "历史对话");
        if (this.workspace != null) {
            loadHarness();
        } else {
            renderLocal();
        }
    }

    // ---------- Harness：真实会话 ----------

    private void loadHarness() {
        this.col.addView(item("新建对话", null, 7, true, new View.OnClickListener() {
            @Override public void onClick(View view) { createSession(); }
        }));
        this.col.addView(label("会话 · 长按可删除"));
        Net.get(this).apiCached(this, "/api/sessions?project=" + enc(workspace), (ok, value, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (!ok || value == null || !value.optBoolean("ok", false)) {
                String reason = error == null || error.isEmpty() ? "读取失败" : error;
                this.col.addView(item(reason, null, 6, false, v -> render()));
                return;
            }
            JSONArray sessions = value.optJSONArray("sessions");
            int count = sessions == null ? 0 : sessions.length();
            for (int i = 0; i < count; i++) {
                JSONObject s = sessions.optJSONObject(i);
                if (s == null) continue;
                final String id = s.optString("id", "");
                final String title = s.optString("title", "未命名");
                boolean running = s.optBoolean("running", false);
                boolean waiting = s.optBoolean("waiting", false);
                String sub = waiting ? "等待回答" : running ? "运行中" : "空闲";
                String model = s.optString("model", "");
                if (!model.isEmpty()) sub += " · " + model;
                View viewItem = item(clip(title, 20), sub, 3, running, new View.OnClickListener() {
                    @Override public void onClick(View view) { openChatReal(id, title); }
                });
                viewItem.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View view) {
                        view.performHapticFeedback(0);
                        confirm("删除对话？", clip(title, 20),
                                running || waiting ? "正在运行，删除会立即中断" : "删除后无法恢复",
                                "删除", () -> deleteRemote(id));
                        return true;
                    }
                });
                this.col.addView(viewItem);
            }
            if (count == 0) this.col.addView(empty("暂无会话，点上面新建一个"));
        });
    }

    // ---------- Chat：本地会话（壳原逻辑，Store 存储） ----------

    private void renderLocal() {
        this.col.addView(item("新建对话", null, 7, true, new View.OnClickListener() {
            @Override public void onClick(View view) { newRealDsChat(); }
        }));
        this.col.addView(label("历史对话 · 长按可删除"));
        java.util.List<String[]> listConvos = Store.convos(this, this.scope);
        if (listConvos.isEmpty()) {
            this.col.addView(empty("暂无历史对话"));
        }
        for (int i = 0; i < listConvos.size(); i++) {
            final String str = listConvos.get(i)[0];
            final String chatId = listConvos.get(i).length > 2 ? listConvos.get(i)[2] : "";
            final int idx = i;
            View viewItem = item(str, listConvos.get(i)[1], 3, false, new View.OnClickListener() {
                @Override public void onClick(View view) { openChatById(chatId, str); }
            });
            viewItem.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View view) {
                    view.performHapticFeedback(0);
                    confirm("删除对话？", str, "删除后无法恢复", "删除", new Runnable() {
                        @Override public void run() {
                            Store.removeConvo(ConversationsActivity.this, ConversationsActivity.this.scope, idx);
                            render();
                        }
                    });
                    return true;
                }
            });
            this.col.addView(viewItem);
        }
    }

    // ---------- 打开与删除 ----------

    /** 新建真实 DeepSeek 会话：/api/ds/new 拿 chatId。 */
    private void newRealDsChat() {
        Net.get(this).apiPost("/api/ds/new", new JSONObject(), (ok, value, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (ok && value != null && value.optBoolean("ok", false)) {
                String chatId = value.optString("chatId", "");
                if (chatId.isEmpty()) { toast("创建失败"); return; }
                String label = "新对话 · " + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(new java.util.Date());
                Store.addConvo(this, this.scope, label, chatId);
                Intent it = new Intent(this, ChatActivity.class);
                it.putExtra("mode", "ds");
                it.putExtra("chatId", chatId);
                it.putExtra("title", "Chat");
                startActivity(it);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            } else {
                toast(error == null || error.isEmpty() ? "创建失败（未登录？）" : error);
            }
        });
    }

    /** 打开有真实 chatId 的本地历史对话。 */
    private void openChatById(String chatId, String label) {
        if (chatId == null || chatId.isEmpty()) {
            toast("这条记录没有会话 ID，请删除后重新新建");
            return;
        }
        Intent it = new Intent(this, ChatActivity.class);
        it.putExtra("mode", "ds");
        it.putExtra("chatId", chatId);
        it.putExtra("title", chatTitle);
        it.putExtra("welcome", "已打开「" + label + "」。");
        startActivity(it);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void openChatReal(String sessionId, String title) {
        // Harness：真实会话
        Intent intent = new Intent(this, (Class<?>) ChatActivity.class);
        intent.putExtra("mode", "harness");
        intent.putExtra("sessionId", sessionId);
        intent.putExtra("title", title);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /** 新建 Harness 会话（真实 /api/create）。 */
    private void createSession() {
        JSONObject body = new JSONObject();
        try { if (projectPath != null && !projectPath.isEmpty()) body.put("projectPath", projectPath); } catch (Exception ignored) { }
        Net.get(this).apiPost("/api/create", body, (ok, value, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (ok && value != null && value.optBoolean("ok", false)) {
                String id = value.optString("sessionId", "");
                if (!id.isEmpty()) openChatReal(id, "新对话");
            } else {
                toast(error == null || error.isEmpty() ? "创建失败" : error);
            }
        });
    }

    private void deleteRemote(String id) {
        Net.get(this).deleteSession(id, (ok, value, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (!ok || value == null || !value.optBoolean("ok", false)) {
                toast(error == null || error.isEmpty() ? "删除失败" : error);
                return;
            }
            Cache.drop(this, "/api/sessions?project=" + enc(workspace));
            render();
        });
    }

    private void toast(String message) {
        android.widget.Toast.makeText(this, message == null ? "操作失败" : message,
                android.widget.Toast.LENGTH_SHORT).show();
    }

    private static String clip(String s, int n) {
        String v = s == null ? "" : s.trim();
        return v.length() <= n ? v : v.substring(0, n) + "…";
    }

    private static String enc(String s) {
        try { return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8").replace("+", "%20"); }
        catch (Exception e) { return s == null ? "" : s; }
    }
}