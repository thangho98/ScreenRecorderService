package com.uit.thaithang.screenrecorderservice.media;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Environment;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.WindowManager;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;


public class MediaRecorderHelper {

    private String TAG = MediaRecorderHelper.class.getName();

    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private MediaProjection mediaProjection;
    private WindowManager windowManager;
    private VirtualDisplay virtualDisplay;
    private MediaProjectionCallback mediaProjectionCallback;
    private MediaRecorder mediaRecorder;
    private int mSrceenDensity;

    private int displayWidth;
    private int displayHeight;
    private int fps;
    private int resolution;
    private boolean enableAudio;

    private String videoUri;
    private String directory;
    private final String defaultName = "/ScreenRecord_";

    private boolean isRec;

    public boolean isPaused() {
        return isPaused;
    }

    private boolean isPaused;

    private ArrayList<String> sourceFiles = new ArrayList<>();

    private final int[][] validResolutions = {
            {640, 360},
            {854, 480},
            {1280, 720},
            {1920, 1080}
    };

    public MediaRecorderHelper(MediaProjection mediaProjection, WindowManager windowManager, int resolution, int fps, boolean enableAudio, String directory) {
        this.resolution = resolution;
        this.fps = fps;
        this.enableAudio = enableAudio;
        this.directory = directory;
        this.isRec = false;
        this.mediaProjection = mediaProjection;
        this.windowManager = windowManager;
        initRecorder();
    }

    private void initRecorder(){
        File dir = new File(directory);
        if(!dir.exists()){
            dir.mkdirs();
        }
        setDisplay();

        this.mediaRecorder = new MediaRecorder();

        if (enableAudio){
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        }

        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setVideoSize(displayWidth, displayHeight);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

        if (enableAudio){
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        }
        mediaRecorder.setVideoEncodingBitRate(10000000);
        mediaRecorder.setVideoFrameRate(30);

        int rotation = windowManager.getDefaultDisplay().getRotation();
        int orientation = DEFAULT_ORIENTATIONS.get(rotation + 90);
        mediaRecorder.setOrientationHint(orientation);

        videoUri = Environment.getExternalStoragePublicDirectory(directory)
                + new StringBuilder(defaultName).append(new SimpleDateFormat("dd-MM-yyyy-hh_mm_ss")
                .format(new Date())).append("-" + displayWidth + "x" + displayHeight + ".mp4").toString();
        mediaRecorder.setOutputFile(videoUri);

        try {
            mediaRecorder.prepare();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setDisplay()
    {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        mSrceenDensity = metrics.densityDpi;

        //Get Srceen
        displayHeight = metrics.heightPixels;
        displayWidth = metrics.widthPixels;

        Log.d(TAG, "WIDTH: " + displayWidth);
        Log.d(TAG, "HEIGHT: " + displayHeight);

        boolean isLandCapse = false;

        if(displayWidth>displayHeight){
            isLandCapse = true;//landcapse
        }
        else
            isLandCapse = false;//portrait

        if(isLandCapse){
            for (int[] res : validResolutions)
            {
                if(res[1]==resolution){
                    displayWidth = res[0];
                    displayHeight = res[1];
                }
            }
        }
        else {
            for (int[] res : validResolutions)
            {
                if(res[1]==resolution){
                    displayWidth = res[1];
                    displayHeight = res[0];
                }
            }
        }
        Log.d(TAG, "WIDTH: " + displayWidth);
        Log.d(TAG, "HEIGHT: " + displayHeight);
    }

    private void startScreenRecorder() {
        mediaProjectionCallback = new MediaProjectionCallback();
        mediaProjection.registerCallback(mediaProjectionCallback,null);
        virtualDisplay = createVirtuaDisplay();
        mediaRecorder.start();
    }

    private VirtualDisplay createVirtuaDisplay() {
        return mediaProjection.createVirtualDisplay("MainActivity",
                displayWidth,displayHeight,mSrceenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.getSurface(),null,null);
    }

    public void start(boolean isClick){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            sourceFiles.add(videoUri);
        }
        if(isClick)
            isRec = true;
        startScreenRecorder();

        isPaused = false;
    }

    public void stop(boolean isClick){
        mediaRecorder.stop();
        mediaRecorder.reset();
        stopRecordScreen();

        isPaused = false;

        if(isClick) {
            isRec = false;

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {

                videoUri = Environment.getExternalStoragePublicDirectory(directory)
                        + new StringBuilder(defaultName).append(new SimpleDateFormat("dd-MM-yyyy-hh_mm_ss")
                        .format(new Date())).append(".mp4").toString();

                Boolean result = mergeMediaFiles(false, sourceFiles, videoUri);

                if (result)
                    Log.d("stop", "success");
                else
                    Log.d("stop", "fail");

                for(int i = 0; i< sourceFiles.size();i++)
                    deleteFiles(sourceFiles.get(i));

                sourceFiles.clear();
            }
        }
    }

    public void resume(WindowManager windowManager, MediaProjection mediaProjection){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder.resume();
        }
        else
        {
            this.start(false);
        }
        isPaused = false;
    }

    public void pause(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder.pause();
        }
        else
        {
            this.stop(false);
        }
        isPaused = true;
    }

    public class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            if (isRec)
            {
                mediaRecorder.stop();
                mediaRecorder.reset();
            }
            mediaProjection = null;
            stopRecordScreen();
            super.onStop();
        }
    }

    private void stopRecordScreen() {
        if(virtualDisplay == null){
            return;
        }
        virtualDisplay.release();
        mediaRecorder.release();
        destroyMediaProjection();
    }

    private void destroyMediaProjection() {
        if(mediaProjection != null)
        {
            mediaProjection.unregisterCallback(mediaProjectionCallback);
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    private boolean mergeMediaFiles(boolean isAudio, ArrayList<String> sourceFiles, String targetFile) {
        try {
            String mediaKey = isAudio ? "soun" : "vide";
            List<Movie> listMovies = new ArrayList<>();
            for (String filename : sourceFiles) {
                listMovies.add(MovieCreator.build(filename));
            }
            List<Track> listTracks = new LinkedList<>();
            for (Movie movie : listMovies) {
                for (Track track : movie.getTracks()) {
                    if (track.getHandler().equals(mediaKey)) {
                        listTracks.add(track);
                    }
                }
            }
            Movie outputMovie = new Movie();
            if (!listTracks.isEmpty()) {
                outputMovie.addTrack(new AppendTrack(listTracks.toArray(new Track[listTracks.size()])));
            }
            Container container = new DefaultMp4Builder().build(outputMovie);
            FileChannel fileChannel = new RandomAccessFile(String.format(targetFile), "rw").getChannel();
            container.writeContainer(fileChannel);
            fileChannel.close();
            return true;
        }
        catch (IOException e) {
            Log.e("mergeMediaFiles", "Error merging media files. exception: "+e.getMessage());
            return false;
        }
    }

    private void deleteFiles(String path) {
//        File file = new File(path);
//
//        if (file.exists()) {
//            String deleteCmd = "rm -r " + path;
//            Runtime runtime = Runtime.getRuntime();
//            try {
//                runtime.exec(deleteCmd);
//            } catch (IOException e) {
//
//            }
//        }
        try
        {
            File file = new File(path);
            if(file.exists())
                file.delete();
        }
        catch (Exception e)
        {
            Log.e("App", "Exception while deleting file " + e.getMessage());
        }

    }
}
