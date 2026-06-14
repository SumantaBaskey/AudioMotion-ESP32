package com.esp32.audiomotion;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class ESP32Client {
    private String ip = "192.168.4.1";
    private int port = 80;
    private int fps = 20;
    private long lastSend = 0;
    private long minInterval;
    private boolean enabled = true;

    public ESP32Client() { setFps(fps); }

    public void setIp(String ip) { this.ip = ip; }
    public void setFps(int fps) {
        this.fps = fps;
        this.minInterval = Math.max(16, 1000 / fps);
    }
    public void setEnabled(boolean e) { this.enabled = e; }
    public boolean isEnabled() { return enabled; }

    public void send(float[] bars) {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        if (now - lastSend < minInterval) return;
        lastSend = now;

        StringBuilder sb = new StringBuilder("http://");
        sb.append(ip).append("/s?d=");
        for (int i = 0; i < bars.length; i++) {
            if (i > 0) sb.append(',');
            int val = Math.round(Math.max(0, Math.min(16, bars[i] * 16)));
            sb.append(val);
        }

        final String url = sb.toString();
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(1000);
                c.setReadTimeout(1000);
                c.setRequestMethod("GET");
                c.getResponseCode();
                c.disconnect();
            } catch (IOException ignored) {}
        }).start();
    }
}
