package android.view;

public class MotionEvent {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_POINTER_DOWN = 5;
    public static final int ACTION_POINTER_UP = 6;

    private int mAction = ACTION_DOWN;
    private float mX, mY;
    private long mTime;

    public static MotionEvent obtain(long down, long event, int action, float x, float y, int meta) {
        MotionEvent e = new MotionEvent();
        e.mAction = action;
        e.mX = x;
        e.mY = y;
        e.mTime = event;
        return e;
    }

    public static MotionEvent obtain(MotionEvent other) {
        MotionEvent e = new MotionEvent();
        e.mAction = other.mAction;
        e.mX = other.mX;
        e.mY = other.mY;
        e.mTime = other.mTime;
        return e;
    }

    public int getActionMasked() { return mAction; }
    public int getAction() { return mAction; }
    public float getX() { return mX; }
    public float getY() { return mY; }
    public float getRawX() { return mX; }
    public float getRawY() { return mY; }
    public long getEventTime() { return mTime; }
    public long getDownTime() { return mTime; }
    public int getPointerCount() { return 1; }
    public float getX(int i) { return mX; }
    public float getY(int i) { return mY; }
    public void setAction(int action) { mAction = action; }
    public void offsetLocation(float dx, float dy) { mX += dx; mY += dy; }
    public void recycle() { }
    public void setSource(int source) { }
}
