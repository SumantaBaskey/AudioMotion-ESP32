package com.esp32.audiomotion;

public class FFTAnalyzer {
    private int fftSize, sampleRate;
    private float[] window;
    private float[] prevBars;
    private float smoothing = 0.5f;
    private float minFreq = 20, maxFreq = 20000;
    private int scaleMode = 2; // 0=bark 1=linear 2=log 3=mel
    private int weighting = 0; // 0=none 1=A 2=B 3=C 4=D 5=468
    private float minDecibels = -85, maxDecibels = -25;
    private boolean linearAmplitude = false;
    private float linearBoost = 1;

    public FFTAnalyzer(int fftSize, int sampleRate) {
        this.fftSize = fftSize;
        this.sampleRate = sampleRate;
        this.window = new float[fftSize];
        this.prevBars = new float[16];
        for (int i = 0; i < fftSize; i++)
            window[i] = (float)(0.5 * (1 - Math.cos(2 * Math.PI * i / (fftSize - 1))));
    }

    // ── Settings ───────────────────────────────────────────────────────
    public void setSmoothing(float v) { smoothing = v; }
    public void setMinFreq(float v) { minFreq = v; }
    public void setMaxFreq(float v) { maxFreq = v; }
    public void setScaleMode(int v) { scaleMode = v; }
    public void setWeighting(int v) { weighting = v; }
    public void setMinDecibels(float v) { minDecibels = v; }
    public void setMaxDecibels(float v) { maxDecibels = v; }
    public void setLinearAmplitude(boolean v) { linearAmplitude = v; }
    public void setLinearBoost(float v) { linearBoost = v; }

    // ── Frequency scale helpers ────────────────────────────────────────
    private float freqAt(int bin) { return (float)bin * sampleRate / fftSize; }

    private float freqToScale(float hz) {
        switch (scaleMode) {
            case 0: // bark
                float z = 13 * (float)Math.atan(0.00076 * hz) + 3.5f * (float)Math.atan((hz / 7500) * (hz / 7500));
                return z;
            case 1: return hz; // linear
            case 2: return (float)Math.log(hz); // log
            case 3: // mel
                return 2595 * (float)Math.log10(1 + hz / 700);
            default: return (float)Math.log(hz);
        }
    }

    private float scaleToFreq(float s) {
        switch (scaleMode) {
            case 0: // bark → hz (approx)
                return 1;
            case 1: return s;
            case 2: return (float)Math.exp(s);
            case 3:
                return 700 * (float)(Math.pow(10, s / 2595) - 1);
            default: return (float)Math.exp(s);
        }
    }

    // ── Weighting filters ──────────────────────────────────────────────
    private float weightingGain(float hz) {
        float f = hz;
        float f2 = f * f, f4 = f2 * f2;
        float h2 = 20.6f * 20.6f, h4 = 107.7f * 107.7f, h5 = 737.9f * 737.9f, h6 = 158.5f * 158.5f;
        float h8 = 12194f * 12194f;
        switch (weighting) {
            case 1: { // A-weighting
                float ra = (h8 * f4) / ((f2 + h2) * (float)Math.sqrt((f2 + h4) * (f2 + h5)) * (f2 + h8));
                return 20 * (float)Math.log10(ra) + 2;
            }
            case 2: { // B-weighting
                float rb = (h8 * f2 * f) / ((f2 + h2) * (float)Math.sqrt(f2 + h6) * (f2 + h8));
                return 20 * (float)Math.log10(rb) + 0.17f;
            }
            case 3: { // C-weighting
                float rc = (h8 * f2) / ((f2 + h2) * (f2 + h8));
                return 20 * (float)Math.log10(rc) + 0.06f;
            }
            case 4: { // D-weighting
                float f2 = f * f;
                float hf = 2 * (float)Math.log10(f / 1000);
                float rd = (f / (f + 0.346f)) * (float)Math.exp(-0.7 * hf * hf);
                return 20 * (float)Math.log10(rd);
            }
            case 5: { // 468-weighting (ITU-R 468)
                float f2 = f * f;
                float ra = 1.2463328f * (float)Math.pow(10, 4);
                float rb = (float)Math.pow(f2 + 28.056f * 28.056f, 2);
                float rc = (f2 + 20.598997f * 20.598997f) * (float)Math.sqrt((f2 + 107.65265f * 107.65265f) * (f2 + 737.86223f * 737.86223f));
                float rd = (f2 + 12194.217f * 12194.217f);
                float r = ra / (rb * rc * rd);
                return 20 * (float)Math.log10(r) - 2.58f;
            }
            default: return 0;
        }
    }

    // ── FFT (Cooley-Tukey radix-2) ────────────────────────────────────
    private void fft(float[] real, float[] imag) {
        int n = real.length;
        int bits = 0;
        while ((1 << bits) < n) bits++;

        for (int i = 0; i < n; i++) {
            int j = Integer.reverse(i) >>> (32 - bits);
            if (j > i) {
                float tmp = real[i]; real[i] = real[j]; real[j] = tmp;
                tmp = imag[i]; imag[i] = imag[j]; imag[j] = tmp;
            }
        }

        for (int len = 2; len <= n; len <<= 1) {
            float ang = (float)(2 * Math.PI / len);
            for (int i = 0; i < n; i += len) {
                for (int j = 0; j < len / 2; j++) {
                    float w = ang * j;
                    float wReal = (float)Math.cos(w);
                    float wImag = (float)Math.sin(w);
                    int idx = i + j + len / 2;
                    float tReal = real[idx] * wReal - imag[idx] * wImag;
                    float tImag = real[idx] * wImag + imag[idx] * wReal;
                    real[idx] = real[i + j] - tReal;
                    imag[idx] = imag[i + j] - tImag;
                    real[i + j] += tReal;
                    imag[i + j] += tImag;
                }
            }
        }
    }

    // ── Process PCM data → 16 bars ────────────────────────────────────
    public float[] process(float[] pcm) {
        int len = Math.min(pcm.length, fftSize);
        float[] real = new float[fftSize];
        float[] imag = new float[fftSize];

        for (int i = 0; i < fftSize; i++) {
            if (i < len) real[i] = pcm[i] * window[i];
            else real[i] = 0;
            imag[i] = 0;
        }

        fft(real, imag);

        int numBins = fftSize / 2;
        float[] magnitude = new float[numBins];

        for (int i = 0; i < numBins; i++) {
            float mag = (float)Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
            float db = (float)(20 * Math.log10(mag + 1e-10f));

            // Apply weighting
            float hz = freqAt(i);
            db += weightingGain(hz);

            // Clamp
            if (db < minDecibels) db = minDecibels;
            if (db > maxDecibels) db = maxDecibels;

            // Normalize 0-1
            float norm = (db - minDecibels) / (maxDecibels - minDecibels);
            if (norm < 0) norm = 0;
            if (norm > 1) norm = 1;

            if (linearAmplitude) {
                norm = (float)Math.pow(norm, 0.5) * linearBoost;
                if (norm > 1) norm = 1;
            }

            magnitude[i] = norm;
        }

        // Map to 16 bars
        float[] bars = new float[16];
        float scaleMin = freqToScale(minFreq);
        float scaleMax = freqToScale(maxFreq);

        for (int b = 0; b < 16; b++) {
            float sStart = scaleMin + (scaleMax - scaleMin) * b / 16f;
            float sEnd = scaleMin + (scaleMax - scaleMin) * (b + 1) / 16f;
            float hzStart = scaleToFreq(sStart);
            float hzEnd = scaleToFreq(sEnd);

            int binStart = Math.max(0, (int)(hzStart * fftSize / sampleRate));
            int binEnd = Math.min(numBins - 1, (int)(hzEnd * fftSize / sampleRate) + 1);
            if (binEnd <= binStart) binEnd = binStart + 1;

            float maxVal = 0;
            for (int i = binStart; i <= binEnd; i++) {
                if (magnitude[i] > maxVal) maxVal = magnitude[i];
            }

            // Apply smoothing
            if (smoothing > 0 && prevBars[b] >= 0) {
                bars[b] = prevBars[b] * smoothing + maxVal * (1 - smoothing);
            } else {
                bars[b] = maxVal;
            }
        }

        System.arraycopy(bars, 0, prevBars, 0, 16);
        return bars;
    }
}
