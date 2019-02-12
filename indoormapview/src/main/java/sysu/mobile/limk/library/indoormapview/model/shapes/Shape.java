package sysu.mobile.limk.library.indoormapview.model.shapes;


import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;

public abstract class Shape {

    public Shape() {
        defaultPaint = new Paint();
        defaultPaint.setAntiAlias(true);
        defaultPaint.setColor(Color.BLUE);
        defaultPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    protected Paint defaultPaint;

    protected String title;

    public Paint getDefaultPaint() {
        return defaultPaint;
    }

    public void setDefaultPaint(Paint defaultPaint) {
        this.defaultPaint = defaultPaint;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public abstract boolean contains(PointF point);

    public abstract void draw(Canvas canvas, Matrix currentMatrix);
}
