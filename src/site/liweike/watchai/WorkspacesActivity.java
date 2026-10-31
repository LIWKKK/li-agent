package site.liweike.watchai;

import android.content.Intent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

/** DeepSeek Harness entry: pick a workspace (real projects from the relay). */
public class WorkspacesActivity extends ListScreen {
    @Override protected void build() {
        startList("DeepSeek Harness", "选择工作区");
        final View label = label("工作区");
        col.addView(label);

        Net.get(this).apiCached(this, "/api/projects", (ok, value, error) -> {
            if (!ok || value == null || !value.optBoolean("ok", false)) {
                col.removeView(label);
                String reason = error == null || error.isEmpty() ? "连不上中继" : error;
                col.addView(item(reason, null, IconView.INFO, false, v -> build()));
                return;
            }
            JSONArray projects = value.optJSONArray("projects");
            if (projects == null || projects.length() == 0) {
                col.addView(item("还没有项目", "在电脑上开一个会话", IconView.FOLDER, false, null));
                return;
            }
            for (int i = 0; i < projects.length(); i++) {
                JSONObject project = projects.optJSONObject(i);
                if (project == null) continue;
                final String name = project.optString("name", "");
                final String path = project.optString("path", "");
                int count = project.optInt("sessionCount", 0);
                int running = project.optInt("running", 0);
                String sub = count + " 个会话" + (running > 0 ? " · " + running + " 运行中" : "");
                col.addView(item(name, sub, IconView.FOLDER, running > 0, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Intent it = new Intent(WorkspacesActivity.this, ConversationsActivity.class);
                        it.putExtra("workspace", name);
                        it.putExtra("path", path);
                        startActivity(it);
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    }
                }));
            }
        });
    }
}