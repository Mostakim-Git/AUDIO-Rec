package android.graphics;

public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;
    public static final int A = ANTI_ALIAS_FLAG;
    public static final int C = 2;
    public static final int J = 4;
    public static final int S = 8;

    public static class Style {
        public static final Style FILL = new Style();
        public static final Style STROKE = new Style();
        public static final Style FILL_AND_STROKE = new Style();
    }

    public static class Cap {
        public static final Cap BUTT = new Cap();
        public static final Cap ROUND = new Cap();
        public static final Cap SQUARE = new Cap();
    }

    public static class Join {
        public static final Join MITER = new Join();
        public static final Join ROUND = new Join();
        public static final Join BEVEL = new Join();
    }

    public static class Align {
        public static final Align LEFT = new Align();
        public static final Align CENTER = new Align();
        public static final Align RIGHT = new Align();
    }

    private int mColor = 0xFF000000;
    private float mTextSize = 14f;

    public Paint() { }

    public Paint(int flags) { }

    public void setColor(int color) { mColor = color; }
    public int getColor() { return mColor; }
    public void setAlpha(int a) { mColor = (mColor & 0x00FFFFFF) | ((a & 0xFF) << 24); }
    public int getAlpha() { return (mColor >>> 24) & 0xFF; }
    public void setAntiAlias(boolean b) { }
    public void setStrokeWidth(float w) { }
    public float getStrokeWidth() { return 1f; }
    public void setStyle(Style s) { }
    public void setStrokeCap(Cap c) { }
    public void setStrokeJoin(Join j) { }
    public void setTextSize(float s) { mTextSize = s; }
    public float getTextSize() { return mTextSize; }
    public void setTypeface(Typeface t) { }
    public void setTextAlign(Align a) { }
    public void setTextSkewX(float f) { }
    public void setFlags(int f) { }
    public float measureText(String text) {
        return text == null ? 0f : text.length() * mTextSize * 0.52f;
    }
    public float measureText(CharSequence text) {
        return measureText(text == null ? null : text.toString());
    }
    public void setShadowLayer(float r, float x, float y, int color) { }
    public void setShader(Object s) { }
}
