package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;

public class AlertDialog extends Dialog implements DialogInterface {

    public static final int BUTTON_POSITIVE = DialogInterface.BUTTON_POSITIVE;
    public static final int BUTTON_NEGATIVE = DialogInterface.BUTTON_NEGATIVE;
    public static final int BUTTON_NEUTRAL = DialogInterface.BUTTON_NEUTRAL;

    private final Button[] mButtons = new Button[]{
            new Button(null), new Button(null), new Button(null)};

    AlertDialog(Context c) {
        super(c);
    }

    public Button getButton(int whichButton) {
        return mButtons[whichButton + 3];
    }

    public void setMessage(CharSequence message) { }

    public void setView(View v) { setContentView(v); }

    public static class Builder {

        private final Context mContext;
        private CharSequence mTitle;
        private CharSequence mMessage;
        private View mView;
        private CharSequence mPositive, mNegative, mNeutral;
        private DialogInterface.OnClickListener mPositiveListener, mNegativeListener,
                mNeutralListener, mItemsListener;
        private CharSequence[] mItems;
        private int mCheckedItem = -1;

        public Builder(Context c) { mContext = c; }

        public Builder setTitle(CharSequence title) { mTitle = title; return this; }

        public Builder setTitle(int resId) { mTitle = "res-" + resId; return this; }

        public Builder setMessage(CharSequence message) { mMessage = message; return this; }

        public Builder setMessage(int resId) { mMessage = "res-" + resId; return this; }

        public Builder setView(View v) { mView = v; return this; }

        public Builder setCustomTitle(View v) { mView = v; return this; }

        public Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener l) {
            mPositive = text;
            mPositiveListener = l;
            return this;
        }

        public Builder setPositiveButton(int resId, DialogInterface.OnClickListener l) {
            return setPositiveButton("res-" + resId, l);
        }

        public Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener l) {
            mNegative = text;
            mNegativeListener = l;
            return this;
        }

        public Builder setNegativeButton(int resId, DialogInterface.OnClickListener l) {
            return setNegativeButton("res-" + resId, l);
        }

        public Builder setNeutralButton(CharSequence text, DialogInterface.OnClickListener l) {
            mNeutral = text;
            mNeutralListener = l;
            return this;
        }

        public Builder setNeutralButton(int resId, DialogInterface.OnClickListener l) {
            return setNeutralButton("res-" + resId, l);
        }

        public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener l) {
            mItems = items;
            mItemsListener = l;
            return this;
        }

        public Builder setItems(int resId, DialogInterface.OnClickListener l) {
            return this;
        }

        public Builder setSingleChoiceItems(CharSequence[] items, int checkedItem,
                                            DialogInterface.OnClickListener l) {
            mItems = items;
            mCheckedItem = checkedItem;
            mItemsListener = l;
            return this;
        }

        public Builder setSingleChoiceItems(android.widget.ListAdapter adapter, int checkedItem,
                                            DialogInterface.OnClickListener l) {
            return this;
        }

        public Builder setMultiChoiceItems(CharSequence[] items, boolean[] checked,
                                           Object l) {
            mItems = items;
            return this;
        }

        public Builder setAdapter(android.widget.ListAdapter adapter,
                                  DialogInterface.OnClickListener l) {
            return this;
        }

        public Builder setCancelable(boolean cancelable) { return this; }

        public Builder setOnDismissListener(DialogInterface.OnDismissListener l) { return this; }

        public Builder setOnItemSelectedListener(Object l) { return this; }

        public AlertDialog create() {
            AlertDialog d = new AlertDialog(mContext);
            d.setTitle(mTitle);
            d.setMessage(mMessage);
            if (mView != null) d.setContentView(mView);
            // wire the buttons the way the framework does, so clicking a dialog
            // button in the harness runs the real listener
            d.getButton(BUTTON_POSITIVE).setText(mPositive == null ? "" : mPositive);
            if (mPositiveListener != null) {
                d.getButton(BUTTON_POSITIVE).setOnClickListener(
                        v -> mPositiveListener.onClick(d, BUTTON_POSITIVE));
            }
            d.getButton(BUTTON_NEGATIVE).setText(mNegative == null ? "" : mNegative);
            if (mNegativeListener != null) {
                d.getButton(BUTTON_NEGATIVE).setOnClickListener(
                        v -> mNegativeListener.onClick(d, BUTTON_NEGATIVE));
            }
            d.getButton(BUTTON_NEUTRAL).setText(mNeutral == null ? "" : mNeutral);
            if (mNeutralListener != null) {
                d.getButton(BUTTON_NEUTRAL).setOnClickListener(
                        v -> mNeutralListener.onClick(d, BUTTON_NEUTRAL));
            }
            return d;
        }

        public AlertDialog show() {
            AlertDialog d = create();
            d.show();
            return d;
        }
    }
}
