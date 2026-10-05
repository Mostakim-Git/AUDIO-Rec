package android.widget;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.util.AttributeSet;

public class EditText extends TextView {

    private int mInputType = InputType.TYPE_CLASS_TEXT;
    private int mSelectionStart, mSelectionEnd;

    public EditText(Context c) { super(c); }
    public EditText(Context c, AttributeSet a) { super(c, a); }

    public void setInputType(int type) { mInputType = type; }
    public int getInputType() { return mInputType; }
    public void setSelection(int index) { mSelectionStart = mSelectionEnd = index; }
    public void setSelection(int start, int stop) { mSelectionStart = start; mSelectionEnd = stop; }
    public void selectAll() { }
    public Editable getEditableText() { return null; }
    public void setFreezesText(boolean b) { }
    public void addTextChangedListener(android.text.TextWatcher w) { }
    public int getSelectionStart() { return mSelectionStart; }
    public int getSelectionEnd() { return mSelectionEnd; }
}
