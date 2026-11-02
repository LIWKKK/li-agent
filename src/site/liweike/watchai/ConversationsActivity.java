package site.liweike.watchai;

import android.content.Intent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 对话列表。带 workspace 时是 Harness 项目下的会话，否则是 DeepSeek Chat 的历史。
 *
 * 两个页面行为都在这里：
 *   - 每 5 秒静默刷新，运行状态和标题不用退出重进
 *   - 长按一行弹出删除确认，确认后调中继 /api/delete
 *     （DSH 内核没有 session/delete，真正删除由 dsh-session-manager 插件完成）
 */
public class ConversationsActivity extends ListScreen {
    private boolean harness;
    private String project = "";
    private String projectPath = "";

    private Ticker ticker;
    private boolean loading;
    /** 上一次渲染的会话 id 拼接串；一样就不重画，避免每 5 秒闪一次。 */
    private String renderedKey = "";

    @Override protected void build() {
        final String ws = getIntent().getStringExtra("workspace");
        harness = ws != null;
        project = harness ? ws : "";
        projectPath = harness ? getIntent().getStringExtra("path") : "";
        startList(harness ? ws : "DeepSeek Chat", harness ? "the coding agent" : "历史对话");

        ticker = new Ticker(5_000, this::refresh);
    }

    @Override protected void onResume() {
        super.onResume();
        ticker.start();
    }

    @Override protected void onPause() {
        super.onPause();
        ticker.stop();
        closeConfirm();
    }

    @Override protected void handleBack() {
        if (confirmOpen()) { closeConfirm(); return; }
        super.handleBack();
    }

    // ---------------------------------------------------------------- 列表

    private void refresh() {
        if (loading) return;
        loading = true;
        if (harness) loadSessions(); else loadDsChats();
    }

    private void loadSessions() {
        Net.get(this).apiCached(this, "/api/sessions?project=" + enc(project), (ok, value, error) -> {
            loading = false;
            if (isFinishing() || isDestroyed()) return;
            if (!ok || value == null || !value.optBoolean("ok", false)) {
                if (renderedKey.isEmpty()) {
                    render(null, error == null ? "读取失败" : error);
                }
                return;
            }
            render(value.optJSONArray("sessions"), null);
        });
    }

    /** sessions 非空时渲染列表；为空且 error 非空时渲染错误行。 */
    private void render(JSONArray sessions, String error) {
        String key = keyOf(sessions, error);
        if (key.equals(renderedKey)) return;
        renderedKey = key;

        col.removeAllViews();
        if (!harness) {
            col.addView(actionRow("新建对话", IconView.PLUS, new View.OnClickListener() {
                @Override public void onClick(View v) { newDsChat(); }
            }));
            col.addView(label("历史对话"));
        } else {
            col.addView(actionRow("新建对话", IconView.PLUS, new View.OnClickListener() {
                @Override public void onClick(View v) { createSession(projectPath); }
            }));
            col.addView(label("会话"));
        }

        if (error != null) {
            col.addView(item(error, null, IconView.INFO, false, null));
            return;
        }
        if (sessions == null || sessions.length() == 0) {
            col.addView(item("还没有会话", "点上面新建一个", IconView.BUBBLE, false, null));
            return;
        }
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject s = sessions.optJSONObject(i);
            if (s == null) continue;
            col.addView(sessionRow(s));
        }
    }

    private View sessionRow(JSONObject s) {
        final String id = s.optString("id", "");
        final String title = s.optString("title", "未命名");
        final boolean running = s.optBoolean("running", false);
        final boolean waiting = s.optBoolean("waiting", false);
        String sub = waiting ? "等待回答" : running ? "运行中" : "空闲";
        String model = s.optString("model", "");
        if (!model.isEmpty()) sub += " · " + model;

        View row = item(clip(title, 18), sub, IconView.BUBBLE, running,
                v -> openHarnessChat(id, title));
        Ui.longPress(row, () -> askDelete(id, title, running || waiting, true));
        return row;
    }

    private void loadDsChats() {
        Net.get(this).apiCached(this, "/api/ds/status", (ok, value, error) -> {
            loading = false;
            if (isFinishing() || isDestroyed()) return;
            boolean loggedIn = ok && value != null && value.optBoolean("loggedIn", false);
            JSONArray chats = localChats();

            // DeepSeek 会话只存在本地，网络只决定"离线"标记，key 里带上它。
            String key = "ds:" + loggedIn + ":" + chats.toString();
            if (key.equals(renderedKey)) return;
            renderedKey = key;

            col.removeAllViews();
            col.addView(actionRow("新建对话", IconView.PLUS, new View.OnClickListener() {
                @Override public void onClick(View v) { newDsChat(); }
            }));
            col.addView(label("历史对话"));

            if (chats.length() == 0) {
                col.addView(item(loggedIn ? "还没有对话" : "未登录 DeepSeek",
                        loggedIn ? "点上面新建一个" : "在中继上粘贴 userToken",
                        IconView.BUBBLE, false, null));
                return;
            }
            for (int i = 0; i < chats.length(); i++) {
                JSONObject c = chats.optJSONObject(i);
                if (c == null) continue;
                final String id = c.optString("id", "");
                final String title = c.optString("title", "未命名");
                View row = item(clip(title, 18), loggedIn ? "" : "离线", IconView.BUBBLE, false,
                        v -> openDsChat(id, title));
                // 本地记录直接删，不碰服务器。
                Ui.longPress(row, () -> askDelete(id, title, false, false));
                col.addView(row);
            }
        });
    }

    private String keyOf(JSONArray sessions, String error) {
        if (error != null) return "err:" + error;
        if (sessions == null) return "empty";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject s = sessions.optJSONObject(i);
            if (s == null) continue;
            sb.append(s.optString("id")).append('|')
              .append(s.optString("title")).append('|')
              .append(s.optBoolean("running")).append('|')
              .append(s.optBoolean("waiting")).append(';');
        }
        return sb.toString();
    }

    private View actionRow(String text, int icon, View.OnClickListener click) {
        View row = item(text, null, icon, true, click);
        return row;
    }

    // ---------------------------------------------------------------- 删除

    /** 长按后先确认，再删。 */
    private void askDelete(String id, String title, boolean active, boolean remote) {
        confirmDialog("删除会话", clip(title, 20),
                active ? "正在运行，删除会立即中断" : null,
                () -> { if (remote) deleteRemote(id); else deleteLocal(id); });
    }

    private void deleteRemote(final String id) {
        setBusyList(true);
        Net.get(this).deleteSession(id, (ok, value, error) -> {
            setBusyList(false);
            if (isFinishing() || isDestroyed()) return;
            if (!ok || value == null || !value.optBoolean("ok", false)) {
                toast(error == null || error.isEmpty() ? "删除失败" : error);
                return;
            }
            Cache.drop(this, "/api/sessions?project=" + enc(project));
            renderedKey = "";
            ticker.kick();
        });
    }

    private void deleteLocal(String id) {
        JSONArray chats = localChats();
        JSONArray kept = new JSONArray();
        for (int i = 0; i < chats.length(); i++) {
            JSONObject c = chats.optJSONObject(i);
            if (c == null) continue;
            if (id.equals(c.optString("id", ""))) continue;
            kept.put(c);
        }
        prefs().edit().putString("ds_chats", kept.toString())
                .remove("ds_msg_" + id).apply();
        Cache.drop(this, "/api/ds/status");
        renderedKey = "";
        ticker.kick();
    }

    /** 删除进行中：标题换成提示，防止连点删两次。 */
    private void setBusyList(boolean busy) {
        if (busy) toast("删除中…");
    }

    // ---------------------------------------------------------------- 新建

    private void createSession(String path) {
        JSONObject body = new JSONObject();
        try { if (path != null && !path.isEmpty()) body.put("projectPath", path); } catch (Exception ignored) { }
        Net.get(this).apiPost("/api/create", body, (ok, value, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (ok && value != null && value.optBoolean("ok", false)) {
                String id = value.optString("sessionId", "");
                if (!id.isEmpty()) {
                    renderedKey = "";
                    openHarnessChat(id, "新对话");
                }
            } else {
                toast(error);
            }
        });
    }

    private void newDsChat() {
        Net.get(this).apiPost("/api/ds/new", new JSONObject(), (ok, value, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (ok && value != null && value.optBoolean("ok", false)) {
                String id = value.optString("chatId", "");
                if (id.isEmpty()) return;
                rememberChat(id, "新对话");
                renderedKey = "";
                openDsChat(id, "新对话");
            } else {
                toast(error);
            }
        });
    }

    private JSONArray localChats() {
        try { return new JSONArray(prefs().getString("ds_chats", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void rememberChat(String id, String title) {
        JSONArray chats = localChats();
        for (int i = 0; i < chats.length(); i++) {
            JSONObject c = chats.optJSONObject(i);
            if (c != null && id.equals(c.optString("id", ""))) return;
        }
        try {
            JSONObject c = new JSONObject();
            c.put("id", id);
            c.put("title", title);
            chats.put(c);
            prefs().edit().putString("ds_chats", chats.toString()).apply();
        } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- 跳转

    private void openHarnessChat(String id, String title) {
        Intent it = new Intent(this, ChatActivity.class);
        it.putExtra("mode", "harness");
        it.putExtra("sessionId", id);
        it.putExtra("title", title);
        startActivity(it);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void openDsChat(String id, String title) {
        Intent it = new Intent(this, ChatActivity.class);
        it.putExtra("mode", "ds");
        it.putExtra("chatId", id);
        it.putExtra("title", title);
        startActivity(it);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // ---------------------------------------------------------------- 工具

    private android.content.SharedPreferences prefs() {
        return getSharedPreferences("li", MODE_PRIVATE);
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
