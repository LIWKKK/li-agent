package site.liweike.watchai;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
final class Store {
    private static final String PREF = "li_store";

    Store() {
    }

    static String wsScope(String str) {
        return "ws:" + str;
    }

    private static JSONObject load(Context context) {
        String string = context.getSharedPreferences(PREF, 0).getString("d", null);
        if (string != null) {
            try {
                return new JSONObject(string);
            } catch (JSONException e) {
            }
        }
        JSONObject jSONObjectSeed = seed();
        save(context, jSONObjectSeed);
        return jSONObjectSeed;
    }

    private static void save(Context context, JSONObject jSONObject) {
        context.getSharedPreferences(PREF, 0).edit().putString("d", jSONObject.toString()).apply();
    }

    private static JSONObject convo(String str, String str2) throws JSONException {
        return new JSONObject().put("t", str).put("time", str2);
    }

    private static JSONObject convo(String str, String str2, String chatId) throws JSONException {
        return new JSONObject().put("t", str).put("time", str2).put("c", chatId);
    }

    static void addConvo(Context context, String str, String str2, String chatId) {
        try {
            JSONObject jSONObjectLoad = load(context);
            JSONObject jSONObject = jSONObjectLoad.getJSONObject("convos");
            JSONArray old = jSONObject.optJSONArray(str);
            JSONArray arr = new JSONArray().put(convo(str2, "刚刚", chatId));
            if (old != null) {
                for (int i = 0; i < old.length(); i++) arr.put(old.getJSONObject(i));
            }
            jSONObject.put(str, arr);
            save(context, jSONObjectLoad);
        } catch (JSONException e) {
        }
    }

    private static JSONObject seed() {
        JSONObject jSONObject = new JSONObject();
        try {
            JSONArray jSONArray = new JSONArray();
            for (String[] strArr : new String[][]{new String[]{"liweike-site", "~/code/liweike-site"}, new String[]{"agent-core", "~/code/agent-core"}, new String[]{"scripts", "~/scripts"}, new String[]{"notes", "~/Documents/notes"}}) {
                jSONArray.put(new JSONObject().put("name", strArr[0]).put("path", strArr[1]));
            }
            JSONObject jSONObject2 = new JSONObject();
            jSONObject2.put("chat", new JSONArray().put(convo("周末行程规划", "2 分钟前")).put(convo("英文邮件润色", "1 小时前")).put(convo("代码问题排查", "昨天")));
            jSONObject2.put(wsScope("liweike-site"), new JSONArray().put(convo("重构导航栏样式", "刚刚")).put(convo("修复构建报错", "2 小时前")).put(convo("整理依赖版本", "昨天")));
            jSONObject2.put(wsScope("agent-core"), new JSONArray().put(convo("设计任务调度器", "今天")).put(convo("补充单元测试", "昨天")));
            jSONObject2.put(wsScope("scripts"), new JSONArray().put(convo("批量重命名文件", "周二")));
            jSONObject2.put(wsScope("notes"), new JSONArray().put(convo("整理读书笔记", "周一")).put(convo("生成周报", "上周")));
            jSONObject.put("workspaces", jSONArray).put("convos", jSONObject2);
        } catch (JSONException e) {
        }
        return jSONObject;
    }

    static List<String[]> workspaces(Context context) {
        ArrayList arrayList = new ArrayList();
        try {
            JSONArray jSONArray = load(context).getJSONArray("workspaces");
            for (int i = 0; i < jSONArray.length(); i++) {
                JSONObject jSONObject = jSONArray.getJSONObject(i);
                arrayList.add(new String[]{jSONObject.getString("name"), jSONObject.getString("path")});
            }
        } catch (JSONException e) {
        }
        return arrayList;
    }

    static void addWorkspace(Context context, String str, String str2) {
        try {
            JSONObject jSONObjectLoad = load(context);
            JSONArray jSONArray = jSONObjectLoad.getJSONArray("workspaces");
            // 去重：同名或同路径不重复加
            for (int i = 0; i < jSONArray.length(); i++) {
                JSONObject item = jSONArray.getJSONObject(i);
                if (item.getString("name").equals(str) || item.getString("path").equals(str2)) return;
            }
            jSONArray.put(new JSONObject().put("name", str).put("path", str2));
            if (!jSONObjectLoad.getJSONObject("convos").has(wsScope(str))) {
                jSONObjectLoad.getJSONObject("convos").put(wsScope(str), new JSONArray());
            }
            save(context, jSONObjectLoad);
        } catch (JSONException e) {
        }
    }

    static void removeWorkspace(Context context, String str) {
        try {
            JSONObject jSONObjectLoad = load(context);
            JSONArray jSONArray = jSONObjectLoad.getJSONArray("workspaces");
            JSONArray jSONArray2 = new JSONArray();
            for (int i = 0; i < jSONArray.length(); i++) {
                if (!jSONArray.getJSONObject(i).getString("name").equals(str)) {
                    jSONArray2.put(jSONArray.getJSONObject(i));
                }
            }
            jSONObjectLoad.put("workspaces", jSONArray2);
            jSONObjectLoad.getJSONObject("convos").remove(wsScope(str));
            save(context, jSONObjectLoad);
        } catch (JSONException e) {
        }
    }

    static List<String[]> convos(Context context, String str) {
        ArrayList arrayList = new ArrayList();
        try {
            JSONArray jSONArrayOptJSONArray = load(context).getJSONObject("convos").optJSONArray(str);
            if (jSONArrayOptJSONArray != null) {
                for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
                    JSONObject jSONObject = jSONArrayOptJSONArray.getJSONObject(i);
                    arrayList.add(new String[]{jSONObject.getString("t"), jSONObject.getString("time"), jSONObject.optString("c", "")});
                }
            }
        } catch (JSONException e) {
        }
        return arrayList;
    }

    static void removeConvo(Context context, String str, int i) {
        try {
            JSONObject jSONObjectLoad = load(context);
            JSONArray jSONArrayOptJSONArray = jSONObjectLoad.getJSONObject("convos").optJSONArray(str);
            if (jSONArrayOptJSONArray != null && i >= 0 && i < jSONArrayOptJSONArray.length()) {
                jSONArrayOptJSONArray.remove(i);
                save(context, jSONObjectLoad);
            }
        } catch (JSONException e) {
        }
    }

    static void addConvo(Context context, String str, String str2) {
        try {
            JSONObject jSONObjectLoad = load(context);
            JSONObject jSONObject = jSONObjectLoad.getJSONObject("convos");
            JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray(str);
            JSONArray jSONArrayPut = new JSONArray().put(convo(str2, "刚刚"));
            if (jSONArrayOptJSONArray != null) {
                for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
                    jSONArrayPut.put(jSONArrayOptJSONArray.getJSONObject(i));
                }
            }
            jSONObject.put(str, jSONArrayPut);
            save(context, jSONObjectLoad);
        } catch (JSONException e) {
        }
    }
}