package sysu.mobile.limk.library.indoormapview;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.Pair;

import java.util.List;

import sysu.mobile.limk.library.indoormapview.PathFinding.Pixel;

public class ObstacleSymbol extends BaseMapSymbol {

    List<Pair<Pixel, Pixel>> pairs;
    Pair<Pixel, Pixel> pair;
    private RectF mRect;
    private Paint rectPaint;
    private RectF mRectDst = new RectF();

    float centerX;
    float centerY;

    PointF point;

    public float getDegree() {
        return degree;
    }

    public void setDegree(float degree, PointF pointF) {
        this.degree = degree;
        point = pointF;
    }

    private float degree;


    public ObstacleSymbol(List<Pair<Pixel, Pixel>> pairs) {
        mVisibility = true;
        this.pairs = pairs;
        pair = pairs.get(0);

        mRect = new RectF();

        rectPaint = new Paint();
        rectPaint.setColor(Color.BLUE);

        centerX = Math.abs(((float) pair.first.getХ() + (float) pair.second.getХ()) / 2);
        centerY = Math.abs((float) pair.first.getY() + (float) pair.second.getY()) / 2;
    }

    @Override
    public void draw(Canvas canvas, Matrix matrix, float scale) {
        canvas.save();

        float[] locationValue = new float[]{(float) pair.first.getХ(),
                (float) pair.second.getY(), (float) pair.second.getХ(),(float) pair.first.getY()};
        matrix.mapPoints(locationValue);

        mRect.set(
                locationValue[0],
                locationValue[1],
                locationValue[2],
                locationValue[3]);


        //matrix.mapRect(mRect);
        /*if (point != null) {
            matrix.postRotate(degree, point.x, point.y);
        }*/
        canvas.drawRect(mRect, rectPaint);
        canvas.restore();
    }

    @Override
    public boolean isPointInClickRect(float x, float y) {
        return false;
    }
}
