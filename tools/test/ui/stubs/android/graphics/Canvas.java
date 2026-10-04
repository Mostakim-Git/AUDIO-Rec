package android.graphics;

public class Canvas {
    private int mWidth = 1080, mHeight = 2340;

    public Canvas() { }

    public int getWidth() { return mWidth; }
    public int getHeight() { return mHeight; }
    public void drawColor(int c) { }
    public void drawRect(float l, float t, float r, float b, Paint p) { }
    public void drawRect(RectF rect, Paint p) { }
    public void drawRoundRect(RectF rect, float rx, float ry, Paint p) { }
    public void drawLine(float x1, float y1, float x2, float y2, Paint p) { }
    public void drawCircle(float cx, float cy, float r, Paint p) { }
    public void drawArc(RectF oval, float start, float sweep, boolean useCenter, Paint p) { }
    public void drawText(String text, float x, float y, Paint p) { }
    public void drawText(CharSequence text, int start, int end, float x, float y, Paint p) { }
    public void drawPath(Path path, Paint p) { }
    public void drawBitmap(Object bitmap, float left, float top, Paint p) { }
    public void drawBitmap(Object bitmap, RectF src, RectF dst, Paint p) { }
    public int save() { return 0; }
    public void restore() { }
    public void restoreToCount(int c) { }
    public void translate(float dx, float dy) { }
    public void scale(float sx, float sy) { }
    public void rotate(float deg) { }
    public void clipRect(float l, float t, float r, float b) { }
    public void drawPoint(float x, float y, Paint p) { }
}
