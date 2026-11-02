package site.liweike.watchai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/* JADX INFO: loaded from: classes.dex */
class IconView extends View {
    static final int ARROW = 4;
    static final int BOLT = 9;
    static final int BUBBLE = 3;
    static final int CHECK = 10;
    static final int CHEVRON = 8;
    static final int FOLDER = 0;
    static final int INFO = 6;
    static final int MONITOR = 1;
    static final int PLUS = 7;
    static final int SPARKLE = 2;
    static final int STAR = 11;
    static final int TERMINAL = 5;
    private final Paint p;
    private final Path path;
    private final RectF r;
    private final int type;

    IconView(Context context, int i, int i2) {
        super(context);
        this.p = new Paint(MONITOR);
        this.path = new Path();
        this.r = new RectF();
        this.type = i;
        this.p.setColor(i2);
        this.p.setStrokeCap(Paint.Cap.ROUND);
        this.p.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        float width = ((getWidth() - getPaddingLeft()) - getPaddingRight()) / 24.0f;
        canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop());
        canvas.scale(width, width);
        this.p.setStyle(Paint.Style.STROKE);
        this.p.setStrokeWidth(1.9f);
        this.path.reset();
        switch (this.type) {
            case FOLDER /* 0 */:
                this.r.set(3.0f, 7.0f, 21.0f, 19.5f);
                canvas.drawRoundRect(this.r, 2.5f, 2.5f, this.p);
                this.path.moveTo(3.0f, 10.0f);
                this.path.lineTo(3.0f, 6.2f);
                this.path.quadTo(3.0f, 4.5f, 4.7f, 4.5f);
                this.path.lineTo(9.0f, 4.5f);
                this.path.lineTo(11.5f, 7.0f);
                canvas.drawPath(this.path, this.p);
                break;
            case MONITOR /* 1 */:
                this.r.set(3.0f, 4.0f, 21.0f, 16.0f);
                canvas.drawRoundRect(this.r, 2.5f, 2.5f, this.p);
                canvas.drawLine(9.0f, 20.0f, 15.0f, 20.0f, this.p);
                canvas.drawLine(12.0f, 16.0f, 12.0f, 20.0f, this.p);
                break;
            case SPARKLE /* 2 */:
                this.p.setStyle(Paint.Style.FILL);
                this.path.moveTo(10.5f, 5.5f);
                this.path.quadTo(10.5f, 13.5f, 18.5f, 13.5f);
                this.path.quadTo(10.5f, 13.5f, 10.5f, 21.5f);
                this.path.quadTo(10.5f, 13.5f, 2.5f, 13.5f);
                this.path.quadTo(10.5f, 13.5f, 10.5f, 5.5f);
                this.path.close();
                canvas.drawPath(this.path, this.p);
                this.path.reset();
                this.path.moveTo(19.0f, 2.5f);
                this.path.quadTo(19.0f, 5.0f, 21.5f, 5.0f);
                this.path.quadTo(19.0f, 5.0f, 19.0f, 7.5f);
                this.path.quadTo(19.0f, 5.0f, 16.5f, 5.0f);
                this.path.quadTo(19.0f, 5.0f, 19.0f, 2.5f);
                this.path.close();
                canvas.drawPath(this.path, this.p);
                break;
            case BUBBLE /* 3 */:
                this.r.set(3.5f, 4.5f, 20.5f, 16.5f);
                canvas.drawRoundRect(this.r, 4.5f, 4.5f, this.p);
                this.path.moveTo(8.0f, 16.5f);
                this.path.lineTo(8.0f, 20.5f);
                this.path.lineTo(12.2f, 16.5f);
                canvas.drawPath(this.path, this.p);
                break;
            case ARROW /* 4 */:
                this.p.setStrokeWidth(2.5f);
                canvas.drawLine(12.0f, 19.0f, 12.0f, 5.5f, this.p);
                this.path.moveTo(6.0f, 11.5f);
                this.path.lineTo(12.0f, 5.5f);
                this.path.lineTo(18.0f, 11.5f);
                canvas.drawPath(this.path, this.p);
                break;
            case TERMINAL /* 5 */:
                this.path.moveTo(5.0f, 7.5f);
                this.path.lineTo(10.5f, 12.0f);
                this.path.lineTo(5.0f, 16.5f);
                canvas.drawPath(this.path, this.p);
                canvas.drawLine(12.5f, 17.0f, 19.0f, 17.0f, this.p);
                break;
            case INFO /* 6 */:
                canvas.drawCircle(12.0f, 12.0f, 9.0f, this.p);
                canvas.drawLine(12.0f, 11.0f, 12.0f, 16.5f, this.p);
                this.p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12.0f, 7.8f, 1.2f, this.p);
                break;
            case PLUS /* 7 */:
                this.p.setStrokeWidth(2.5f);
                canvas.drawLine(12.0f, 5.0f, 12.0f, 19.0f, this.p);
                canvas.drawLine(5.0f, 12.0f, 19.0f, 12.0f, this.p);
                break;
            case CHEVRON /* 8 */:
                this.p.setStrokeWidth(2.6f);
                this.path.moveTo(6.0f, 9.0f);
                this.path.lineTo(12.0f, 15.0f);
                this.path.lineTo(18.0f, 9.0f);
                canvas.drawPath(this.path, this.p);
                break;
            case BOLT /* 9 */:
                this.p.setStyle(Paint.Style.FILL);
                this.path.moveTo(13.5f, 2.5f);
                this.path.lineTo(5.0f, 13.5f);
                this.path.lineTo(11.0f, 13.5f);
                this.path.lineTo(10.0f, 21.5f);
                this.path.lineTo(19.0f, 10.0f);
                this.path.lineTo(13.0f, 10.0f);
                this.path.close();
                canvas.drawPath(this.path, this.p);
                break;
            case CHECK /* 10 */:
                this.p.setStrokeWidth(2.6f);
                this.path.moveTo(5.0f, 12.5f);
                this.path.lineTo(10.0f, 17.5f);
                this.path.lineTo(19.0f, 7.0f);
                canvas.drawPath(this.path, this.p);
                break;
            case STAR /* 11 */:
                this.p.setStyle(Paint.Style.FILL);
                this.path.moveTo(12.0f, 2.0f);
                this.path.quadTo(12.0f, 12.0f, 22.0f, 12.0f);
                this.path.quadTo(12.0f, 12.0f, 12.0f, 22.0f);
                this.path.quadTo(12.0f, 12.0f, 2.0f, 12.0f);
                this.path.quadTo(12.0f, 12.0f, 12.0f, 2.0f);
                this.path.close();
                canvas.drawPath(this.path, this.p);
                break;
        }
        canvas.restore();
    }
}
