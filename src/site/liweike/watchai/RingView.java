package site.liweike.watchai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.view.View;

class RingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float progress = 0f;

    RingView(Context c) {
        super(c);
        float sw = Ui.px(16);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(sw);
        track.setColor(0xFF1C1C20);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(sw);
        arc.setStrokeCap(Paint.Cap.ROUND);
    }

    void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float inset = arc.getStrokeWidth() / 2f;
        oval.set(inset, inset, w - inset, h - inset);
        cv.drawOval(oval, track);
        if (progress <= 0.001f) return;
        float pos = Math.max(0.05f, progress);
        arc.setShader(new SweepGradient(w / 2f, h / 2f, new int[]{Ui.A1, Ui.A2}, new float[]{0f, pos}));
        cv.save();
        cv.rotate(-90f, w / 2f, h / 2f);
        cv.drawArc(oval, 0f, 360f * progress, false, arc);
        cv.restore();
    }
}
