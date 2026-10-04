package android.content;

public interface DialogInterface {
    void dismiss();

    interface OnClickListener {
        void onClick(DialogInterface dialog, int which);
    }

    interface OnDismissListener {
        void onDismiss(DialogInterface dialog);
    }

    int BUTTON_POSITIVE = -1;
    int BUTTON_NEGATIVE = -2;
    int BUTTON_NEUTRAL = -3;
}
