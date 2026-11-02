package site.liweike.watchai;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

/* JADX INFO: loaded from: classes.dex */
class EdgeScrollView extends ScrollView {
    EdgeScrollView(Context context) {
        super(context);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(2);
    }

    private void apply() {
        if (getChildCount() != 0) {
            ViewGroup viewGroup = (ViewGroup) getChildAt(0);
            int height = getHeight();
            if (height != 0) {
                float f = height / 2.0f;
                int scrollY = getScrollY();
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    View childAt = viewGroup.getChildAt(i);
                    float fMin = Math.min(1.0f, Math.abs(((childAt.getTop() + (childAt.getHeight() / 2.0f)) - scrollY) - f) / f);
                    float f2 = 1.0f - ((0.16f * fMin) * fMin);
                    float fMax = fMin < 0.62f ? 1.0f : Math.max(0.12f, 1.0f - ((fMin - 0.62f) * 2.2f));
                    childAt.setScaleX(f2);
                    childAt.setScaleY(f2);
                    childAt.setAlpha(fMax);
                }
            }
        }
    }

    @Override // android.view.View
    protected void onScrollChanged(int i, int i2, int i3, int i4) {
        super.onScrollChanged(i, i2, i3, i4);
        apply();
    }

    @Override // android.widget.ScrollView, android.widget.FrameLayout, android.view.ViewGroup, android.view.View
    protected void onLayout(boolean z, int i, int i2, int i3, int i4) {
        super.onLayout(z, i, i2, i3, i4);
        apply();
    }
}
