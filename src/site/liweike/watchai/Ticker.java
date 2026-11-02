package site.liweike.watchai;

import android.os.Handler;
import android.os.Looper;

/**
 * 前台周期刷新。
 *
 * 之前每个页面都是"进页面拉一次，之后再也不动"——所以必须退出重进才看到新数据。
 * 这个类只做一件事：可见时按间隔重复跑同一个任务，不可见时立刻停（省电）。
 * 生命周期由调用方的 onResume/onPause 驱动。
 */
final class Ticker implements Runnable {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long intervalMs;
    private final Runnable task;
    private boolean running;

    Ticker(long intervalMs, Runnable task) {
        this.intervalMs = intervalMs;
        this.task = task;
    }

    void start() {
        if (running) return;
        running = true;
        handler.post(this);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(this);
    }

    /** 立刻跑一次（例如刚发完消息，不想等下一个周期）。 */
    void kick() {
        if (!running) return;
        handler.removeCallbacks(this);
        handler.post(this);
    }

    @Override public void run() {
        if (!running) return;
        task.run();
        if (running) handler.postDelayed(this, intervalMs);
    }
}
