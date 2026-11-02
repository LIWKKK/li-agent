package site.liweike.watchai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.view.View;

/* JADX INFO: loaded from: classes.dex */
class RingView extends View {
    private final Paint arc;
    private final RectF oval;
    private float progress;
    private final Paint track;

    RingView(Context context) {
        super(context);
        this.track = new Paint(1);
        this.arc = new Paint(1);
        this.oval = new RectF();
        this.progress = 0.0f;
        float fPx = Ui.px(16.0f);
        this.track.setStyle(Paint.Style.STROKE);
        this.track.setStrokeWidth(fPx);
        this.track.setColor(-14935008);
        this.arc.setStyle(Paint.Style.STROKE);
        this.arc.setStrokeWidth(fPx);
        this.arc.setStrokeCap(Paint.Cap.ROUND);
    }

    void setProgress(float f) {
        this.progress = Math.max(0.0f, Math.min(1.0f, f));
        invalidate();
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        float strokeWidth = this.arc.getStrokeWidth() / 2.0f;
        this.oval.set(strokeWidth, strokeWidth, width - strokeWidth, height - strokeWidth);
        canvas.drawOval(this.oval, this.track);
        if (this.progress > 0.001f) {
            this.arc.setShader(new SweepGradient(width / 2.0f, height / 2.0f, new int[]{-2525353, -1863569}, new float[]{0.0f, Math.max(0.05f, this.progress)}));
            canvas.save();
            canvas.rotate(-90.0f, width / 2.0f, height / 2.0f);
            canvas.drawArc(this.oval, 0.0f, this.progress * 360.0f, false, this.arc);
            canvas.restore();
        }
    }
}
