package android.widget;

import android.content.Context;
import android.view.View;

public class AdapterView<T> extends View {

    public interface OnItemSelectedListener {
        void onItemSelected(AdapterView<?> parent, View view, int position, long id);
        void onNothingSelected(AdapterView<?> parent);
    }

    private OnItemSelectedListener mListener;
    private int mSelected = -1;

    public AdapterView(Context c) { super(c); }

    public void setOnItemSelectedListener(OnItemSelectedListener l) { mListener = l; }
    public OnItemSelectedListener getOnItemSelectedListener() { return mListener; }
    public void setSelection(int position) { mSelected = position; }
    public int getSelectedItemPosition() { return mSelected; }
}
