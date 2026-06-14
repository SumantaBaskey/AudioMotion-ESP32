package com.esp32.audiomotion;

import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class AudioCaptureService extends Service {
    private volatile boolean running = false;
    private Thread captureThread;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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

            AudioRecord recorder;
            try {
                recorder = new AudioRecord.Builder()
                        .setAudioSource(MediaRecorder.AudioSource.MIC)
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .build())
                        .setBufferSizeInBytes(bufSize * 4)
                        .build();
            } catch (Exception e) {
                stopSelf();
                return;
            }

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
}
