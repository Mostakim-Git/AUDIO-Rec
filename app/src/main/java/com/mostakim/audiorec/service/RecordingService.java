package com.mostakim.audiorec.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.R;
import com.mostakim.audiorec.audio.AudioEngine;
import com.mostakim.audiorec.audio.Recorder;
import com.mostakim.audiorec.ui.MainActivity;
import com.mostakim.audiorec.util.Fmt;

/**
 * Keeps a take alive while the operator is in another app.
 *
 * A phone that is being used as a recorder must not drop a session because the
 * screen locked, so the capture runs under a microphone foreground service with
 * a live notification: running time, written size, level, and a stop action.
 */
public class RecordingService extends Service implements AudioEngine.Listener {

    public static final String ACTION_STOP = "com.mostakim.audiorec.STOP_RECORDING";
    public static final String ACTION_TOGGLE_MONITOR = "com.mostakim.audiorec.TOGGLE_MONITOR";
    private static final String CHANNEL = "audiorec-capture";
    private static final int NOTIF_ID = 4101;

    private AudioEngine mEngine;
    private NotificationManager mNm;
    private long mLastBytes;
    private long mLastElapsed;
    private float mLastPeak;

    @Override
    public void onCreate() {
        super.onCreate();
        mNm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mEngine = App.get().audio();
        mEngine.addListener(this);
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            mEngine.stopRecording();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startForeground(NOTIF_ID, buildNotification("Recording", 0, 0, 0f));
        } catch (Exception e) {
            // microphone permission revoked while the service was starting: the
            // capture cannot be kept alive, so say so instead of dying
            android.widget.Toast.makeText(this,
                    "AUDIO-rec could not start the capture service: " + e.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!mEngine.isCapturing()) {
            mEngine.startMonitor();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mEngine != null) mEngine.removeListener(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Recording",
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Active USB audio capture");
        ch.setShowBadge(false);
        ch.enableVibration(false);
        ch.setSound(null, null);
        mNm.createNotificationChannel(ch);
    }

    private Notification buildNotification(String state, long bytes, long elapsedMs, float peakDb) {
        Intent open = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, RecordingService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = state + " \u00b7 " + Fmt.timecode(elapsedMs)
                + " \u00b7 " + Fmt.size(bytes)
                + (peakDb > -99 ? "  \u00b7  " + Fmt.dbShort(peakDb) + " dBFS" : "");

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setContentTitle("AUDIO-rec")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_rec)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stop),
                        "Stop", stopPi).build());
        return b.build();
    }

    // ------------------------------------------------------------- listener
    @Override
    public void onRecordingTick(long frames, long bytes, long elapsedMs) {
        mLastBytes = bytes;
        mLastElapsed = elapsedMs;
        mNm.notify(NOTIF_ID, buildNotification("Recording", bytes, elapsedMs, mLastPeak));
    }

    @Override
    public void onLevels(float[] rmsDb, float[] peakDb, int channels) {
        float peak = -120f;
        for (int i = 0; i < channels && i < peakDb.length; i++) {
            if (peakDb[i] > peak) peak = peakDb[i];
        }
        mLastPeak = peak;
    }

    @Override
    public void onRecordingFinished(Recorder.Result result) {
        if (result != null) {
            Notification.Builder nb = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(this, CHANNEL)
                    : new Notification.Builder(this);
            mNm.notify(NOTIF_ID + 1, nb
                    .setContentTitle("Take saved")
                    .setContentText(result.file == null ? "" : result.file.getName()
                            + "  \u00b7  " + result.summary())
                    .setSmallIcon(R.drawable.ic_check)
                    .setAutoCancel(true)
                    .build());
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onEngineState(AudioEngine.State s) {
        if (s == AudioEngine.State.IDLE) {
            stopForeground(true);
            stopSelf();
        } else {
            mNm.notify(NOTIF_ID, buildNotification(label(s), mLastBytes, mLastElapsed, mLastPeak));
        }
    }

    private String label(AudioEngine.State s) {
        switch (s) {
            case RECORDING: return "Recording";
            case PAUSED: return "Paused";
            case MONITORING: return "Monitoring";
            default: return "Idle";
        }
    }
}
