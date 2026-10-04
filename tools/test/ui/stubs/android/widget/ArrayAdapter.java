package android.widget;

import android.content.Context;

import java.util.Arrays;
import java.util.List;

public class ArrayAdapter<T> implements ListAdapter {

    private final List<T> mItems;

    public ArrayAdapter(Context c, int resource, T[] items) {
        mItems = Arrays.asList(items);
    }

    public ArrayAdapter(Context c, int resource, List<T> items) {
        mItems = items;
    }

    @Override
    public int getCount() { return mItems.size(); }

    @Override
    public Object getItem(int position) { return mItems.get(position); }
}
