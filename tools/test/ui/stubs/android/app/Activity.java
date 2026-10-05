package android.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

/**
 * Harness stand-in for android.app.Activity.
 *
 * simulateCreate()/simulateResume() call the real (protected) lifecycle hooks so
 * the harness runs the app's actual onCreate path; post() and postDelayed() run
 * callbacks inline, which is what makes the shell's layout pass happen without a
 * message loop.
 */
public class Activity extends Context {

    private final Window mWindow = new Window();
    private View mContentView;
    private Intent mIntent = new Intent();
    private boolean mFinished;

    public static final int RESULT_OK = -1;
    public static final int RESULT_CANCELED = 0;
    public static final int RESULT_FIRST_USER = 1;

    protected void onCreate(Bundle savedInstanceState) { }

    protected void onResume() { }

    protected void onPause() { }

    protected void onDestroy() { }

    protected void onNewIntent(Intent intent) { }

    public void onBackPressed() { }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) { }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) { }

    protected void onStart() { }

    protected void onStop() { }

    protected void onRestart() { }

    protected void onSaveInstanceState(Bundle outState) { }

    protected void onRestoreInstanceState(Bundle savedInstanceState) { }

    protected void onUserLeaveHint() { }

    public void setContentView(View view) { mContentView = view; }

    public void setContentView(int resId) { }

    public View findViewById(int id) { return null; }

    public Window getWindow() { return mWindow; }

    public Intent getIntent() { return mIntent; }

    public void setIntent(Intent intent) { mIntent = intent; }

    public void runOnUiThread(Runnable action) { action.run(); }

    public void requestPermissions(String[] permissions, int requestCode) { }

    public void finish() { mFinished = true; }

    public boolean isFinishing() { return mFinished; }

    public boolean isDestroyed() { return false; }

    public void setResult(int resultCode) { }

    public void setResult(int resultCode, Intent data) { }

    public void setTitle(CharSequence title) { }

    public void setRequestedOrientation(int orientation) { }

    public void overridePendingTransition(int enterAnim, int exitAnim) { }

    public Window getWindowManager() { return mWindow; }

    public android.view.LayoutInflater getLayoutInflater() {
        throw new UnsupportedOperationException("the app never inflates a layout");
    }

    /** start the activity the way the framework would */
    public void simulateCreate(Bundle savedInstanceState) {
        onCreate(savedInstanceState);
    }

    public void simulateResume() {
        onResume();
    }

    public void simulatePause() {
        onPause();
    }

    public void simulateDestroy() {
        onDestroy();
    }

    public View contentView() { return mContentView; }
}
