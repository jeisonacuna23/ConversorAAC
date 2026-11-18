package com.personal.conversoraac;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
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
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
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

    private boolean estaGrabando = false;
    private String nombreGrabacionFinal;

    // Escucha los mensajes que envía el Servicio (tiempo y finalización)
    private BroadcastReceiver serviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (RecordingService.ACTION_UPDATE_UI.equals(intent.getAction())) {
                String time = intent.getStringExtra(RecordingService.EXTRA_TIME);
                tvTimer.setText(time);
            } else if (RecordingService.ACTION_FINISHED.equals(intent.getAction())) {
                String msg = intent.getStringExtra(RecordingService.EXTRA_MESSAGE);
                actualizarLog(msg);
                finalizarGrabacionUI();
            }
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

        // Registrar el receptor para Android 13+ y anteriores
        IntentFilter filter = new IntentFilter();
        filter.addAction(RecordingService.ACTION_UPDATE_UI);
        filter.addAction(RecordingService.ACTION_FINISHED);
        ContextCompat.registerReceiver(this, serviceReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(serviceReceiver);
    }

    // --- LÓGICA DE GRABACIÓN ---

    private void toggleGrabacion() {
        if (!estaGrabando) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 101);
                return;
            }
            // Pedir permiso de notificaciones si es Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 102);
                return;
            }
            mostrarDialogoNombre();
        } else {
            detenerGrabacionUI();
        }
    }

    private void mostrarDialogoNombre() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_name, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.7f);
        }

        TextInputEditText etNombre = view.findViewById(R.id.etNombre);
        MaterialButton btnCancelar = view.findViewById(R.id.btnCancelar);
        MaterialButton btnGrabarConfirmar = view.findViewById(R.id.btnGrabarConfirmar);

        String defaultName = "Grabacion_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        etNombre.setText(defaultName);
        etNombre.setSelection(defaultName.length());

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        btnGrabarConfirmar.setOnClickListener(v -> {
            String name = etNombre.getText() != null ? etNombre.getText().toString().trim() : "";
            if (name.isEmpty()) name = defaultName;
            nombreGrabacionFinal = name.replaceAll("[^a-zA-Z0-9_\\-\\s]", "");
            dialog.dismiss();
            iniciarGrabacionUI();
        });

        dialog.show();
    }

    private void iniciarGrabacionUI() {
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

        // Enviar la orden de START al servicio
        Intent serviceIntent = new Intent(this, RecordingService.class);
        serviceIntent.setAction(RecordingService.ACTION_START);
        serviceIntent.putExtra(RecordingService.EXTRA_NAME, nombreGrabacionFinal);
        ContextCompat.startForegroundService(this, serviceIntent);
        
        actualizarLog("[REC] Grabando: " + nombreGrabacionFinal);
    }

    private void detenerGrabacionUI() {
        // Enviar la orden de STOP al servicio
        Intent serviceIntent = new Intent(this, RecordingService.class);
        serviceIntent.setAction(RecordingService.ACTION_STOP);
        startService(serviceIntent);
        
        actualizarLog("[STOP] Deteniendo y procesando...");
        recordingDot.clearAnimation();
        recordingPanel.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE); // Mostrar barra mientras FFmpeg procesa
    }

    private void finalizarGrabacionUI() {
        estaGrabando = false;
        btnGrabar.setText("Iniciar Grabación");
        btnGrabar.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_fa_mic));
        btnGrabar.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.retroBrown));
        btnGrabar.setTextColor(ContextCompat.getColor(this, R.color.retroYellow));
        
        btnConvertir.setEnabled(true);
        btnAuditar.setEnabled(true);
        progressBar.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mostrarDialogoNombre();
        } else if (requestCode == 102) {
            mostrarDialogoNombre(); // Continuar aunque niegue las notificaciones
        } else if (requestCode == 101) {
            Toast.makeText(this, "Se necesita permiso de micrófono", Toast.LENGTH_SHORT).show();
        }
    }

    // --- LÓGICA DE AUDITORÍA Y CONVERSIÓN (Igual que antes) ---

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
