package com.mostakim.audiorec.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Harness foreground service: never started. */
public class RecordingService extends Service {

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
