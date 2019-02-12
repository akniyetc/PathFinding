package sysu.mobile.limk.library.indoormapview.PathFinding;

public class Pixel {

    private double х;
    private double y;
    private int state;

    public Pixel(double x, double y, int state) {
        this.х = x;
        this.y = y;
        this.state = state;
    }

    public Pixel(double x, double y) {
        this.х = x;
        this.y = y;
        state = TYPE_OPEN;
    }

    public double getХ() {
        return х;
    }

    public void setХ(double х) {
        this.х = х;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public static int TYPE_OPEN = 0;
    public static int TYPE_WALL = 1;
    public static int TYPE_DYNAMYC_OBJECT = 2;
    public static int TYPE_START_POSITION = 3;
    public static int TYPE_END_POSITION = 4;
}
