package com.esp32.audiomotion;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_RECORD_AUDIO = 100;

    private Button btnCapture;
    private TextView tvStatus;

    static FFTAnalyzer analyzer;
    static ESP32Client esp32;
    private AudioCaptureService service;
    private boolean capturing = false;

    private Spinner spFftSize, spScale, spWeighting;
    private SeekBar sbSmoothing, sbMinDb, sbMaxDb, sbBoost, sbFps;
    private TextView tvSmoothing, tvMinDb, tvMaxDb, tvBoost, tvFps;
    private EditText etIp, etMinFreq, etMaxFreq;
    private Button btnLinearAmp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("AudioMotion ESP32");

        analyzer = new FFTAnalyzer(1024, 44100);
        esp32 = new ESP32Client();

        btnCapture = findViewById(R.id.btnCapture);
        tvStatus = findViewById(R.id.tvStatus);
        etIp = findViewById(R.id.etIp);

        spFftSize = findViewById(R.id.spFftSize);
        spFftSize.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"256", "512", "1024", "2048", "4096", "8192", "16384", "32768"}));
        spFftSize.setSelection(2);
        spFftSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, android.view.View v, int pos, long id) {
                int size = Integer.parseInt((String) p.getItemAtPosition(pos));
                analyzer = new FFTAnalyzer(size, 44100);
                applySettings();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        spScale = findViewById(R.id.spScale);
        spScale.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"Bark", "Linear", "Log", "Mel"}));
        spScale.setSelection(2);
        spScale.setOnItemSelectedListener(simpleListener(() -> applySettings()));

        spWeighting = findViewById(R.id.spWeighting);
        spWeighting.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"None", "A", "B", "C", "D", "468"}));
        spWeighting.setOnItemSelectedListener(simpleListener(() -> applySettings()));

        sbSmoothing = findViewById(R.id.sbSmoothing);
        tvSmoothing = findViewById(R.id.tvSmoothing);
        sbSmoothing.setProgress(50);
        tvSmoothing.setText("0.50");

        sbMinDb = findViewById(R.id.sbMinDb);
        tvMinDb = findViewById(R.id.tvMinDb);
        sbMinDb.setMax(30); // -120 to -60
        sbMinDb.setProgress(17); // -86 ≈ -85
        tvMinDb.setText("-85");

        sbMaxDb = findViewById(R.id.sbMaxDb);
        tvMaxDb = findViewById(R.id.tvMaxDb);
        sbMaxDb.setMax(40);
        sbMaxDb.setProgress(15);
        tvMaxDb.setText("-25");

        etMinFreq = findViewById(R.id.etMinFreq);
        etMaxFreq = findViewById(R.id.etMaxFreq);
        etMinFreq.setText("20");
        etMaxFreq.setText("20000");

        btnLinearAmp = findViewById(R.id.btnLinearAmp);

        sbBoost = findViewById(R.id.sbBoost);
        tvBoost = findViewById(R.id.tvBoost);
        sbBoost.setMax(30);
        sbBoost.setProgress(0);
        tvBoost.setText("1.0");

        sbFps = findViewById(R.id.sbFps);
        tvFps = findViewById(R.id.tvFps);
        sbFps.setProgress(20);
        tvFps.setText("20");

        sbSmoothing.setOnSeekBarChangeListener(seekListener(tvSmoothing, v -> {
            tvSmoothing.setText(String.format("%.2f", v / 100f));
            applySettings();
        }));
        sbMinDb.setOnSeekBarChangeListener(seekListener(tvMinDb, v -> {
            int val = -120 + v * 2;
            tvMinDb.setText(String.valueOf(val));
            applySettings();
        }));
        sbMaxDb.setOnSeekBarChangeListener(seekListener(tvMaxDb, v -> {
            int val = -40 + v;
            tvMaxDb.setText(String.valueOf(val));
            applySettings();
        }));
        sbBoost.setOnSeekBarChangeListener(seekListener(tvBoost, v -> {
            tvBoost.setText(String.format("%.1f", v / 10f));
            applySettings();
        }));
        sbFps.setOnSeekBarChangeListener(seekListener(tvFps, v -> {
            tvFps.setText(String.valueOf(v));
            esp32.setFps(v);
        }));

        etIp.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int s2, int c, int a2) {}
            @Override public void onTextChanged(CharSequence s, int s2, int c, int a2) { esp32.setIp(s.toString().trim()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        etMinFreq.addTextChangedListener(freqListener(() -> applySettings()));
        etMaxFreq.addTextChangedListener(freqListener(() -> applySettings()));

        btnLinearAmp.setOnClickListener(v -> {
            btnLinearAmp.setSelected(!btnLinearAmp.isSelected());
            applySettings();
        });

        btnCapture.setOnClickListener(v -> {
            if (capturing) stopCapture();
            else startCapture();
        });
    }

    private void startCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_RECORD_AUDIO);
            return;
        }

        applySettings();
        esp32.setEnabled(true);
        Intent intent = new Intent(this, AudioCaptureService.class);
        startService(intent);
        capturing = true;
        btnCapture.setText("Stop");
        tvStatus.setText("Capturing...");
    }

    private void stopCapture() {
        stopService(new Intent(this, AudioCaptureService.class));
        esp32.setEnabled(false);
        capturing = false;
        btnCapture.setText("Start");
        tvStatus.setText("Idle");
    }

    private void applySettings() {
        analyzer.setSmoothing(sbSmoothing.getProgress() / 100f);
        analyzer.setMinDecibels(-120 + sbMinDb.getProgress() * 2);
        analyzer.setMaxDecibels(-40 + sbMaxDb.getProgress());
        analyzer.setScaleMode(spScale.getSelectedItemPosition());
        analyzer.setWeighting(spWeighting.getSelectedItemPosition());
        analyzer.setLinearAmplitude(btnLinearAmp.isSelected());
        analyzer.setLinearBoost(sbBoost.getProgress() / 10f);
        try {
            float minF = Float.parseFloat(etMinFreq.getText().toString());
            float maxF = Float.parseFloat(etMaxFreq.getText().toString());
            analyzer.setMinFreq(minF);
            analyzer.setMaxFreq(maxF);
        } catch (Exception ignored) {}
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == PERMISSION_RECORD_AUDIO && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED)
            startCapture();
        else
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show();
    }

    private SeekBar.OnSeekBarChangeListener seekListener(TextView tv, SeekBarConsumer c) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int v, boolean u) { c.accept(v); }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        };
    }

    private AdapterView.OnItemSelectedListener simpleListener(Runnable r) {
        return new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, android.view.View v, int pos, long id) { r.run(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        };
    }

    private TextWatcher freqListener(Runnable r) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int s2, int c, int a2) {}
            @Override public void onTextChanged(CharSequence s, int s2, int c, int a2) { r.run(); }
            @Override public void afterTextChanged(Editable s) {}
        };
    }

    private interface SeekBarConsumer {
        void accept(int v);
    }
}
