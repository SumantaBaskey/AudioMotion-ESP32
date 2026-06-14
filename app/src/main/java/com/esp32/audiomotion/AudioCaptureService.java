package com.esp32.audiomotion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class AudioCaptureService extends Service {
    static final String CHANNEL_ID = "audiomotion_capture";
    static final int NOTIF_ID = 1;

    private volatile boolean running = false;
    private Thread captureThread;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Audio Capture", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AudioMotion ESP32")
                .setContentText("Capturing audio...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
        startForeground(NOTIF_ID, n);
        startCapture();
        return START_STICKY;
    }

    private void startCapture() {
        if (running) return;
        running = true;
        captureThread = new Thread(() -> {
            int sampleRate = 44100;
            int realFftSize = MainActivity.analyzer != null ? MainActivity.analyzer.getFftSize() : 1024;
            int bufSize = realFftSize;
            int minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT);
            if (minBuf > bufSize) bufSize = minBuf;

            AudioRecord recorder = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                    .setBufferSizeInBytes(bufSize * 4)
                    .build();

            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                stopSelf();
                return;
            }

            recorder.startRecording();
            float[] buf = new float[bufSize];

            while (running) {
                int read = recorder.read(buf, 0, buf.length, AudioRecord.READ_BLOCKING);
                if (read > 0 && MainActivity.analyzer != null && MainActivity.esp32 != null) {
                    float[] bars = MainActivity.analyzer.process(buf);
                    MainActivity.esp32.send(bars);
                }
            }

            recorder.stop();
            recorder.release();
        });
        captureThread.start();
    }

    private void stopCapture() {
        running = false;
        if (captureThread != null) {
            try { captureThread.join(1000); } catch (InterruptedException ignored) {}
            captureThread = null;
        }
    }

    @Override
    public void onDestroy() {
        stopCapture();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    public boolean isRunning() { return running; }
}
