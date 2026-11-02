package site.liweike.watchai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/* JADX INFO: loaded from: classes.dex */
class BarView extends View {
    private final Path clip;
    private int[] col;
    private float[] fr;
    private final Paint p;
    private final RectF r;

    BarView(Context context) {
        super(context);
        this.p = new Paint(1);
        this.clip = new Path();
        this.r = new RectF();
        this.fr = new float[0];
        this.col = new int[0];
    }

    BarView set(float[] fArr, int[] iArr) {
        this.fr = fArr;
        this.col = iArr;
        invalidate();
        return this;
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        this.r.set(0.0f, 0.0f, width, height);
        this.p.setColor(-13093325);
        canvas.drawRoundRect(this.r, height / 2.0f, height / 2.0f, this.p);
        this.clip.reset();
        this.clip.addRoundRect(this.r, height / 2.0f, height / 2.0f, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(this.clip);
        int i = 0;
        float f = 0.0f;
        while (i < this.fr.length) {
            this.p.setColor(this.col[i]);
            float f2 = f + (this.fr[i] * width);
            canvas.drawRect(f, 0.0f, f2, height, this.p);
            i++;
            f = f2;
        }
        canvas.restore();
    }
}
