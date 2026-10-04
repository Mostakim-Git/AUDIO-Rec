package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.Window;

public class Dialog implements DialogInterface {

    private final Context mContext;
    private final Window mWindow = new Window();
    private boolean mShowing;
    private View mContentView;
    private CharSequence mTitle;
    private OnDismissListener mDismissListener;

    public Dialog(Context c) { mContext = c; }

    public void setTitle(CharSequence title) { mTitle = title; }

    public void setTitle(int resId) { mTitle = "res-" + resId; }

    public CharSequence getTitle() { return mTitle; }

    public void setContentView(View v) { mContentView = v; }

    public Window getWindow() { return mWindow; }

    public Context getContext() { return mContext; }

    public void show() { mShowing = true; }

    @Override
    public void dismiss() {
        mShowing = false;
        if (mDismissListener != null) mDismissListener.onDismiss(this);
    }

    public boolean isShowing() { return mShowing; }

    public void setOnDismissListener(OnDismissListener l) { mDismissListener = l; }

    public void setOnCancelListener(OnCancelListener l) { }

    public void setCancelable(boolean b) { }

    public void setCanceledOnTouchOutside(boolean b) { }

    public View contentView() { return mContentView; }

    public interface OnCancelListener {
        void onCancel(DialogInterface dialog);
    }

    public interface OnShowListener {
        void onShow(DialogInterface dialog);
    }
}
