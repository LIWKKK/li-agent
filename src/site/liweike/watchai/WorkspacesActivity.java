package site.liweike.watchai;

import android.content.Intent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

public class WorkspacesActivity extends ListScreen {
    @Override // site.liweike.watchai.BaseActivity
    protected void build() {
        render();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void render() {
        closeOverlay();
        this.stage.removeAllViews();
        startList("Harness", "选择工作区");
        this.col.addView(label("工作区 · 来自电脑"));
        Net.get(this).apiCached(this, "/api/projects", (ok, value, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (!ok || value == null || !value.optBoolean("ok", false)) {
                String reason = error == null || error.isEmpty() ? "连不上中继" : error;
                this.col.addView(item(reason, null, 6, false, v -> render()));
                return;
            }
            JSONArray projects = value.optJSONArray("projects");
            int count = projects == null ? 0 : projects.length();
            if (count == 0) {
                this.col.addView(empty("暂无工作区"));
                this.col.addView(item("提示", "在电脑上开一个会话即可", 6, false, null));
                return;
            }
            for (int i = 0; i < count; i++) {
                JSONObject project = projects.optJSONObject(i);
                if (project == null) continue;
                final String name = project.optString("name", "");
                final String path = project.optString("path", "");
                int sessionCount = project.optInt("sessionCount", 0);
                int running = project.optInt("running", 0);
                String sub = sessionCount + " 个会话" + (running > 0 ? " · " + running + " 运行中" : "");
                View viewItem = item(name, sub, 0, running > 0, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        Intent intent = new Intent(WorkspacesActivity.this, ConversationsActivity.class);
                        intent.putExtra("workspace", name);
                        intent.putExtra("path", path);
                        WorkspacesActivity.this.startActivity(intent);
                        WorkspacesActivity.this.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    }
                });
                viewItem.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View view) {
                        view.performHapticFeedback(0);
                        confirm("删除工作区？", name,
                                "工作区由电脑端管理，手表上仅展示", "知道了", null);
                        return true;
                    }
                });
                this.col.addView(viewItem);
            }
        });
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
    }
}
