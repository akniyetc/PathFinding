package sysu.mobile.limk.library.indoormapview;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

import java.util.List;

import sysu.mobile.limk.library.indoormapview.PathFinding.Pixel;

public class PathFindingSymbol extends BaseMapSymbol {

    private List<Pixel> mPixelList;
    private Paint nodePaint;
    private int nodeRadius = 6;

    public PathFindingSymbol() {
        this.mVisibility = true;

        nodePaint = new Paint();
        nodePaint.setColor(Color.RED);
        nodePaint.setStyle(Paint.Style.FILL);
    }

    public void setPixelList(List<Pixel> pixelList) {
        this.mPixelList = pixelList;
    }

    @Override
    public void draw(Canvas canvas, Matrix matrix, float scale) {
        if (!mVisibility || scale < mThreshold || mPixelList == null)
            return;

        canvas.save();

        /*for (int i = 0; i < mPixelList.size() - 1; i++) {

            *//*float[] locationValue = new float[]{(float) mPixelList.get(i).getХ(),
                    (float) mPixelList.get(i).getY()};
            matrix.mapPoints(locationValue);

            canvas.drawCircle(locationValue[0], locationValue[1], nodeRadius,
                    nodePaint);*//*

            float[] locationValue = new float[]{(float) mPixelList.get(i).getХ(),
                    (float) mPixelList.get(i).getY()};
            float[] locationValue1 = new float[]{(float) mPixelList.get(i + 1).getХ(),
                    (float) mPixelList.get(i + 1).getY()};
            matrix.mapPoints(locationValue);
            matrix.mapPoints(locationValue1);
            nodePaint.setStrokeWidth(10);
            canvas.drawLine(locationValue[0], locationValue[1], locationValue1[0], locationValue1[1], nodePaint);
        }*/

        canvas.restore();
    }

    @Override
    public boolean isPointInClickRect(float x, float y) {
        return false;
    }
}
