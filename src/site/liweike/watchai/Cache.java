package site.liweike.watchai;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 本地缓存：把成功拉到的数据存下来。
 *
 * 目的：断网时仍能查看上次的工作区、会话、对话内容、用量和系统信息。
 * 所有写入都是"最后一次成功响应"的快照，key 用路径本身，简单直接。
 */
final class Cache {
    private static final String FILE = "cache";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static void put(Context c, String key, String value) {
        if (value == null) return;
        prefs(c).edit().putString(key, value).apply();
    }

    static String get(Context c, String key) {
        return prefs(c).getString(key, "");
    }

    static long at(Context c, String key) {
        return prefs(c).getLong(key + "@", 0L);
    }

    /** 记下最后更新时间，界面可显示"离线数据"提示。 */
    static void touch(Context c, String key) {
        prefs(c).edit().putLong(key + "@", System.currentTimeMillis()).apply();
    }
}
