package android.widget;

import android.content.Context;
import android.util.AttributeSet;

public class Spinner extends AdapterView<Adapter> {

    private Adapter mAdapter;

    public Spinner(Context c) { super(c); }
    public Spinner(Context c, AttributeSet a) { super(c); }

    public void setAdapter(Adapter adapter) { mAdapter = adapter; }
    public Adapter getAdapter() { return mAdapter; }

    @Override
    public void setSelection(int position) {
        super.setSelection(position);
        AdapterView.OnItemSelectedListener l = getOnItemSelectedListener();
        if (l != null) l.onItemSelected(this, this, position, position);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = (int) (46 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(Math.max(h, getSuggestedMinimumHeight()), heightMeasureSpec));
    }
}
