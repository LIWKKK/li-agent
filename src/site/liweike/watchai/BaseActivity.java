package site.liweike.watchai;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/** Hosts a square 466x466 "stage" centered on screen so the round-face geometry is exact. */
public abstract class BaseActivity extends Activity {
    protected FrameLayout stage;
    private float x0, y0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        stage = new FrameLayout(this);
        stage.setFocusableInTouchMode(true);
        root.addView(stage, new FrameLayout.LayoutParams(Ui.px(466), Ui.px(466), Gravity.CENTER));
        setContentView(root);
        build();
    }

    protected abstract void build();

    /** Back gesture / button. Screens with overlays can close them here before finishing. */
    protected void handleBack() { finish(); }

    @Override public void onBackPressed() { handleBack(); }

    protected boolean swipeBack() { return true; }

    protected void open(Class<?> c) {
        startActivity(new Intent(this, c));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            x0 = e.getRawX();
            y0 = e.getRawY();
        } else if (a == MotionEvent.ACTION_UP && swipeBack()) {
            float dx = e.getRawX() - x0, dy = e.getRawY() - y0;
            if (x0 < Ui.px(190) && dx > Ui.px(110) && Math.abs(dy) < dx * 0.6f) {
                handleBack();
            }
        }
        return super.dispatchTouchEvent(e);
    }

    @Override public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
