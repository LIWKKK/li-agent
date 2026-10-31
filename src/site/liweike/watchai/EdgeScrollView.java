package site.liweike.watchai;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

/** ScrollView whose rows shrink and fade toward the top/bottom of the round screen. */
class EdgeScrollView extends ScrollView {
    EdgeScrollView(Context c) {
        super(c);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    private void apply() {
        if (getChildCount() == 0) return;
        ViewGroup col = (ViewGroup) getChildAt(0);
        int h = getHeight();
        if (h == 0) return;
        float mid = h / 2f;
        int sy = getScrollY();
        for (int i = 0; i < col.getChildCount(); i++) {
            View v = col.getChildAt(i);
            float cy = v.getTop() + v.getHeight() / 2f - sy;
            float d = Math.min(1f, Math.abs(cy - mid) / mid);
            float sc = 1f - 0.16f * d * d;
            float a = d < 0.62f ? 1f : Math.max(0.12f, 1f - (d - 0.62f) * 2.2f);
            v.setScaleX(sc);
            v.setScaleY(sc);
            v.setAlpha(a);
        }
    }

    @Override protected void onScrollChanged(int l, int t, int ol, int ot) {
        super.onScrollChanged(l, t, ol, ot);
        apply();
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        apply();
    }
}
