package com.personal.conversoraac;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.MediaInformation;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.StreamInformation;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private MaterialButton btnConvertir, btnAuditar, btnGrabar;
    private TextView tvLog, tvTimer, tvRecordingName;
    private ScrollView scrollView;
    private LinearProgressIndicator progressBar;
    private MaterialCardView recordingPanel;
    private View recordingDot;
    private final String CARPETA = "A Voz en Cuello";

    private MediaRecorder recorder;
    private File archivoTemporal;
    private String nombreGrabacionFinal;
    private boolean estaGrabando = false;

    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private long tiempoInicioGrabacion = 0;
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long millis = System.currentTimeMillis() - tiempoInicioGrabacion;
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
            timerHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnConvertir = findViewById(R.id.btnConvertir);
        btnAuditar = findViewById(R.id.btnAuditar);
        btnGrabar = findViewById(R.id.btnGrabar);
        tvLog = findViewById(R.id.tvLog);
        scrollView = findViewById(R.id.scrollView);
        progressBar = findViewById(R.id.progressBar);
        recordingPanel = findViewById(R.id.recordingPanel);
        tvTimer = findViewById(R.id.tvTimer);
        tvRecordingName = findViewById(R.id.tvRecordingName);
        recordingDot = findViewById(R.id.recordingDot);

        btnConvertir.setOnClickListener(v -> verificarEIniciar(false));
        btnAuditar.setOnClickListener(v -> verificarEIniciar(true));
        btnGrabar.setOnClickListener(v -> toggleGrabacion());
    }

    // --- LÓGICA DE GRABACIÓN ---

    private void toggleGrabacion() {
        if (!estaGrabando) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 101);
                return;
            }
            mostrarDialogoNombre();
        } else {
            detenerGrabacion();
        }
    }

    private void mostrarDialogoNombre() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Nombre de la grabación");
        
        final EditText input = new EditText(this);
        String defaultName = "Grabacion_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        input.setText(defaultName);
        input.setSelection(defaultName.length());
        builder.setView(input);

        builder.setPositiveButton("Grabar", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = defaultName;
            // Limpiamos el nombre para evitar caracteres inválidos en archivos
            nombreGrabacionFinal = name.replaceAll("[^a-zA-Z0-9_\\-\\s]", "");
            iniciarGrabacion();
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void iniciarGrabacion() {
        archivoTemporal = new File(getCacheDir(), "temp_grabacion.m4a");

        recorder = new MediaRecorder();
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioChannels(2);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setOutputFile(archivoTemporal.getAbsolutePath());
            
            recorder.prepare();
            recorder.start();
            
            estaGrabando = true;
            btnGrabar.setText("Detener Grabación");
            btnGrabar.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_fa_stop));
            btnGrabar.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.retroYellow));
            btnGrabar.setTextColor(ContextCompat.getColor(this, R.color.retroBrown));
            
            btnConvertir.setEnabled(false);
            btnAuditar.setEnabled(false);
            
            recordingPanel.setVisibility(View.VISIBLE);
            tvRecordingName.setText(nombreGrabacionFinal);
            recordingDot.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse));
            
            tiempoInicioGrabacion = System.currentTimeMillis();
            timerHandler.postDelayed(timerRunnable, 0);
            
            actualizarLog("[REC] Grabando: " + nombreGrabacionFinal);
        } catch (IOException | IllegalStateException e) {
            actualizarLog("[ERROR] Error al iniciar grabador: " + e.getMessage());
        }
    }

    private void detenerGrabacion() {
        try {
            recorder.stop();
            recorder.release();
            recorder = null;
            estaGrabando = false;
            
            timerHandler.removeCallbacks(timerRunnable);
            recordingDot.clearAnimation();
            recordingPanel.setVisibility(View.GONE);
            
            btnGrabar.setText("Iniciar Grabación");
            btnGrabar.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_fa_mic));
            btnGrabar.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.retroBrown));
            btnGrabar.setTextColor(ContextCompat.getColor(this, R.color.retroYellow));
            
            btnConvertir.setEnabled(true);
            btnAuditar.setEnabled(true);
            actualizarLog("[STOP] Grabación detenida. Procesando formato de radio...");
            
            new Thread(this::procesarGrabacion).start();
            
        } catch (RuntimeException e) {
            actualizarLog("[ERROR] Error al detener grabación: " + e.getMessage());
            estaGrabando = false;
            btnGrabar.setText("Iniciar Grabación");
        }
    }

    private void procesarGrabacion() {
        File dir = new File(Environment.getExternalStorageDirectory(), CARPETA);
        if (!dir.exists()) dir.mkdirs();

        File archivoFinal = new File(dir, nombreGrabacionFinal + ".aac");

        runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));

        String cmd = "-y -i \"" + archivoTemporal.getAbsolutePath() + "\" " +
                     "-vn -c:a aac -profile:a aac_low " +
                     "-b:a 64k -minrate 64k -maxrate 64k -bufsize 128k " +
                     "-ar 44100 -ac 2 -f adts \"" + archivoFinal.getAbsolutePath() + "\"";

        AtomicBoolean exito = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        FFmpegKit.executeAsync(cmd, session -> {
            exito.set(ReturnCode.isSuccess(session.getReturnCode()));
            latch.countDown();
        }, log -> {}, statistics -> {});

        try { latch.await(); } catch (InterruptedException ignored) {}

        if (exito.get() && archivoFinal.exists() && archivoFinal.length() > 0) {
            actualizarLog("[OK] Grabación lista y guardada como: " + archivoFinal.getName());
            archivoTemporal.delete();
        } else {
            actualizarLog("[ERROR] Error al convertir la grabación.");
        }
        
        runOnUiThread(() -> progressBar.setVisibility(View.INVISIBLE));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mostrarDialogoNombre();
        } else {
            Toast.makeText(this, "Se necesita permiso de micrófono para grabar", Toast.LENGTH_SHORT).show();
        }
    }

    // --- LÓGICA DE AUDITORÍA Y CONVERSIÓN ---

    private void verificarEIniciar(boolean esAuditoria) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Toast.makeText(this, "Concede el acceso a archivos primero", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }

        btnConvertir.setEnabled(false);
        btnAuditar.setEnabled(false);
        btnGrabar.setEnabled(false);
        tvLog.setText("");
        
        if (esAuditoria) {
            progressBar.setVisibility(View.INVISIBLE);
            new Thread(this::auditarCarpeta).start();
        } else {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            new Thread(this::procesarCarpeta).start();
        }
    }

    private void auditarCarpeta() {
        File dir = new File(Environment.getExternalStorageDirectory(), CARPETA);
        if (!dir.exists() || !dir.isDirectory()) {
            actualizarLog("No se encontró la carpeta.");
            finalizarUI();
            return;
        }

        File[] archivos = dir.listFiles();
        if (archivos == null) return;

        actualizarLog("--- INICIANDO AUDITORÍA ---\n");
        int malos = 0;

        for (File archivo : archivos) {
            String nombre = archivo.getName().toLowerCase();
            if (nombre.endsWith(".aac") || !(nombre.endsWith(".mp3") || nombre.endsWith(".mp4") || nombre.endsWith(".m4a") || nombre.endsWith(".wav"))) {
                continue;
            }

            try {
                MediaInformation info = FFprobeKit.getMediaInformation(archivo.getAbsolutePath()).getMediaInformation();
                if (info != null) {
                    List<StreamInformation> streams = info.getStreams();
                    if (streams != null && !streams.isEmpty()) {
                        StreamInformation audio = streams.get(0);
                        String rate = audio.getSampleRate();
                        String codec = audio.getCodec();
                        String channelLayout = audio.getChannelLayout();
                        String br = audio.getBitrate();
                        String dur = info.getDuration();

                        String khz = rate != null ? (Integer.parseInt(rate) / 1000.0) + " kHz" : "?";
                        String chStr = channelLayout != null ? (channelLayout.toLowerCase().contains("mono") ? "Mono" : "Estéreo") : "?";
                        String brStr = br != null ? (Integer.parseInt(br) / 1000) + " kbps" : "?";
                        String durStr = "?";
                        if (dur != null) {
                            int totalSecs = (int) Double.parseDouble(dur);
                            durStr = (totalSecs / 60) + ":" + String.format("%02d", totalSecs % 60);
                        }

                        String alerta = "";
                        if (!"44100".equals(rate)) {
                            alerta = "[!] ";
                            malos++;
                        }

                        actualizarLog(alerta + archivo.getName() + "\n   Codec: " + codec + " | " + khz + " | " + chStr + "\n   Bitrate: " + brStr + " | Dur: " + durStr + "\n");
                    }
                }
            } catch (Exception ignored) {}
        }

        actualizarLog("--- FIN DE AUDITORÍA ---\n");
        if (malos > 0) actualizarLog("ATENCIÓN: " + malos + " archivo(s) no están a 44.1 kHz.");
        else actualizarLog("Todos los archivos están a 44.1 kHz.");
        finalizarUI();
    }

    private void procesarCarpeta() {
        File dir = new File(Environment.getExternalStorageDirectory(), CARPETA);
        if (!dir.exists() || !dir.isDirectory()) {
            actualizarLog("No se encontró la carpeta.");
            finalizarUI();
            return;
        }

        File[] archivos = dir.listFiles();
        if (archivos == null) return;

        int convertidos = 0, fallidos = 0;

        for (File archivo : archivos) {
            String nombre = archivo.getName().toLowerCase();
            if (nombre.endsWith(".aac") || !(nombre.endsWith(".mp3") || nombre.endsWith(".mp4") || nombre.endsWith(".m4a") || nombre.endsWith(".wav"))) continue;

            String base = archivo.getName().substring(0, archivo.getName().lastIndexOf("."));
            File salida = new File(dir, base + ".aac");

            if (salida.exists() && salida.length() > 0) {
                actualizarLog("Saltando (ya existe): " + archivo.getName());
                continue;
            }

            actualizarLog("Procesando: " + archivo.getName());
            runOnUiThread(() -> progressBar.setProgress(0));

            long duracionMs = 0;
            try {
                MediaInformation info = FFprobeKit.getMediaInformation(archivo.getAbsolutePath()).getMediaInformation();
                if (info != null && info.getDuration() != null) duracionMs = (long) (Double.parseDouble(info.getDuration()) * 1000);
            } catch (Exception ignored) {}

            String cmd = "-y -i \"" + archivo.getAbsolutePath() + "\" -vn -c:a aac -profile:a aac_low -b:a 64k -minrate 64k -maxrate 64k -bufsize 128k -ar 44100 -ac 2 -f adts \"" + salida.getAbsolutePath() + "\"";
            AtomicBoolean exito = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);
            final long finalDuracionMs = duracionMs;

            FFmpegKit.executeAsync(cmd, session -> {
                exito.set(ReturnCode.isSuccess(session.getReturnCode()));
                latch.countDown();
            }, log -> {}, statistics -> {
                if (finalDuracionMs > 0 && statistics != null && statistics.getTime() >= 0) {
                    int progreso = (int) ((statistics.getTime() / (double) finalDuracionMs) * 100);
                    runOnUiThread(() -> progressBar.setProgress(Math.min(progreso, 100)));
                }
            });

            try { latch.await(); } catch (InterruptedException ignored) {}

            if (exito.get() && salida.exists() && salida.length() > 0) {
                actualizarLog("  Convertido y borrado OK");
                archivo.delete();
                convertidos++;
            } else {
                actualizarLog("  Error en la conversión");
                if (salida.exists()) salida.delete();
                fallidos++;
            }
        }

        actualizarLog("\nProceso Finalizado.\nConvertidos: " + convertidos + "\nFallidos: " + fallidos);
        finalizarUI();
    }

    private void finalizarUI() {
        runOnUiThread(() -> {
            btnConvertir.setEnabled(true);
            btnAuditar.setEnabled(true);
            btnGrabar.setEnabled(true);
            progressBar.setVisibility(View.INVISIBLE);
        });
    }

    private void actualizarLog(String msj) {
        runOnUiThread(() -> {
            tvLog.append(msj + "\n");
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }
}
