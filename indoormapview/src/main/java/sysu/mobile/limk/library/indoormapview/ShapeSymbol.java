package sysu.mobile.limk.library.indoormapview;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.List;

import sysu.mobile.limk.library.indoormapview.model.shapes.Shape;

/**
 * ShapeLayer
 *
 * @author: Askar Syzdykov
 */
public class ShapeSymbol extends BaseMapSymbol {

    private final String TAG = "PolygonLayer";

    private Paint defaultPaint;

    private List<Shape> shapes;

    private OnPolygonClickListener listener;

    public ShapeSymbol(OnPolygonClickListener listener) {
        this.listener = listener;

        initLayer();
    }

    public ShapeSymbol() {
        initLayer();
    }

    private void initLayer() {
        mVisibility = true;
        shapes = new ArrayList<>();

        defaultPaint = new Paint();
        defaultPaint.setAntiAlias(true);
        defaultPaint.setColor(Color.BLUE);
        defaultPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    public void setDefaultPaint(Paint paint) {
        if (paint == null) {
            throw new NullPointerException("Paint can't be null");
        }
        this.defaultPaint = paint;
    }

    public void addShape(Shape shape) {
        shapes.add(shape);
    }

    public void setOnPolygonClickListener(OnPolygonClickListener listener) {
        this.listener = listener;
    }

    @Override
    public void draw(Canvas canvas, Matrix matrix, float scale) {

        if (!mVisibility || scale < mThreshold || shapes == null)
            return;


        canvas.save();
        for (Shape shape : shapes) {
            shape.draw(canvas, matrix);
        }

        canvas.restore();

    }

    public interface OnPolygonClickListener {
        void onPolygonClick(Shape polygon);
    }

    @Override
    public boolean isPointInClickRect(float x, float y) {
        return false;
    }
}
