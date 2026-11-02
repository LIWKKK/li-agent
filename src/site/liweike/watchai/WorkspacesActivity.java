package site.liweike.watchai;

import android.content.Intent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

/** the coding agent entry: pick a workspace (real projects from the relay). */
public class WorkspacesActivity extends ListScreen {
    private Ticker ticker;
    private boolean loading;
    /** 项目列表可能变（新建/删除会话都会影响计数），每次刷新整体重建。 */
    private boolean drawn;

    @Override protected void build() {
        startList("the coding agent", "选择工作区");
        ticker = new Ticker(5_000, this::load);
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
        Net.get(this).apiCached(this, "/api/projects", (ok, value, error) -> {
            loading = false;
            if (isFinishing() || isDestroyed()) return;
            if (!ok || value == null || !value.optBoolean("ok", false)) {
                if (!drawn) {
                    String reason = error == null || error.isEmpty() ? "连不上中继" : error;
                    col.removeAllViews();
                    col.addView(item(reason, null, IconView.INFO, false, v -> load()));
                    drawn = true;
                }
                return;
            }
            JSONArray projects = value.optJSONArray("projects");
            // 首次进入或数量变化才重建；内容没变就不动，避免每 5 秒闪一下。
            int count = projects == null ? 0 : projects.length();
            if (drawn && count == lastCount) return;
            lastCount = count;
            drawn = true;

            col.removeAllViews();
            col.addView(label("工作区"));
            if (count == 0) {
                col.addView(item("还没有项目", "在电脑上开一个会话", IconView.FOLDER, false, null));
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
                View row = item(name, sub, IconView.FOLDER, running > 0, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Intent it = new Intent(WorkspacesActivity.this, ConversationsActivity.class);
                        it.putExtra("workspace", name);
                        it.putExtra("path", path);
                        startActivity(it);
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    }
                });
                col.addView(row);
            }
        });
    }

    private int lastCount = -1;
}
