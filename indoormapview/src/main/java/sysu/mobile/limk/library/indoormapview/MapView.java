package sysu.mobile.limk.library.indoormapview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import sysu.mobile.limk.library.indoormapview.PathFinding.ExampleNode;
import sysu.mobile.limk.library.indoormapview.PathFinding.PathFindingTool;
import sysu.mobile.limk.library.indoormapview.PathFinding.Pixel;
import sysu.mobile.limk.library.indoormapview.backend.Block;
import sysu.mobile.limk.library.indoormapview.mathUtils.MapMath;
import sysu.mobile.limk.library.indoormapview.model.shapes.Polygon;

public class MapView extends RelativeLayout {
    // attributes
    private boolean mDebug;

    private int mMyLocationColor = 0xFFFF0000;
    private int mCircleEdgeColor = 0xFFFFFFFF;
    private int mMyLocationRangeColor = 0x1EFF0000;
    private float mMyLocationRadius = 64;
    private float mMyLocationRangeRadius = 64;

    private float mMinScale = 0.1f;
    private float mMaxScale = 4f;

    private float mRestrictSize = 200f;

    // local parameters
    private float mScale = 1;
    private long mFloorId;
    private float mInitRotation = 0;
    private float mInitScale = 1;
    private boolean mIsTrackPosition = false;
    private boolean mInitMapView = false;

    private Matrix mMapMatrix = new Matrix();
    private Matrix mDetailMapMatrix = new Matrix();
    private Matrix mShadowMapMatrix = new Matrix();
    private BitmapRegionDecoder mMapDecoder;
    private Bitmap mShadowBitmap;
    private Bitmap mDetailBitmap;
    private Handler mDecodeBitmapHandler;
    private Context mContext;
    private Rect mScreenRect = null;
    private LocationSymbol mMyLocationSymbol;
    private RealLocationSymbol mRealLocationSymbol;

    // for the gesture detection
    private boolean mIsMoved = false;
    private boolean mIsRealLocationMove = false;

    private PointF[] mPreviousTouchPoints = {new PointF(), new PointF()};
    private int mPreviousPointerCount = 0;

    public ReentrantReadWriteLock mMapLock = new ReentrantReadWriteLock();

    // data container
    private List<BaseMapSymbol> mMapSymbols = new ArrayList<>();

    private OnRealLocationMoveListener mOnRealLocationMoveListener = null;
    private TranslateAnimRunnable mTranslateAnimRunnable = null;
    private UpdateMyLocationAnimRunnable mUpdateMyLocationAnimRunnable = null;

    PathFindingTool pathFinder;

    private PathFindingSymbol mPathFindingSymbol;
    ShapeSymbol mShapeSymbol;
    Pixel pixels[][];

    ImageButton btnCompass;

    private float oldDist = 0, oldDegree = 0;
    private float saveRotateDegrees = 0.0f;

    private RouteAnimRunnable mRouteAnimRunnable;


    private class TranslateAnimRunnable implements Runnable {

        private float mMoveToX;
        private float mMoveToY;
        private boolean mIsRunning = false;

        TranslateAnimRunnable(float x, float y) {
            mMoveToX = x;
            mMoveToY = y;
        }

        @Override
        public void run() {
            mIsRunning = true;
            try {
                while (!mInitMapView) {
                    Thread.sleep(50);
                }
                while (true) {
                    mMapLock.writeLock().lock();
                    float[] pointValue = new float[]{mMoveToX, mMoveToY};
                    mMapMatrix.mapPoints(pointValue);
                    PointF step = new PointF(
                            (getWidth() / 2f - pointValue[0]) / 2,
                            (getHeight() / 2f - pointValue[1]) / 2);
                    if (Math.abs(step.x) > 10 || Math.abs(step.y) > 10) {
                        mMapMatrix.postTranslate(step.x, step.y);
                        calScreenRect();
                        if (getHandler() != null) {
                            getHandler().post(new Runnable() {
                                @Override
                                public void run() {
                                    invalidate();
                                }
                            });
                        }
                        mMapLock.writeLock().unlock();
                    } else {
                        refreshDetailBitmap();
                        mMapLock.writeLock().unlock();
                        break;
                    }
                    Thread.sleep(30);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            mIsRunning = false;
        }

        void setMoveToPoint(float x, float y) {
            mMoveToX = x;
            mMoveToY = y;
        }

        boolean isRunning() {
            return mIsRunning;
        }

    }

    private class UpdateMyLocationAnimRunnable implements Runnable {

        private float[] mTargetPoint;
        private boolean mIsRunning = false;

        UpdateMyLocationAnimRunnable(float x, float y) {
            mTargetPoint = new float[2];
            mTargetPoint[0] = x;
            mTargetPoint[1] = y;
        }

        boolean isRunning() {
            return mIsRunning;
        }

        void setTarget(float x, float y) {
            if (mTargetPoint == null)
                mTargetPoint = new float[2];
            mTargetPoint[0] = x;
            mTargetPoint[1] = y;
        }

        @Override
        public void run() {
            while (true) {
                mIsRunning = true;
                PointF step = new PointF((float) (mTargetPoint[0] - mMyLocationSymbol
                        .getLocation().getX()) / 4,
                        (float) (mTargetPoint[1] - mMyLocationSymbol.getLocation()
                                .getY()) / 4);
                if (Math.abs(step.x) > 1 || Math.abs(step.y) > 1) {
                    mMyLocationSymbol.getLocation().setX(
                            mMyLocationSymbol.getLocation().getX() + step.x);
                    mMyLocationSymbol.getLocation().setY(
                            mMyLocationSymbol.getLocation().getY() + step.y);
                    if (getHandler() != null) {
                        getHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                invalidate();
                            }
                        });
                    }
                } else {
                    mMyLocationSymbol.getLocation().setX(mTargetPoint[0]);
                    mMyLocationSymbol.getLocation().setY(mTargetPoint[1]);
                    if (getHandler() != null) {
                        getHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                invalidate();
                            }
                        });
                    }
                    break;
                }
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (mIsTrackPosition) {
                centerMyLocation();
            }
            mIsRunning = false;

        }

    }

    private void initRouteMatrix() {
        int width = mMapDecoder.getWidth() / 20;
        int height = mMapDecoder.getHeight() / 20;

        pixels = new Pixel[width - 1][height - 1];
        pathFinder = new PathFindingTool(width, height);

        int yValue = 0;

        for (int y = 0; y < height - 1; y++) {
            yValue += 20;
            int xValue = 0;
            for (int x = 0; x < width - 1; x++) {
                xValue += 20;
                pixels[x][y] = new Pixel(xValue, yValue, Pixel.TYPE_OPEN);
                pathFinder.setBlock(x, y, false);
            }
        }

        pixels[0][1].setState(Pixel.TYPE_START_POSITION);
        pixels[16][6].setState(Pixel.TYPE_END_POSITION);

        pixels[4][1].setState(Pixel.TYPE_WALL);
        //pixels[16][7].setState(Pixel.TYPE_END_POSITION);

        pathFinder.setBlock(0, 1, false);
        pathFinder.setBlock(16, 7, false);

        pathFinder.setBlock(4, 1, true);
        pathFinder.setBlock(6, 2, true);

        for (Block block : blocks) {
            for (double k = block.getX(); k < block.getX() + block.getWidth(); k++) {
                for (double n = block.getY(); n < block.getY() + block.getHeight(); n++) {
                    pathFinder.setBlock((int) k, (int) n, true);
                }
            }
        }
    }


    private class RouteAnimRunnable implements Runnable {

        public RouteAnimRunnable() {
            mPosition = new Position(0, 0);
        }

        private boolean mIsRunning = false;

        public void setPosition(Position position) {
            mPosition = position;
        }

        private Position mPosition;

        boolean isRunning() {
            return mIsRunning;
        }

        @Override
        public void run() {
            while (true) {
                mIsRunning = true;

                drawRoute(mPosition);
                if (getHandler() != null) {
                    getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            invalidate();
                        }
                    });
                }
                break;
            }
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mIsRunning = false;
        }

    }
    public void drawRoute(Position mPosition) {

        double minDistance = Double.MAX_VALUE;

        double startX = 0;
        double startY = 0;

        double x1 = mPosition.getX();
        double y1 = mPosition.getY();

        for (int k = 0; k < pixels.length - 1; k++) {
            for (int n = 0; n < pixels[k].length - 1; n++) {
                double x2 = pixels[k][n].getХ();
                double y2 = pixels[k][n].getY();

                double distance = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));

                if (distance < minDistance) {
                    minDistance = distance;
                    startX = k;
                    startY = n;
                }
            }
        }
        long startSeconds = System.currentTimeMillis();
        List<ExampleNode> nodes = pathFinder.findPath((int) startX, (int) startY, 35, 35);
        long endSeconds = System.currentTimeMillis();

        Log.d("suka", String.valueOf(endSeconds - startSeconds));

        List<Pixel> pixelList = new ArrayList<>();
        Pixel currentPixel;
        Pixel nextFirstPixel;
        Pixel nextSecondPixel;
        int nextFirstIndex;
        int nextSecondIndex;

        for (int i = 0; i < nodes.size(); i++) {
            nextFirstIndex = (i < nodes.size() - 1) ? i + 1 : i;
            nextSecondIndex = (i < nodes.size() - 2) ? i + 2 : i;
            currentPixel = pixels[nodes.get(i).getxPosition()][nodes.get(i).getyPosition()];
            nextFirstPixel = pixels[nodes.get(nextFirstIndex).getxPosition()][nodes.get(nextFirstIndex).getyPosition()];
            nextSecondPixel = pixels[nodes.get(nextSecondIndex).getxPosition()][nodes.get(nextSecondIndex).getyPosition()];

            double angle = angle(currentPixel, nextFirstPixel, nextSecondPixel);

            if (Math.abs(angle) != 180) {
                pixelList.add(nextFirstPixel);
            }
        }
        pixelList.add(0, new Pixel(mPosition.getX(), mPosition.getY()));

        mPathFindingSymbol.setPixelList(pixelList);
    }

    private double angle(Pixel first, Pixel second, Pixel third) {
        Pixel v1 = new Pixel(first.getХ() - second.getХ(), first.getY() - second.getY());
        Pixel v2 = new Pixel(third.getХ() - second.getХ(), third.getY() - second.getY());
        double angle = Math.atan2(v2.getY(), v2.getХ()) - Math.atan2(v1.getY(), v1.getХ());
        return angle * (180 / Math.PI);
    }

    public MapView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        analyzeParams(context, attrs);
        init();
    }

    public MapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        analyzeParams(context, attrs);
        init();
    }

    private void analyzeParams(Context context, AttributeSet attrs) {
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.MapView, 0, 0);
        try {
            mDebug = a.getBoolean(R.styleable.MapView_debug, false);
            mMaxScale = a.getFloat(R.styleable.MapView_max_scale, 4f);
            mMinScale = a.getFloat(R.styleable.MapView_min_scale, 0.1f);
            mMyLocationColor = a.getColor(R.styleable.MapView_mylocation_color, 0xFFFF0000);
            mCircleEdgeColor = a.getColor(R.styleable.MapView_circle_edge_color, 0xFFFFFFFF);
            mMyLocationRangeColor = a.getColor(R.styleable.MapView_mylocation_range_color, 0x1EFF0000);
            mMyLocationRadius = a.getDimension(R.styleable.MapView_mylocation_radius, 10);
            mMyLocationRangeRadius = a.getDimension(R.styleable.MapView_mylocation_range_radius, 20);
        } finally {
            a.recycle();
        }
    }

    List<Block> blocks = new ArrayList<>();

    private void init() {
        mContext = getContext();
        mMyLocationSymbol = new LocationSymbol(mMyLocationColor,
                mCircleEdgeColor, mMyLocationRadius);
        mMyLocationSymbol.setRangeCircle(mMyLocationRangeRadius,
                mMyLocationRangeColor);

        mShapeSymbol = new ShapeSymbol();
        mPathFindingSymbol = new PathFindingSymbol();
        setBackgroundColor(Color.GRAY);
        HandlerThread decodeBitmapThread = new HandlerThread("decodeBitmap");
        decodeBitmapThread.start();
        mDecodeBitmapHandler = new Handler(decodeBitmapThread.getLooper());

        blocks.add(new Block(40, 34, 4, 4));
        initCompassButton();
    }

    public void drawPath(Position position) {
        if (mRouteAnimRunnable == null) {
            mRouteAnimRunnable = new RouteAnimRunnable();
        } else {
            mRouteAnimRunnable.setPosition(position);
        }
        if (!mRouteAnimRunnable.isRunning()) {
            new Thread(mRouteAnimRunnable).start();
        }
    }


    public void drawObstacles() {
        List<PointF> cornerPoints = new ArrayList<>();
        for (Block block : blocks) {
            Pixel p1 = pixels[(int) block.getX()][(int) (block.getY())];
            Pixel p2 = pixels[(int) (block.getX() + block.getWidth())][(int) block.getY()];
            Pixel p3 = pixels[(int) (block.getX() + block.getWidth())][(int) (block.getY() + block.getHeight())];
            Pixel p4 = pixels[(int) block.getX()][(int) (block.getY() + block.getHeight())];

            cornerPoints.add(new PointF((float) p1.getХ(), (float) p1.getY()));
            cornerPoints.add(new PointF((float) p2.getХ(), (float) p2.getY()));
            cornerPoints.add(new PointF((float) p3.getХ(), (float) p3.getY()));
            cornerPoints.add(new PointF((float) p4.getХ(), (float) p4.getY()));
        }


        /*List<Pair<Pixel, Pixel>> pairs = new ArrayList<>();
        Pair<Pixel, Pixel> pair = new Pair<>(p1, p2);
        pairs.add(pair);*/

        Polygon polygon = new Polygon(cornerPoints);

        mShapeSymbol.addShape(polygon);
    }

    private PointF mid = new PointF();
    private float currentDegree = 0f;
    private PointF startTouch = new PointF();

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mMapLock.isWriteLocked() || mShadowBitmap == null) {
            return super.onTouchEvent(event);
        }
        float newDegree;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_UP:
                if ((!mIsMoved) && event.getPointerCount() == 1) {
                    onClick(event.getX(), event.getY(), mMapMatrix, mScale);
                }
                refreshDetailBitmap();
                mIsMoved = false;
                mIsRealLocationMove = false;
                mRealLocationSymbol.setMoving(mIsRealLocationMove);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                mPreviousTouchPoints[0].set(event.getX(), event.getY());
                if (event.getPointerCount() > 1)
                    mPreviousTouchPoints[1].set(event.getX(1), event.getY(1));
                mPreviousPointerCount = event.getPointerCount();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                mid = midPoint(event);
                oldDist = distance(event, mid);
                oldDegree = rotation(event, mid);
                saveRotateDegrees = currentDegree;
                startTouch.set(event.getX(0), event.getY(0));
                break;
            case MotionEvent.ACTION_DOWN:

                float[] xy = {event.getX(), event.getY()};
                xy = transformToMapCoordinate(xy);
                if (mRealLocationSymbol.isPointInClickRect(xy[0], xy[1])
                        && mRealLocationSymbol.mOnMapSymbolListener != null) {
                    mIsRealLocationMove = true;
                    mRealLocationSymbol.setMoving(mIsRealLocationMove);
                }

                mPreviousTouchPoints[0].set(event.getX(), event.getY());
                if (event.getPointerCount() > 1)
                    mPreviousTouchPoints[1].set(event.getX(1), event.getY(1));
                mPreviousPointerCount = event.getPointerCount();
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1
                        && (event.getX() - mPreviousTouchPoints[0].x)
                        * (event.getX() - mPreviousTouchPoints[0].x)
                        + (event.getY() - mPreviousTouchPoints[0].y)
                        * (event.getY() - mPreviousTouchPoints[0].y) < 1)
                    break;
                if (event.getPointerCount() >= 2
                        && MathUtil.squareDistance(
                        new PointF(event.getX(), event.getY()),
                        mPreviousTouchPoints[0]) < 20
                        && MathUtil.squareDistance(
                        new PointF(event.getX(1), event.getY(1)),
                        mPreviousTouchPoints[1]) < 20)
                    break;
                mIsMoved = true;
                if (event.getPointerCount() == mPreviousPointerCount) {
                    if (event.getPointerCount() == 1) {
                        // Judge is dragging the real locaiton symbol
                        if (!mIsRealLocationMove) {
                            float delX = event.getX() - mPreviousTouchPoints[0].x;
                            float delY = event.getY() - mPreviousTouchPoints[0].y;

                            // get the coor of real map
                            float[] left_top = transformToViewCoordinate(
                                    new float[]{0, 0});
                            float[] right_bottom = transformToViewCoordinate(
                                    new float[]{mMapDecoder.getWidth(), mMapDecoder.getHeight()});
                            float[] left_bottom = transformToViewCoordinate(
                                    new float[]{0, mMapDecoder.getHeight()});
                            float[] right_top = transformToViewCoordinate(
                                    new float[]{mMapDecoder.getWidth(), 0});

                            float left = min(new float[]{left_top[0], right_top[0], left_bottom[0], right_bottom[0]});
                            float right = max(new float[]{left_top[0], right_top[0], left_bottom[0], right_bottom[0]});

                            float top = min(new float[]{left_top[1], right_top[1], left_bottom[1], right_bottom[1]});
                            float bottom = max(new float[]{left_top[1], right_top[1], left_bottom[1], right_bottom[1]});

                            if ((left + delX > getWidth() - mRestrictSize && delX > 0)
                                    || (right + delX < mRestrictSize && delX < 0)) {
                                delX = 0;
                            }

                            if ((top + delY > getHeight() - mRestrictSize && delY > 0)
                                    || (bottom + delY < mRestrictSize && delY < 0)) {
                                delY = 0;
                            }

                            mMapMatrix.postTranslate(delX, delY);
                            calScreenRect();
                            Log.v("onTouch", "trans:" + delX + "," + delY + " matrix:" + mMapMatrix.toShortString());
                            onTranslate(delX, delY, mMapMatrix, mScale);
                        } else {
                            Position curPos = new Position();
                            float delX = event.getX() - mPreviousTouchPoints[0].x;
                            float delY = event.getY() - mPreviousTouchPoints[0].y;

                            float[] viewPos = transformToViewCoordinate(
                                    new float[]{(float) mRealLocationSymbol.getLocation().getX(),
                                            (float) mRealLocationSymbol.getLocation().getY()});
                            float[] mapPos = transformToMapCoordinate(new float[]{viewPos[0] + delX, viewPos[1] + delY});
                            curPos.setX(mapPos[0]);
                            curPos.setY(mapPos[1]);
                            mRealLocationSymbol.setLocation(curPos);

                            if (mOnRealLocationMoveListener != null) {
                                mOnRealLocationMoveListener.onMove(curPos);
                            }
                            float x = oldDist;
                            float y = MapMath.getDistanceBetweenTwoPoints(event.getX(0),
                                    event.getY(0), startTouch.x, startTouch.y);
                            float z = distance(event, mid);
                            float cos = (x * x + y * y - z * z) / (2 * x * y);

                            float degree = (float) Math.toDegrees(Math.acos(cos));

                            if (degree < 120 && degree > 45) {
                                oldDegree = rotation(event, mid);
                            } else {
                                oldDist = distance(event, mid);
                            }
                        }
                    } else {
                        PointF preMid = MathUtil.midPoint(mPreviousTouchPoints[0],
                                mPreviousTouchPoints[1]);
                        PointF curMid = MathUtil.midPoint(new PointF(event.getX(),
                                        event.getY()),
                                new PointF(event.getX(1), event.getY(1)));
                        double preDis = MathUtil.distance(mPreviousTouchPoints[0],
                                mPreviousTouchPoints[1]);
                        double curDis = MathUtil.distance(new PointF(event.getX(),
                                        event.getY()),
                                new PointF(event.getX(1), event.getY(1)));
                        if (MathUtil.distance(curMid, preMid) > 8) {
                            mMapMatrix.postTranslate(curMid.x - preMid.x, curMid.y
                                    - preMid.y);
                            calScreenRect();
                        }
                        float scale = (float) (curDis / preDis);
                        Log.v("scale", scale + "#####" + mScale);
                        if ((scale >= 1 && mScale < mMaxScale)
                                || (scale <= 1 && mScale > mMinScale)) {
                            mMapMatrix.postScale(scale, scale, preMid.x, preMid.y);
                            updateScale();
                            onScale(scale, scale, mMapMatrix, mScale);
                        }
                        double preAngle = MathUtil.angle(mPreviousTouchPoints[0],
                                preMid);
                        double curAngle = MathUtil.angle(new PointF(event.getX(),
                                event.getY()), curMid);
                        float delAngle = (float) ((curAngle - preAngle) / Math.PI * 180);

                        //obstacleSymbol.setDegree(delAngle, preMid);
                        mMapMatrix.postRotate(delAngle, preMid.x, preMid.y);
                        calScreenRect();
                        onRotate(delAngle, preMid.x, preMid.y, mMapMatrix, mScale);
                        onRotatePre((float) preAngle, preMid.x, preMid.y, mMapMatrix, mScale);
                        onRotateCur((float) curAngle, preMid.x, preMid.y, mMapMatrix, mScale);

                        newDegree = rotation(event, preMid);
                        float rotate = newDegree - oldDegree;
                        currentDegree = (rotate + saveRotateDegrees) % 360;
                        currentDegree = currentDegree > 0 ? currentDegree :
                                currentDegree + 360;
                        btnCompass.animate().rotation(currentDegree).setInterpolator(new AccelerateDecelerateInterpolator());
                    }
                }
                invalidate();
                break;
            default:
                break;
        }
        if (event.getPointerCount() == 1) {
            mPreviousTouchPoints[0].set(event.getX(), event.getY());
        } else if (event.getPointerCount() > 1) {
            mPreviousTouchPoints[0].set(event.getX(), event.getY());
            mPreviousTouchPoints[1].set(event.getX(1), event.getY(1));
        }
        mPreviousPointerCount = event.getPointerCount();
        return true;
    }

    private float rotation(MotionEvent event, PointF mid) {
        return MapMath.getDegreeBetweenTwoPoints(event.getX(0), event.getY(0)
                , mid.x, mid.y);
    }

    private PointF midPoint(MotionEvent event) {
        return MapMath.getMidPointBetweenTwoPoints(event.getX(0), event.getY(0)
                , event.getX(1), event.getY(1));
    }

    private float distance(MotionEvent event, PointF mid) {
        return MapMath.getDistanceBetweenTwoPoints(event.getX(0), event.getY(0)
                , mid.x, mid.y);
    }

    private void initCompassButton() {
        btnCompass = new ImageButton(getContext());
        btnCompass.setBackgroundColor(Color.TRANSPARENT);
        btnCompass.setImageDrawable(getResources().getDrawable(R.mipmap.ic_compass_direction));
        btnCompass.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                /*setCurrentRotateDegrees(0);
                btnCompass.animate().rotation(currentDegree).setInterpolator(new AccelerateDecelerateInterpolator());*/
                invalidate();
            }
        });

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
        params.leftMargin = 16;
        params.topMargin = 16;

        addView(btnCompass, params);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Log.d("On Draw", mMapLock.isWriteLocked() + "");

        canvas.drawColor(Color.GRAY);
        mMapLock.readLock().lock();
        Matrix curMapMatrix = new Matrix(mMapMatrix);
        Matrix shadowMatrix = new Matrix(mShadowMapMatrix);
        shadowMatrix.postConcat(curMapMatrix);
        if (mShadowBitmap != null) {
            canvas.drawBitmap(mShadowBitmap, shadowMatrix, null);
            if (mDetailBitmap != null) {
                Matrix detailMatrix = new Matrix(mDetailMapMatrix);
                detailMatrix.postConcat(curMapMatrix);
                canvas.drawBitmap(mDetailBitmap, detailMatrix, null);
            }
            drawMapSymbols(canvas);
            if (mMyLocationSymbol != null
                    && mMyLocationSymbol.getLocation() != null) {
                mMyLocationSymbol.draw(canvas, mMapMatrix, mScale);
            }

            if (mRealLocationSymbol != null && mRealLocationSymbol.getLocation() != null) {
                mRealLocationSymbol.draw(canvas, mMapMatrix, mScale);
            }

            if (mPathFindingSymbol != null) {
                mPathFindingSymbol.draw(canvas, mMapMatrix, mScale);
            }

            if (mShapeSymbol != null) {
                mShapeSymbol.draw(canvas, mMapMatrix, mScale);
            }
        } else {
            canvas.drawColor(getResources().getColor(
                    android.R.color.holo_blue_light));
        }
        mMapLock.readLock().unlock();
    }

    private void drawMapSymbols(Canvas canvas) {
        if (mMapSymbols != null) {
            for (int i = 0; i < mMapSymbols.size(); i++) {
                BaseMapSymbol mapSymbol = mMapSymbols.get(i);
                mapSymbol.draw(canvas, mMapMatrix, mScale);
            }
        }
    }

    private void onRotate(float angle, float centerX, float centerY,
                          Matrix matrix, float scale) {
        Log.v("callbackRotate", "onRotate" + " center: (" + centerX + ","
                + centerY + ")" + " angleDel:" + angle);
    }

    private void onRotatePre(float angle, float centerX, float centerY,
                             Matrix matrix, float scale) {
        Log.v("callbackRotate", "onRotatePre" + " center: (" + centerX + ","
                + centerY + ")" + " anglePre:" + angle);
    }

    private void onRotateCur(float angle, float centerX, float centerY,
                             Matrix matrix, float scale) {
        Log.v("callbackRotate", "onRotateCur" + " center: (" + centerX + ","
                + centerY + ")" + " angleCur:" + angle);
    }

    private void onScale(float xScale, float yScale, Matrix matrix, float scale) {
        Log.v("callback", "onTranslate" + " ScaleDel: (" + xScale + ","
                + yScale + ")" + " matrix:" + matrix + " scale:" + scale);
    }

    private void onTranslate(float tranX, float tranY, Matrix matrix,
                             float scale) {
        mIsTrackPosition = false;
        Log.v("callback", "onTranslate" + " transDis: (" + tranX + "," + tranY
                + ")" + " matrix:" + matrix + " scale:" + scale);
    }

    private void onClick(float x, float y, Matrix matrix, float scale) {
        Log.v("callback", "onClick" + " Position: (" + x + "," + y + ")"
                + " matrix:" + matrix + " scale:" + scale);
        for (int i = mMapSymbols.size() - 1; i >= 0; i--) {
            BaseMapSymbol symbol = mMapSymbols.get(i);
            if ((!symbol.isVisible()) || symbol.getThreshold() > mScale)
                continue;
            Position location = symbol.getLocation();
            if (location != null) {
                float[] xy = {x, y};
                xy = transformToMapCoordinate(xy);
                if (symbol.isPointInClickRect(xy[0], xy[1])
                        && symbol.mOnMapSymbolListener != null) {
                    if (symbol.mOnMapSymbolListener
                            .onMapSymbolClick(mMapSymbols.get(i)))
                        break;
                }
            }
        }
    }

    private void calScreenRect() {
        Matrix invertMatrix = new Matrix();
        mMapMatrix.invert(invertMatrix);
        float[] center = {getWidth() / 2, getHeight() / 2};
        invertMatrix.mapPoints(center);
        double diagnal = Math.sqrt(getWidth() * getWidth() + getHeight()
                * getHeight())
                / mScale;
        int left = (int) Math.max(0, Math.round(center[0] - diagnal / 2));
        int top = (int) Math.max(0, Math.round(center[1] - diagnal / 2));
        int right = (int) Math.min(mMapDecoder.getWidth(),
                Math.round(center[0] + diagnal / 2));
        int bottom = (int) Math.min(mMapDecoder.getHeight(),
                Math.round(center[1] + diagnal / 2));
        Rect rect = new Rect(left, top, right, bottom);
        updateScreenRect(rect);
    }

    private void updateScreenRect(Rect rect) {
        mScreenRect = rect;
    }

    public void setMap(Bitmap bitmap) {
        Drawable drawable = new BitmapDrawable(getResources(), bitmap);
        setBackground(drawable);
    }

    private void updateScale() {
        float[] matrixValue = new float[9];
        mMapMatrix.getValues(matrixValue);
        float scalex = matrixValue[Matrix.MSCALE_X];
        float skewy = matrixValue[Matrix.MSKEW_Y];
        mScale = (float) Math.sqrt(scalex * scalex + skewy * skewy);
        Log.v("update scale", mScale + "");
    }

    private void refreshDetailBitmap() {
        mDecodeBitmapHandler.post(new Runnable() {

            @Override
            public void run() {
                calScreenRect();
                Rect rect = mScreenRect;
                if (!rect.isEmpty()) {
                    Options options = new Options();
                    options.inSampleSize = dealSampeSize(
                            rect.right - rect.left, rect.bottom - rect.top);
                    mDetailBitmap = mMapDecoder.decodeRegion(rect, options);
                    mDetailMapMatrix = new Matrix();
                    mDetailMapMatrix.setScale(
                            rect.width() / mDetailBitmap.getWidth(),
                            rect.height() / mDetailBitmap.getHeight());
                    mDetailMapMatrix.postTranslate(rect.left, rect.top);
                    if (getHandler() != null) {
                        getHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                postInvalidate();
                            }
                        });
                    }
                }
            }
        });
    }

    private int dealSampeSize(int width, int height) {
        int inSampleSize = (int) Math.ceil(1f / mScale);
        long freeMem = Runtime.getRuntime().maxMemory()
                - (Runtime.getRuntime().totalMemory() - Runtime.getRuntime()
                .freeMemory()) - 15 * 1024 * 1024;
        if (freeMem - width * height * 4 / inSampleSize / inSampleSize < 0) {
            if (freeMem < 15 * 1024 * 1024)
                freeMem = (freeMem + 15 * 1024 * 1024) / 3;
            float size = ((float) (width * height * 4) / freeMem);
            inSampleSize = (int) Math.ceil(size);
        }
        return inSampleSize;
    }

    public float[] transformToViewCoordinate(float[] mapCoordinate) {
        float[] viewCoordinate = {mapCoordinate[0], mapCoordinate[1]};
        mMapMatrix.mapPoints(viewCoordinate);
        return viewCoordinate;
    }

    public float[] transformToMapCoordinate(float[] viewCoordinate) {
        float[] mapCoordinate = {viewCoordinate[0], viewCoordinate[1]};
        Matrix invertMatrix = new Matrix();
        mMapMatrix.invert(invertMatrix);
        invertMatrix.mapPoints(mapCoordinate);
        return mapCoordinate;
    }

    public void centerMyLocation() {
        centerSpecificLocation(mMyLocationSymbol.getLocation());
    }

    public void centerSpecificLocation(Position location) {
        translateToSpecificPoint((float) location.getX(), (float) location.getY());
    }

    private void translateToSpecificPoint(float x, float y) {
        if (mTranslateAnimRunnable == null) {
            mTranslateAnimRunnable = new TranslateAnimRunnable(x, y);
        } else {
            mTranslateAnimRunnable.setMoveToPoint(x, y);
        }
        if (!mTranslateAnimRunnable.isRunning()) {
            new Thread(mTranslateAnimRunnable).start();
        }
    }

    public long getFloorId() {
        return mFloorId;
    }

    public float getScale() {
        return mScale;
    }

    public void initNewMap(InputStream inputStream, double scale,
                           double rotation, Position currentPosition) {
        try {
            mMyLocationSymbol.setLocation(null);
            mMapDecoder = BitmapRegionDecoder.newInstance(inputStream, false);
            Rect rect = new Rect(0, 0, mMapDecoder.getWidth(),
                    mMapDecoder.getHeight());
            Options options = new Options();
            options.inJustDecodeBounds = true;
            options.inSampleSize = Math.round(Math.max(mMapDecoder.getWidth(),
                    mMapDecoder.getHeight()) / 1024f);
            Bitmap bitmap = mMapDecoder.decodeRegion(rect, options);
            mShadowBitmap = bitmap;
            mShadowMapMatrix = new Matrix();
            mShadowMapMatrix.setScale(rect.width() / mShadowBitmap.getWidth(),
                    rect.height() / mShadowBitmap.getHeight(), 0, 0);
            Matrix initMatrix = new Matrix();
            initMatrix.postScale((float) scale / mInitScale, (float) scale
                    / mInitScale);
            initMatrix.postRotate((float) rotation - mInitRotation);
            mMapMatrix = initMatrix;
            refreshDetailBitmap();
            invalidate();
            mInitScale = (float) scale;
            mInitRotation = (float) rotation;
            mInitMapView = true;

            Bitmap symbolBitmap = BitmapFactory.decodeResource(mContext.getResources(), R.mipmap.marker);

            mRealLocationSymbol = new RealLocationSymbol(symbolBitmap,
                    symbolBitmap.getWidth() / 2, symbolBitmap.getHeight() / 2);
            if (currentPosition == null) {
                Position centerInMap = new Position();
                centerInMap.setX(rect.width() / 2);
                centerInMap.setY(rect.height() / 2);
                mRealLocationSymbol.setLocation(centerInMap);
            } else {
                mRealLocationSymbol.setLocation(currentPosition);
            }
            if (mOnRealLocationMoveListener != null) {
                mOnRealLocationMoveListener.onMove(mRealLocationSymbol.getLocation());
            }

            initRouteMatrix();


        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void setMapSymbols(List<BaseMapSymbol> mapSymbols) {
        mMapSymbols = mapSymbols;
    }

    /**
     * update my location with animation
     *
     * @param location location
     */
    public void updateMyLocation(Position location) {
        if (mMyLocationSymbol.getLocation() == null) {
            mMyLocationSymbol.setLocation(location);
            centerMyLocation();
        } else {
            if (mUpdateMyLocationAnimRunnable == null) {
                mUpdateMyLocationAnimRunnable = new UpdateMyLocationAnimRunnable((float)
                        location.getX(), (float) location.getY());
            } else {
                mUpdateMyLocationAnimRunnable.setTarget((float) location.getX(),
                        (float) location.getY());
            }
            if (!mUpdateMyLocationAnimRunnable.isRunning()) {
                new Thread(mUpdateMyLocationAnimRunnable).start();
            }
        }
    }

    public List<BaseMapSymbol> getMapSymbols() {
        return mMapSymbols;
    }

    public LocationSymbol getmMyLocationSymbol() {
        return mMyLocationSymbol;
    }

    public void setTrackPosition() {
        mIsTrackPosition = true;
    }

    public Position getRealLocation() {
        return mRealLocationSymbol.getLocation();
    }

    public void setOnRealLocationMoveListener(OnRealLocationMoveListener
                                                      mOnRealLocationMoveListener) {
        this.mOnRealLocationMoveListener = mOnRealLocationMoveListener;
        if (mOnRealLocationMoveListener != null && mRealLocationSymbol != null) {
            mOnRealLocationMoveListener.onMove(mRealLocationSymbol.getLocation());
        }
    }

    public void toggleRealLocationSymbol() {
        if (mRealLocationSymbol.isVisible()) {
            mRealLocationSymbol.setVisible(false);
            mMyLocationSymbol.setVisible(true);
        } else {
            mRealLocationSymbol.setVisible(true);
            mMyLocationSymbol.setVisible(false);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Size getScreenSize(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return new Size(dm.widthPixels, dm.heightPixels);
    }

    private float min(float[] data) {
        if (data == null || data.length == 0) {
            return Float.MAX_VALUE;
        }

        float minValue = data[0];
        for (float d : data) {
            if (d < minValue) {
                minValue = d;
            }
        }

        return minValue;
    }

    private float max(float[] data) {
        if (data == null || data.length == 0) {
            return Float.MIN_VALUE;
        }

        float maxValue = data[0];
        for (float d : data) {
            if (d > maxValue) {
                maxValue = d;
            }
        }

        return maxValue;
    }
}
