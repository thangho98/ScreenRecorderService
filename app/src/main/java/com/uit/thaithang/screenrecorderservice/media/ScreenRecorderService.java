package com.uit.thaithang.screenrecorderservice.media;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.io.File;
import java.io.IOException;



/**
 * Created by uzias on 10/3/16.
 */

public class ScreenRecorderService extends Service {

    private final String TAG = ScreenRecorderService.class.getSimpleName();

    private static final String BASE = "com.uit.thaithang.screenrecorderservice.";
    public static final String ACTION_START = BASE + "ACTION_START";
    public static final String ACTION_STOP = BASE + "ACTION_STOP";
    public static final String ACTION_PAUSE = BASE + "ACTION_PAUSE";
    public static final String ACTION_RESUME = BASE + "ACTION_RESUME";
    public static final String ACTION_QUERY_STATUS = BASE + "ACTION_QUERY_STATUS";
    public static final String ACTION_QUERY_STATUS_RESULT = BASE + "ACTION_QUERY_STATUS_RESULT";
    public static final String EXTRA_RESULT_CODE = BASE + "EXTRA_RESULT_CODE";
    public static final String EXTRA_QUERY_RESULT_RECORDING = BASE + "EXTRA_QUERY_RESULT_RECORDING";
    public static final String EXTRA_QUERY_RESULT_PAUSING = BASE + "EXTRA_QUERY_RESULT_PAUSING";

    private static final Object sSync = new Object();

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private WindowManager windowManager;
    private MediaRecorderHelper mediaRecorderHelper;

    private String directory = String.valueOf(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "ScreenRecoder"));

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mediaProjectionManager = (MediaProjectionManager) getApplicationContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy:");
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand:intent=" + intent);

        int result = START_STICKY;
        final String action = intent != null ? intent.getAction() : null;
        if (ACTION_START.equals(action)) {
            startScreenRecord(intent);
            updateStatus();
        } else if (ACTION_STOP.equals(action) || TextUtils.isEmpty(action)) {
            stopScreenRecord();
            updateStatus();
            result = START_NOT_STICKY;
        } else if (ACTION_QUERY_STATUS.equals(action)) {
            if (!updateStatus()) {
                stopSelf();
                result = START_NOT_STICKY;
            }
        } else if (ACTION_PAUSE.equals(action)) {
            pauseScreenRecord();
        } else if (ACTION_RESUME.equals(action)) {
            resumeScreenRecord();
        }
        return result;
    }

    private void startScreenRecord(Intent intent) {
        Log.v(TAG, "startScreenRecord: mediaRecorderHelper=" + mediaRecorderHelper);
        synchronized (sSync) {
            if (mediaRecorderHelper == null) {
                int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
                // get MediaProjection
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, intent);
                if (mediaProjection != null) {
                    windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                    mediaRecorderHelper = new MediaRecorderHelper(mediaProjection, windowManager,360,30,true, directory);
                    mediaRecorderHelper.start(true);
                }
            }
        }
    }

    private void stopScreenRecord() {
        Log.v(TAG, "stopScreenRecord:mediaRecorderHelper=" + mediaRecorderHelper);
        synchronized (sSync) {
            if (mediaRecorderHelper != null) {
                mediaRecorderHelper.stop(true);
                mediaRecorderHelper = null;
                mediaProjection = null;
                windowManager = null;
                // you should not wait here
            }
        }
        stopSelf();
    }

    private void resumeScreenRecord(){
        synchronized (sSync) {
            if (mediaRecorderHelper != null) {
                mediaRecorderHelper.pause();
            }
        }
    }

    private void pauseScreenRecord(){
        synchronized (sSync) {
            if (mediaRecorderHelper != null) {
                if (mediaProjection != null) {
                    mediaRecorderHelper.resume(windowManager,mediaProjection);
                }
            }
        }
    }

    private boolean updateStatus() {
        final boolean isRecording, isPausing;
        synchronized (sSync) {
            isRecording = (mediaRecorderHelper != null);
            isPausing = isRecording && mediaRecorderHelper.isPaused();
        }
        final Intent result = new Intent();
        result.setAction(ACTION_QUERY_STATUS_RESULT);
        result.putExtra(EXTRA_QUERY_RESULT_RECORDING, isRecording);
        result.putExtra(EXTRA_QUERY_RESULT_PAUSING, isPausing);
        Log.v(TAG, "sendBroadcast:isRecording=" + isRecording + ",isPausing=" + isPausing);
        sendBroadcast(result);
        return isRecording;
    }
}