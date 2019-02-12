package sysu.mobile.limk.library.indoormapview.model.shapes;


import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PointF;

import java.util.List;

/**
 * Polygon
 * @author: Askar Syzdykov
 */
public class Polygon extends Shape {

    private List<PointF> points;

    public Polygon(List<PointF> points) {
        this("", points);
    }

    public Polygon(String title, List<PointF> points) {
        super();
        this.title = title;
        this.points = points;
    }

    public List<PointF> getPoints() {
        return points;
    }

    /**
     * Return true if the given point is contained inside the boundary.
     * See: http://www.ecse.rpi.edu/Homepages/wrf/Research/Short_Notes/pnpoly.html
     *
     * @param point The point to check
     * @return true if the point is inside the boundary, false otherwise
     */
    public boolean contains(PointF point) {
        int i;
        int j;
        boolean result = false;
        for (i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            if ((points.get(i).y > point.y) != (points.get(j).y > point.y) &&
                    (point.x < (points.get(j).x - points.get(i).x) * (point.y - points.get(i).y) / (points.get(j).y - points.get(i).y) + points.get(i).x)) {
                result = !result;
            }
        }
        return result;
    }

    @Override
    public void draw(Canvas canvas, Matrix currentMatrix) {
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
