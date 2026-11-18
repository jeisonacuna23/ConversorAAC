package com.personal.conversoraac;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecordingService extends Service {

    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String ACTION_UPDATE_UI = "ACTION_UPDATE_UI";
    public static final String ACTION_FINISHED = "ACTION_FINISHED";
    public static final String EXTRA_NAME = "EXTRA_NAME";
    public static final String EXTRA_TIME = "EXTRA_TIME";
    public static final String EXTRA_MESSAGE = "EXTRA_MESSAGE";

    private static final String CHANNEL_ID = "GrabacionChannel";
    private static final int NOTIFICATION_ID = 1;

    // Variables estáticas para saber el estado desde la pantalla principal
    public static boolean isRunning = false;
    public static String currentName = "";
    public static String currentTime = "00:00";

    private MediaRecorder recorder;
    private File archivoTemporal;
    private String nombreGrabacion;
    private long tiempoInicio;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRecording = false;

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRecording) return;
            long millis = System.currentTimeMillis() - tiempoInicio;
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            String timeStr = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);

            currentTime = timeStr; // Guardar tiempo para la UI

            updateNotification(timeStr);

            Intent intent = new Intent(ACTION_UPDATE_UI);
            intent.putExtra(EXTRA_TIME, timeStr);
            sendBroadcast(intent);

            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals(ACTION_START)) {
                nombreGrabacion = intent.getStringExtra(EXTRA_NAME);
                iniciarGrabacion();
            } else if (intent.getAction().equals(ACTION_STOP)) {
                detenerGrabacionYProcesar();
            }
        }
        return START_NOT_STICKY;
    }

    private void iniciarGrabacion() {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("00:00"));

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

            isRecording = true;
            isRunning = true;       // <-- Indicar que está corriendo
            currentName = nombreGrabacion;
            currentTime = "00:00";

            tiempoInicio = System.currentTimeMillis();
            handler.postDelayed(timerRunnable, 0);

        } catch (IOException | IllegalStateException e) {
            stopSelf();
        }
    }

    private void detenerGrabacionYProcesar() {
        isRecording = false;
        isRunning = false; // <-- Indicar que ya no está corriendo
        handler.removeCallbacks(timerRunnable);

        try {
            recorder.stop();
            recorder.release();
        } catch (Exception ignored) {}

        updateNotification("Procesando...");

        new Thread(() -> {
            File dir = new File(Environment.getExternalStorageDirectory(), "A Voz en Cuello");
            if (!dir.exists()) dir.mkdirs();
            File archivoFinal = new File(dir, nombreGrabacion + ".aac");

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

            String msgFinal;
            if (exito.get() && archivoFinal.exists() && archivoFinal.length() > 0) {
                msgFinal = "[OK] Grabación lista: " + archivoFinal.getName();
                archivoTemporal.delete();
            } else {
                msgFinal = "[ERROR] Error al convertir la grabación.";
            }

            Intent intent = new Intent(ACTION_FINISHED);
            intent.putExtra(EXTRA_MESSAGE, msgFinal);
            sendBroadcast(intent);

            stopForeground(true);
            stopSelf();
        }).start();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Grabación en curso",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification buildNotification(String time) {
        Intent stopIntent = new Intent(this, RecordingService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Grabando: " + nombreGrabacion)
                .setContentText("Tiempo: " + time)
                .setSmallIcon(R.drawable.ic_fa_mic)
                .setContentIntent(openAppPendingIntent)
                .addAction(R.drawable.ic_fa_stop, "Detener", stopPendingIntent) // Icono de stop
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible en pantalla de bloqueo
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String time) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(time));
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
