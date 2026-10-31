package site.liweike.watchai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Tiny vector icons drawn on a 24-unit grid. */
class IconView extends View {
    static final int FOLDER = 0, MONITOR = 1, SPARKLE = 2, BUBBLE = 3, ARROW = 4, TERMINAL = 5, INFO = 6, PLUS = 7, CHEVRON = 8, BOLT = 9, CHECK = 10;
    private final int type;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    IconView(Context c, int type, int color) {
        super(c);
        this.type = type;
        p.setColor(color);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth() - getPaddingLeft() - getPaddingRight();
        float s = w / 24f;
        cv.save();
        cv.translate(getPaddingLeft(), getPaddingTop());
        cv.scale(s, s);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.9f);
        path.reset();
        switch (type) {
            case FOLDER:
                r.set(3f, 7f, 21f, 19.5f);
                cv.drawRoundRect(r, 2.5f, 2.5f, p);
                path.moveTo(3f, 10f);
                path.lineTo(3f, 6.2f);
                path.quadTo(3f, 4.5f, 4.7f, 4.5f);
                path.lineTo(9f, 4.5f);
                path.lineTo(11.5f, 7f);
                cv.drawPath(path, p);
                break;
            case MONITOR:
                r.set(3f, 4f, 21f, 16f);
                cv.drawRoundRect(r, 2.5f, 2.5f, p);
                cv.drawLine(9f, 20f, 15f, 20f, p);
                cv.drawLine(12f, 16f, 12f, 20f, p);
                break;
            case TERMINAL:
                path.moveTo(5f, 7.5f);
                path.lineTo(10.5f, 12f);
                path.lineTo(5f, 16.5f);
                cv.drawPath(path, p);
                cv.drawLine(12.5f, 17f, 19f, 17f, p);
                break;
            case INFO:
                cv.drawCircle(12f, 12f, 9f, p);
                cv.drawLine(12f, 11f, 12f, 16.5f, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(12f, 7.8f, 1.2f, p);
                break;
            case CHEVRON:
                p.setStrokeWidth(2.6f);
                path.moveTo(6f, 9f);
                path.lineTo(12f, 15f);
                path.lineTo(18f, 9f);
                cv.drawPath(path, p);
                break;
            case CHECK:
                p.setStrokeWidth(2.6f);
                path.moveTo(5f, 12.5f);
                path.lineTo(10f, 17.5f);
                path.lineTo(19f, 7f);
                cv.drawPath(path, p);
                break;
            case BOLT:
                p.setStyle(Paint.Style.FILL);
                path.moveTo(13.5f, 2.5f);
                path.lineTo(5f, 13.5f);
                path.lineTo(11f, 13.5f);
                path.lineTo(10f, 21.5f);
                path.lineTo(19f, 10f);
                path.lineTo(13f, 10f);
                path.close();
                cv.drawPath(path, p);
                break;
            case PLUS:
                p.setStrokeWidth(2.5f);
                cv.drawLine(12f, 5f, 12f, 19f, p);
                cv.drawLine(5f, 12f, 19f, 12f, p);
                break;
            case SPARKLE:
                p.setStyle(Paint.Style.FILL);
                path.moveTo(10.5f, 5.5f);
                path.quadTo(10.5f, 13.5f, 18.5f, 13.5f);
                path.quadTo(10.5f, 13.5f, 10.5f, 21.5f);
                path.quadTo(10.5f, 13.5f, 2.5f, 13.5f);
                path.quadTo(10.5f, 13.5f, 10.5f, 5.5f);
                path.close();
                cv.drawPath(path, p);
                path.reset();
                path.moveTo(19f, 2.5f);
                path.quadTo(19f, 5f, 21.5f, 5f);
                path.quadTo(19f, 5f, 19f, 7.5f);
                path.quadTo(19f, 5f, 16.5f, 5f);
                path.quadTo(19f, 5f, 19f, 2.5f);
                path.close();
                cv.drawPath(path, p);
                break;
            case BUBBLE:
                r.set(3.5f, 4.5f, 20.5f, 16.5f);
                cv.drawRoundRect(r, 4.5f, 4.5f, p);
                path.moveTo(8f, 16.5f);
                path.lineTo(8f, 20.5f);
                path.lineTo(12.2f, 16.5f);
                cv.drawPath(path, p);
                break;
            case ARROW:
                p.setStrokeWidth(2.5f);
                cv.drawLine(12f, 19f, 12f, 5.5f, p);
                path.moveTo(6f, 11.5f);
                path.lineTo(12f, 5.5f);
                path.lineTo(18f, 11.5f);
                cv.drawPath(path, p);
                break;
            default:
                break;
        }
        cv.restore();
    }
}
