package com.uit.thaithang.screenrecorderservice;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.uit.thaithang.screenrecorderservice.media.ScreenRecorderService;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private String TAG = MainActivity.class.getName();

    private static final int REQUEST_CODE = 1000;
    private static final int REQUEST_PERMISSION = 1001;


    //view
    private ToggleButton tgBtnStartStop;
    private ToggleButton tgBtnPauseResume;
    private MyBroadcastReceiver mReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tgBtnStartStop = (ToggleButton) findViewById(R.id.tgBtnStartStop);
        tgBtnPauseResume = (ToggleButton) findViewById(R.id.tgBtnPauseResume);

        updateRecording(false, false);
        if (mReceiver == null) {
            mReceiver = new MyBroadcastReceiver(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.v(TAG, "onResume:");
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ScreenRecorderService.ACTION_QUERY_STATUS_RESULT);
        registerReceiver(mReceiver, intentFilter);
        queryRecordingStatus();
    }
    private void queryRecordingStatus() {
        Log.v(TAG, "queryRecording:");
        final Intent intent = new Intent(this, ScreenRecorderService.class);
        intent.setAction(ScreenRecorderService.ACTION_QUERY_STATUS);
        startService(intent);
    }

    @Override
    protected void onPause() {
        Log.v(TAG, "onPause:");
        unregisterReceiver(mReceiver);
        super.onPause();
    }

    private CompoundButton.OnCheckedChangeListener mOnCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            switch (buttonView.getId()){
                case R.id.tgBtnStartStop:
                    if (isExternalStorageReadable()) {
                        checkAndRequestPermissions();
                        if (isChecked) {
                            start();

                        } else {
                            stop();
                        }
                    } else {
                        tgBtnStartStop.setOnCheckedChangeListener(null);
                        try {
                            tgBtnStartStop.setChecked(false);
                        } finally {
                            tgBtnStartStop.setOnCheckedChangeListener(mOnCheckedChangeListener);
                        }
                    }
                    break;
                case R.id.tgBtnPauseResume:
                    if(isChecked)
                        pause();
                    else
                        resume();
                    break;
            }
        }
    };


    private void start() {
        MediaProjectionManager manager = (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent permissionIntent = manager.createScreenCaptureIntent();
        startActivityForResult(permissionIntent, REQUEST_CODE);
    }

    private void stop() {
        final Intent intent = new Intent(MainActivity.this, ScreenRecorderService.class);
        intent.setAction(ScreenRecorderService.ACTION_STOP);
        startService(intent);
    }

    private void resume() {
        final Intent intent = new Intent(MainActivity.this, ScreenRecorderService.class);
        intent.setAction(ScreenRecorderService.ACTION_RESUME);
        startService(intent);
    }

    private void pause() {
        final Intent intent = new Intent(MainActivity.this, ScreenRecorderService.class);
        intent.setAction(ScreenRecorderService.ACTION_PAUSE);
        startService(intent);
    }

    private void updateRecording(final boolean isRecording, final boolean isPausing) {
        Log.v(TAG, "updateRecording:isRecording=" + isRecording + ",isPausing=" + isPausing);
        tgBtnStartStop.setOnCheckedChangeListener(null);
        tgBtnPauseResume.setOnCheckedChangeListener(null);
        try {
            tgBtnStartStop.setChecked(isRecording);
            tgBtnPauseResume.setEnabled(isRecording);
            tgBtnPauseResume.setChecked(isPausing);
        } finally {
            tgBtnStartStop.setOnCheckedChangeListener(mOnCheckedChangeListener);
            tgBtnPauseResume.setOnCheckedChangeListener(mOnCheckedChangeListener);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        Log.v(TAG, "onActivityResult:resultCode=" + resultCode + ",data=" + data.getExtras().get("data"));
        super.onActivityResult(requestCode, resultCode, data);
        if (REQUEST_CODE == requestCode) {
            if (resultCode != Activity.RESULT_OK) {
                // when no permission
                Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
                return;
            }
            startScreenRecorder(resultCode, data);
        }
    }

    private void startScreenRecorder(final int resultCode, final Intent data) {
        final Intent intent = new Intent(this, ScreenRecorderService.class);
        intent.setAction(ScreenRecorderService.ACTION_START);
        intent.putExtra(ScreenRecorderService.EXTRA_RESULT_CODE, resultCode);
        intent.putExtras(data);
        startService(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode)
        {
            case REQUEST_PERMISSION:
                if ((grantResults.length > 0) && (grantResults[0] + grantResults[1] + grantResults[2] == PackageManager.PERMISSION_GRANTED)) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this,Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED)
                        Toast.makeText(MainActivity.this, "Permission Granted", Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(MainActivity.this, "Permission Denied", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private static final class MyBroadcastReceiver extends BroadcastReceiver {
        private final WeakReference<MainActivity> mWeakParent;
        public MyBroadcastReceiver(final MainActivity parent) {
            mWeakParent = new WeakReference<MainActivity>(parent);
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            Log.v("MyBroadcastReceiver", "onReceive:" + intent);
            final String action = intent.getAction();
            if (ScreenRecorderService.ACTION_QUERY_STATUS_RESULT.equals(action)) {
                final boolean isRecording = intent.getBooleanExtra(ScreenRecorderService.EXTRA_QUERY_RESULT_RECORDING, false);
                final boolean isPausing = intent.getBooleanExtra(ScreenRecorderService.EXTRA_QUERY_RESULT_PAUSING, false);
                final MainActivity parent = mWeakParent.get();
                if (parent != null) {
                    parent.updateRecording(isRecording, isPausing);
                }
            }
        }
    }

//================================================================================
// methods related to new permission model on Android 6 and later
//================================================================================

    private void checkAndRequestPermissions() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            String[] permissions = new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO
            };
            List<String> listPermissionsNeeded = new ArrayList<>();
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, permission) != PackageManager.PERMISSION_GRANTED) {
                    listPermissionsNeeded.add(permission);
                }
            }
            if (!listPermissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(MainActivity.this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), REQUEST_CODE);
            }
        }
    }

    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }
}
