package site.liweike.watchai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 中继网络层。
 *
 * 两条路：
 *   1. 局域网 IPv4（在家，最快）
 *   2. 公网 IPv6（出门，移动流量直连，无需端口转发）
 *
 * IPv6 地址是运营商动态分配的，所以不写死：每次连上局域网时顺手把
 * /api/status 里上报的 host.ipv6 记下来，出门用记下的那个。
 */
final class Net {
    /** 局域网地址（电脑在 19490 端口）。 */
    static final String BASE_LAN = "http://192.168.1.100:19490";

    /**
     * 出厂 IPv6 兜底（前缀）。
     *
     * 教训一：别填 RFC 3849 的 `2001:db8::`，那是文档示例段，公网永远不可达。
     * 教训二：也别填完整地址——运营商每几十分钟换一次主机位（实测
     * `...:f153:2` → `...:f153:5`），写死就必然过期。
     *
     * 现状：运营商只换**最后一段**，前缀稳定。所以这里只存前缀，
     * 首次连接时扫描后缀自动找到真实地址（见 smartProbe）。
     */
    private static final String IPV6_PREFIX = "2409:0000:0000:0000:0000:0000:0000";

    /** 中继令牌。 */
    static final String TOKEN = "RELAY_TOKEN";

    /** 上次记下的 IPv6（静态，进程内共享）。 */
    private static String ipv6 = "";

    /** 上次成功的地址，下次优先用它——这是"响应慢"的主要修复点。 */
    private static volatile String lastGood = "";

    /** 连接超时：局域网/IPv6 都不该等这么久，2 秒足够判断死活。 */
    private static final int CONNECT_TIMEOUT_MS = 2_500;
    /** 读超时：服务端 /api/projects 约 0.6s，留足余量。 */
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int STREAM_TIMEOUT_MS = 90_000;

    private static final Net INSTANCE = new Net();

    /** 应用上下文，用于地址记忆落盘。 */
    private static Context appContext;

    static Net get(Context context) {
        if (context != null) appContext = context.getApplicationContext();
        return INSTANCE;
    }

    private final ExecutorService pool = Executors.newFixedThreadPool(6);
    private final Handler main = new Handler(Looper.getMainLooper());

    private Net() { }

    /** 从本地恢复上次的地址记忆，避免每次冷启动都重新探测。 */
    void restore(Context context) {
        android.content.SharedPreferences p = context.getSharedPreferences("li", Context.MODE_PRIVATE);
        String savedV6 = p.getString("ipv6", "");
        if (savedV6 != null && savedV6.contains(":")) ipv6 = savedV6;
        String savedGood = p.getString("lastGood", "");
        if (savedGood != null && !savedGood.isEmpty()) lastGood = savedGood;
    }

    /** 记下地址到本地。 */
    private void persist(Context context) {
        context.getSharedPreferences("li", Context.MODE_PRIVATE).edit()
                .putString("ipv6", ipv6 == null ? "" : ipv6)
                .putString("lastGood", lastGood == null ? "" : lastGood)
                .apply();
    }

    /** 手动指定 IPv6（用户兜底用）。 */
    static void setManualIPv6(String value) {
        String v = value == null ? "" : value.trim();
        if (v.startsWith("[")) v = v.substring(1);
        if (v.endsWith("]")) v = v.substring(0, v.length() - 1);
        if (v.contains(":")) ipv6 = v;
    }

    static String currentIPv6() { return ipv6 == null ? "" : ipv6; }

    /**
     * 前缀扫描：运营商只换最后一段，把 1..254 全试一遍就能找到真身。
     *
     * 为什么可行：实测 IPv6 从 `...:f153:2` 变成 `...:f153:5`，
     * 前 15 段一个没动。所以没必要让用户手改地址，扫一遍即可。
     * 用独立线程池跑，不占主请求池。
     */
    static void smartProbe(String prefix, java.util.function.Consumer<String> onFound) {
        if (prefix == null || prefix.trim().isEmpty() || onFound == null) return;
        final String base = prefix.trim();
        final java.util.concurrent.atomic.AtomicBoolean settled =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.ExecutorService scanner =
                java.util.concurrent.Executors.newFixedThreadPool(32);

        for (int i = 1; i <= 254 && !settled.get(); i++) {
            final String candidate = base + ":" + Integer.toHexString(i);
            scanner.execute(() -> {
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL("http://[" + candidate + "]:19490/api/status")
                            .openConnection();
                    conn.setConnectTimeout(1200);
                    conn.setReadTimeout(1200);
                    conn.setRequestMethod("GET");
                    if (conn.getResponseCode() == 200) {
                        if (settled.compareAndSet(false, true)) {
                            INSTANCE.main.post(() -> onFound.accept(candidate));
                        }
                    }
                } catch (Exception ignored) {
                    // 扫不到是常态，继续下一个
                } finally {
                    if (conn != null) conn.disconnect();
                }
            });
        }
        // 扫描器跑完自己关闭（最多 254 个任务，很快）
        scanner.shutdown();
    }

    /** 候选地址：局域网优先，IPv6 兜底。 */
    static String[] bases() {
        String v6 = ipv6 == null ? "" : ipv6.trim();
        String v6base = v6.isEmpty() ? "" : "http://[" + v6 + "]:19490";
        // 上次成功的地址排第一，避免每次都先撞一遍死地址。
        String good = lastGood;
        if (good != null && !good.isEmpty()) {
            if (good.equals(v6base)) return new String[]{v6base, BASE_LAN};
            return v6base.isEmpty() ? new String[]{good, BASE_LAN} : new String[]{good, BASE_LAN, v6base};
        }
        return v6base.isEmpty() ? new String[]{BASE_LAN} : new String[]{BASE_LAN, v6base};
    }

    /** 记下中继上报的 IPv6，供出门使用。/api/status 给顶层，/api/live 给 host 里。 */
    private void rememberIPv6(JSONObject value) {
        try {
            String v6 = value == null ? "" : value.optString("ipv6", "");
            if (v6 == null || !v6.contains(":")) {
                JSONObject host = value == null ? null : value.optJSONObject("host");
                v6 = host == null ? "" : host.optString("ipv6", "");
            }
            if (v6 != null && v6.contains(":")) ipv6 = v6;
        } catch (Exception ignored) { }
    }

    /** 记下地址并落盘（下次冷启动直接用）。 */
    private void remember(Context context, JSONObject value, String base) {
        rememberIPv6(value);
        if (base != null && !base.isEmpty()) lastGood = base;
        if (context != null) persist(context);
    }

    interface Callback { void onResult(boolean ok, JSONObject value, String error); }

    interface Stream {
        void onDelta(String text);
        void onThink(String text);
        void onDone();
        void onError(String error);
    }

    interface Cancelable { void cancel(); }

    /** GET /api/xxx */
    void api(String path, Callback cb) {
        request("GET", path, null, cb);
    }

/**
     * 带缓存的 GET：先回缓存（秒开），再拉网络；
     * 网络失败时把缓存当结果交回去，界面不至于空屏。
     */
    void apiCached(Context context, String path, Callback cb) {
        request("GET", path, null, (ok, value, error) -> {
            if (ok) {
                Cache.put(context, path, value.toString());
                Cache.touch(context, path);
                cb.onResult(true, value, "");
                return;
            }
            // 网络失败才回缓存。之前是"先回缓存再回网络"，界面 addView 两次就出现重复项。
            String cached = Cache.get(context, path);
            if (!cached.isEmpty()) {
                try {
                    JSONObject value2 = new JSONObject(cached);
                    cb.onResult(true, value2, "");
                    return;
                } catch (Exception ignored) { }
            }
            cb.onResult(false, null, error);
        });
    }

    /** POST /api/xxx */
    void apiPost(String path, JSONObject body, Callback cb) {
        request("POST", path, body, cb);
    }

    /**
     * 删除一个 DSH 会话。
     *
     * 中继把它转给 dsh-session-manager 插件——真正干活的是插件（停 agent、
     * flush、删会话目录、级联删子智能体、清投影缓存）。中继只负责鉴权和转发。
     */
    void deleteSession(String sessionId, Callback cb) {
        JSONObject body = new JSONObject();
        try { body.put("sessionId", sessionId); } catch (Exception ignored) { }
        apiPost("/api/delete", body, cb);
    }

    private void request(String method, String path, JSONObject body, Callback cb) {
        String[] candidates = bases();
        // 只有 GET 能并发：POST 并发会重复提交（比如把一条消息发两遍）。
        if (candidates.length <= 1 || !"GET".equals(method)) {
            requestAt(method, candidates, 0, path, body, cb);
            return;
        }
        // GET：并发发起，只交第一个成功的结果。
        final java.util.concurrent.atomic.AtomicBoolean settled =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        final String[] firstError = {null};
        final java.util.concurrent.atomic.AtomicInteger pending =
                new java.util.concurrent.atomic.AtomicInteger(candidates.length);
        for (int i = 0; i < candidates.length; i++) {
            // 只传单个地址：并发分支不该再自己往下轮，否则同一地址会被请求两次。
            final String[] single = {candidates[i]};
            requestAt(method, single, 0, path, body, (ok, value, error) -> {
                if (ok) {
                    if (settled.compareAndSet(false, true)) cb.onResult(true, value, "");
                    return;
                }
                if (firstError[0] == null) firstError[0] = error;
                if (pending.decrementAndGet() == 0 && settled.compareAndSet(false, true)) {
                    cb.onResult(false, null, firstError[0]);
                }
            });
        }
    }

    /**
     * 并发尝试所有候选地址，第一个成功的就用。
     *
     * 之前是串行：局域网 2.5 秒超时后才试 IPv6，等于每个请求白等 2.5 秒。
     * 并发之后，出门场景直接命中 IPv6，在家直接命中局域网，都是一次往返。
     */
    private void requestAt(String method, String[] candidates, int index,
                           String path, JSONObject body, Callback cb) {
        if (index >= candidates.length) {
            // 所有已知地址都不通——可能运营商换了 IPv6 主机位。
            // 用前缀扫一遍当前网段，找到就记住并重试一次。
            final String[] tried = candidates;
            smartProbe(IPV6_PREFIX, found -> {
                ipv6 = found;
                if (appContext != null) persist(appContext);
                // 找到新地址后重试一次（只重试一次，避免死循环）
                final String newBase = "http://[" + found + "]:19490";
                for (String t : tried) {
                    if (t.equals(newBase)) {
                        cb.onResult(false, null, "连不上中继");
                        return;
                    }
                }
                requestAt(method, new String[]{newBase}, 0, path, body, cb);
            });
            return;
        }
        final String base = candidates[index];
        pool.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(base + path).openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setRequestProperty("accept", "application/json");
                conn.setRequestProperty("authorization", "Bearer " + TOKEN);
                if (body != null) {
                    conn.setDoOutput(true);
                    conn.setRequestProperty("content-type", "application/json; charset=utf-8");
                    byte[] payload = body.toString().getBytes("UTF-8");
                    conn.setFixedLengthStreamingMode(payload.length);
                    try (OutputStream out = conn.getOutputStream()) { out.write(payload); }
                }
                int status = conn.getResponseCode();
                String text = readAll(status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream());
                if (status < 200 || status >= 300) {
                    String detail = errorOf(status, text);
                    // 4xx 是业务错误，不该换地址重试
                    if (status >= 400 && status < 500) {
                        main.post(() -> cb.onResult(false, null, detail));
                        return;
                    }
                    requestAt(method, candidates, index + 1, path, body, cb);
                    return;
                }
                JSONObject value = new JSONObject(text);
                rememberIPv6(value);
                lastGood = base;
                if (appContext != null) persist(appContext);
                main.post(() -> cb.onResult(true, value, ""));
            } catch (Exception error) {
                if (conn != null) conn.disconnect();
                requestAt(method, candidates, index + 1, path, body, cb);
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    /** SSE 流（DeepSeek 用）。 */
    Cancelable stream(String path, JSONObject body, Stream stream) {
        return streamAt(path, body, stream, bases(), 0);
    }

    private Cancelable streamAt(String path, JSONObject body, Stream stream, String[] candidates, int index) {
        final boolean[] canceled = {false};
        final HttpURLConnection[] holder = new HttpURLConnection[1];
        if (index >= candidates.length) {
            main.post(() -> stream.onError("连不上中继"));
            return () -> { };
        }
        final String base = candidates[index];
        pool.execute(() -> {
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            try {
                conn = (HttpURLConnection) new URL(base + path).openConnection();
                holder[0] = conn;
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(STREAM_TIMEOUT_MS);
                conn.setUseCaches(false);
                conn.setRequestProperty("accept", "text/event-stream");
                conn.setRequestProperty("cache-control", "no-cache");
                conn.setRequestProperty("content-type", "application/json; charset=utf-8");
                conn.setRequestProperty("authorization", "Bearer " + TOKEN);
                byte[] payload = body.toString().getBytes("UTF-8");
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(payload.length);
                try (OutputStream out = conn.getOutputStream()) { out.write(payload); }
                int status = conn.getResponseCode();
                if (status < 200 || status >= 300) {
                    String text = readAll(conn.getErrorStream());
                    String message = errorOf(status, text);
                    main.post(() -> stream.onError(message));
                    return;
                }
                lastGood = base;
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"), 8192);
                StringBuilder line = new StringBuilder();
                while (true) {
                    int ch = reader.read();
                    if (ch == -1) break;
                    if (canceled[0]) return;
                    if (ch == '\n') {
                        String row = line.toString().trim();
                        line.setLength(0);
                        if (!row.startsWith("data:")) continue;
                        String type;
                        String text;
                        try {
                            JSONObject event = new JSONObject(row.substring(5).trim());
                            type = event.optString("type", "");
                            text = event.optString("text", "");
                        } catch (Exception ignored) { continue; }
                        main.post(() -> {
                            if (canceled[0]) return;
                            if ("delta".equals(type)) stream.onDelta(text);
                            else if ("think".equals(type)) stream.onThink(text);
                            else if ("done".equals(type)) stream.onDone();
                            else if ("error".equals(type)) stream.onError(text.isEmpty() ? "上游出错" : text);
                        });
                        if ("done".equals(type) || "error".equals(type)) return;
                    } else if (ch != '\r') {
                        line.append((char) ch);
                    }
                }
                main.post(stream::onDone);
            } catch (Exception error) {
                if (canceled[0]) return;
                if (conn != null) conn.disconnect();
                // 连接阶段失败可以换地址，发送后失败不能重发
                streamAt(path, body, stream, candidates, index + 1);
                return;
            } finally {
                if (reader != null) try { reader.close(); } catch (Exception ignored) { }
                if (conn != null) conn.disconnect();
            }
        });
        return () -> {
            canceled[0] = true;
            if (holder[0] != null) try { holder[0].disconnect(); } catch (Exception ignored) { }
        };
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = stream.read(chunk)) != -1) buffer.write(chunk, 0, read);
        return buffer.toString("UTF-8");
    }

    private static String describe(Exception error) {
        if (error instanceof java.net.SocketTimeoutException) return "连接超时";
        if (error instanceof java.net.ConnectException) return "连不上中继";
        if (error instanceof java.net.UnknownHostException) return "地址无法解析";
        return "网络错误";
    }

    private static String errorOf(int status, String text) {
        String detail = text == null ? "" : text.trim();
        try {
            String error = new JSONObject(detail).optString("error", "");
            if (!error.isEmpty()) return error;
        } catch (Exception ignored) { }
        return "服务返回 " + status;
    }
}