package android.view;

public class VelocityTracker {
    public static VelocityTracker obtain() { return new VelocityTracker(); }
    public void addMovement(MotionEvent e) { }
    public float getXVelocity() { return 0f; }
    public float getYVelocity() { return 0f; }
    public void computeCurrentVelocity(int units) { }
    public void recycle() { }
}
