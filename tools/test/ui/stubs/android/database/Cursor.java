package android.database;

/** A tiny in-memory cursor: enough for the model mappers. */
public class Cursor {
    private final String[] mColumns;
    private final java.util.List<Object[]> mRows;
    private int mPos = -1;

    public Cursor(String[] columns, java.util.List<Object[]> rows) {
        mColumns = columns;
        mRows = rows;
    }

    public int getCount() { return mRows.size(); }
    public int getPosition() { return mPos; }
    public boolean moveToFirst() { return moveToPosition(0); }
    public boolean moveToNext() {
        if (mPos + 1 >= mRows.size()) return false;
        mPos++;
        return true;
    }
    public boolean moveToPosition(int p) {
        if (p < 0 || p >= mRows.size()) return false;
        mPos = p;
        return true;
    }
    public boolean isAfterLast() { return mPos >= mRows.size(); }
    public void close() { }
    public String getString(int i) { return (String) mRows.get(mPos)[i]; }
    public int getInt(int i) { return ((Number) mRows.get(mPos)[i]).intValue(); }
    public long getLong(int i) { return ((Number) mRows.get(mPos)[i]).longValue(); }
    public float getFloat(int i) { return ((Number) mRows.get(mPos)[i]).floatValue(); }
    public boolean isNull(int i) { return mRows.get(mPos)[i] == null; }

    public int getColumnIndex(String name) {
        for (int i = 0; i < mColumns.length; i++) {
            if (mColumns[i].equals(name)) return i;
        }
        return -1;
    }

    public int getColumnIndexOrThrow(String name) {
        int i = getColumnIndex(name);
        if (i < 0) throw new IllegalArgumentException("no column " + name);
        return i;
    }
}
