package site.liweike.watchai;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/* JADX INFO: loaded from: classes.dex */
public abstract class BaseActivity extends Activity {
    protected FrameLayout stage;
    private float x0;
    private float y0;

    protected abstract void build();

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Ui.init(this);
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setBackgroundColor(-14737891);
        this.stage = new FrameLayout(this);
        this.stage.setFocusableInTouchMode(true);
        frameLayout.addView(this.stage, new FrameLayout.LayoutParams(Ui.px(466.0f), Ui.px(466.0f), 17));
        setContentView(frameLayout);
        build();
    }

    protected void handleBack() {
        finish();
    }

    @Override // android.app.Activity
    public void onBackPressed() {
        handleBack();
    }

    protected boolean swipeBack() {
        return true;
    }

    protected void open(Class<?> cls) {
        startActivity(new Intent(this, cls));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        int actionMasked = motionEvent.getActionMasked();
        if (actionMasked == 0) {
            this.x0 = motionEvent.getRawX();
            this.y0 = motionEvent.getRawY();
        } else if (actionMasked == 1 && swipeBack()) {
            float rawX = motionEvent.getRawX() - this.x0;
            float rawY = motionEvent.getRawY() - this.y0;
            if (this.x0 < Ui.px(190.0f) && rawX > Ui.px(110.0f) && Math.abs(rawY) < rawX * 0.6f) {
                handleBack();
            }
        }
        return super.dispatchTouchEvent(motionEvent);
    }

    @Override // android.app.Activity
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
