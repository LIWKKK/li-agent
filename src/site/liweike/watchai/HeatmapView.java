package site.liweike.watchai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** GitHub-style contribution grid: columns = weeks, rows = weekdays. Values are levels 0..4. */
class HeatmapView extends View {
    static final int[] LEVELS = {0xFF353330, 0xFF5C3B30, 0xFF8A4A37, 0xFFB85F43, 0xFFD97757};
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int weeks, days = 7;
    private final int[][] data;

    HeatmapView(Context c, int weeks, int[][] data) {
        super(c);
        this.weeks = weeks;
        this.data = data;
    }

    @Override protected void onDraw(Canvas cv) {
        float gap = Ui.px(4);
        float cell = (getWidth() - gap * (weeks - 1)) / weeks;
        float rad = cell * 0.26f;
        for (int w = 0; w < weeks; w++) {
            for (int d = 0; d < days; d++) {
                float x = w * (cell + gap), y = d * (cell + gap);
                r.set(x, y, x + cell, y + cell);
                p.setColor(LEVELS[data[w][d]]);
                cv.drawRoundRect(r, rad, rad, p);
            }
        }
    }
}
