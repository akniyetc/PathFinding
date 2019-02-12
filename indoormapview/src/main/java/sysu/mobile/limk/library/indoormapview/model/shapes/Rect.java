package sysu.mobile.limk.library.indoormapview.model.shapes;


import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/**
 * Rect
 * @author: Askar Syzdykov
 */
public class Rect extends Shape {

    private RectF rectF;

    public Rect(RectF rectF, String title) {
        super();
        this.rectF = rectF;
        this.title = title;
    }

    @Override
    public boolean contains(PointF point) {
        return rectF.contains(point.x, point.y);
    }

    @Override
    public void draw(Canvas canvas, Matrix currentMatrix) {
        /*float[] goal = {rectF.left, rectF.top, rectF.right, rectF.bottom};
        currentMatrix.mapPoints(goal);
        canvas.drawRect(goal[0], goal[1], goal[2], goal[3], defaultPaint);*/

        List<PointF> points = new ArrayList<>();
        points.add(new PointF(rectF.left, rectF.top));
        points.add(new PointF(rectF.right, rectF.top));
        points.add(new PointF(rectF.right, rectF.bottom));
        points.add(new PointF(rectF.left, rectF.bottom));

        Path path = new Path();
        path.reset(); // only needed when reusing this path for a new build

        PointF startPoint = points.get(0);
        float[] goal = {startPoint.x, startPoint.y};
        currentMatrix.mapPoints(goal);
        path.moveTo(goal[0], goal[1]); // used for first point

        for (int i = 0; i < points.size(); i++) {
            float[] goal1 = {points.get(i).x, points.get(i).y};
            currentMatrix.mapPoints(goal1);
            path.lineTo(goal1[0], goal1[1]);
        }

        path.lineTo(goal[0], goal[1]); // used for first point

        canvas.drawPath(path, defaultPaint);
    }
}
