package android.view;

import android.content.Context;

public class ViewConfiguration {
    private static final ViewConfiguration INSTANCE = new ViewConfiguration();

    public static ViewConfiguration get(Context c) { return INSTANCE; }
    public int getScaledTouchSlop() { return 24; }
    public int getScaledMinimumFlingVelocity() { return 50; }
    public int getScaledMaximumFlingVelocity() { return 8000; }
    public long getLongPressTimeout() { return 500; }
}
