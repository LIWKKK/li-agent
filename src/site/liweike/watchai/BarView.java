package site.liweike.watchai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Horizontal rounded bar made of one or more colored segments. */
class BarView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF r = new RectF();
    private float[] fr = new float[0];
    private int[] col = new int[0];

    BarView(Context c) { super(c); }

    BarView set(float[] fractions, int[] colors) {
        fr = fractions;
        col = colors;
        invalidate();
        return this;
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        r.set(0, 0, w, h);
        p.setColor(Ui.CHIP);
        cv.drawRoundRect(r, h / 2f, h / 2f, p);
        clip.reset();
        clip.addRoundRect(r, h / 2f, h / 2f, Path.Direction.CW);
        cv.save();
        cv.clipPath(clip);
        float x = 0;
        for (int i = 0; i < fr.length; i++) {
            p.setColor(col[i]);
            float x2 = x + fr[i] * w;
            cv.drawRect(x, 0, x2, h, p);
            x = x2;
        }
        cv.restore();
    }
}
