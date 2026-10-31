package site.liweike.watchai;

import android.content.Intent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 对话列表。带 workspace 时是 Harness 项目下的会话，否则是 DeepSeek Chat 的历史。
 */
public class ConversationsActivity extends ListScreen {
    private String chatTitle;
    private boolean harness;

    @Override protected void build() {
        final String ws = getIntent().getStringExtra("workspace");
        final String path = getIntent().getStringExtra("path");
        harness = ws != null;
        chatTitle = harness ? ws : "DeepSeek Chat";
        startList(chatTitle, harness ? "DeepSeek Harness" : "历史对话");

        if (harness) {
            col.addView(item("新建对话", null, IconView.PLUS, true, new View.OnClickListener() {
                @Override public void onClick(View v) { createSession(path); }
            }));
            col.addView(label("会话"));
            loadSessions(ws);
        } else {
            col.addView(item("新建对话", null, IconView.PLUS, true, new View.OnClickListener() {
                @Override public void onClick(View v) { newDsChat(); }
            }));
            col.addView(label("历史对话"));
            loadDsChats();
        }
    }

    // ---------------------------------------------------------------- Harness

    private void loadSessions(String project) {
        Net.get(this).apiCached(this, "/api/sessions?project=" + enc(project), (ok, value, error) -> {
            if (!ok || value == null || !value.optBoolean("ok", false)) {
                col.addView(item("读取失败", error, IconView.INFO, false, null));
                return;
            }
            JSONArray sessions = value.optJSONArray("sessions");
            if (sessions == null || sessions.length() == 0) {
                col.addView(item("还没有会话", "点上面新建一个", IconView.BUBBLE, false, null));
                return;
            }
            for (int i = 0; i < sessions.length(); i++) {
                JSONObject s = sessions.optJSONObject(i);
                if (s == null) continue;
                final String id = s.optString("id", "");
                final String title = s.optString("title", "未命名");
                boolean running = s.optBoolean("running", false);
                boolean waiting = s.optBoolean("waiting", false);
                String sub = waiting ? "等待回答" : running ? "运行中" : "空闲";
                String model = s.optString("model", "");
                if (!model.isEmpty()) sub += " · " + model;
                col.addView(item(clip(title, 18), sub, IconView.BUBBLE, running,
                        v -> openHarnessChat(id, title)));
            }
        });
    }

    private void createSession(String path) {
        JSONObject body = new JSONObject();
        try { if (path != null && !path.isEmpty()) body.put("projectPath", path); } catch (Exception ignored) { }
        Net.get(this).apiPost("/api/create", body, (ok, value, error) -> {
            if (ok && value != null && value.optBoolean("ok", false)) {
                String id = value.optString("sessionId", "");
                if (!id.isEmpty()) openHarnessChat(id, "新对话");
            } else {
                toast(error);
            }
        });
    }

    // ---------------------------------------------------------------- DeepSeek

    /** 本地会话列表：只渲染一次，网络只用于标登录态。 */
    private void loadDsChats() {
        Net.get(this).apiCached(this, "/api/ds/status", (ok, value, error) -> {
            boolean loggedIn = ok && value != null && value.optBoolean("loggedIn", false);
            JSONArray chats = localChats();
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
                col.addView(item(clip(title, 18), loggedIn ? "" : "离线", IconView.BUBBLE, false,
                        v -> openDsChat(id, title)));
            }
        });
    }

    private JSONArray localChats() {
        try { return new JSONArray(prefs().getString("ds_chats", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void newDsChat() {
        Net.get(this).apiPost("/api/ds/new", new JSONObject(), (ok, value, error) -> {
            if (ok && value != null && value.optBoolean("ok", false)) {
                String id = value.optString("chatId", "");
                if (id.isEmpty()) return;
                rememberChat(id, "新对话");
                openDsChat(id, "新对话");
            } else {
                toast(error);
            }
        });
    }

    /** 记一条本地会话，便于下次直接进来。 */
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