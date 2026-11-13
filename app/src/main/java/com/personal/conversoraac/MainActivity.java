package com.personal.conversoraac;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private Button boton;
    private TextView logView;
    private final String CARPETA = "A Voz en Cuello";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 40, 40, 40);

        TextView titulo = new TextView(this);
        titulo.setText("Conversor AAC 64 kbps");
        titulo.setTextSize(22);
        titulo.setGravity(Gravity.CENTER);
        root.addView(titulo);

        boton = new Button(this);
        boton.setText("Iniciar Conversión");
        boton.setOnClickListener(v -> verificarEIniciar());
        root.addView(boton);

        ScrollView scroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextSize(12);
        logView.setPadding(0, 20, 0, 20);
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        setContentView(root);
    }

    private void verificarEIniciar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Toast.makeText(this, "Concede el acceso a archivos primero", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }

        boton.setEnabled(false);
        logView.setText("Iniciando...\n");
        new Thread(this::procesarCarpeta).start();
    }

    private void procesarCarpeta() {
        File dir = new File(Environment.getExternalStorageDirectory(), CARPETA);
        if (!dir.exists() || !dir.isDirectory()) {
            actualizarLog("❌ No se encontró la carpeta: " + dir.getAbsolutePath());
            runOnUiThread(() -> boton.setEnabled(true));
            return;
        }

        File[] archivos = dir.listFiles();
        if (archivos == null) return;

        int convertidos = 0;
        int fallidos = 0;

        for (File archivo : archivos) {
            String nombre = archivo.getName().toLowerCase();
            // Ignorar si ya es .aac o no es un audio/video soportado
            if (nombre.endsWith(".aac") || !(nombre.endsWith(".mp3") || nombre.endsWith(".mp4") || nombre.endsWith(".m4a") || nombre.endsWith(".wav"))) {
                continue;
            }

            String base = archivo.getName().substring(0, archivo.getName().lastIndexOf("."));
            File salida = new File(dir, base + ".aac");

            // Si ya existe el .aac, lo saltamos para no duplicar
            if (salida.exists() && salida.length() > 0) {
                actualizarLog("⏭ Saltando (ya existe): " + archivo.getName());
                continue;
            }

            actualizarLog("🎵 Convirtiendo: " + archivo.getName());

            // TU COMANDO EXACTO DE FFMPEG
            String cmd = "-y -i \"" + archivo.getAbsolutePath() + "\" " +
                         "-vn -c:a aac -profile:a aac_low " +
                         "-b:a 64k -minrate 64k -maxrate 64k -bufsize 128k " +
                         "-ar 44100 -ac 2 -f adts \"" + salida.getAbsolutePath() + "\"";

            ReturnCode rc = FFmpegKit.execute(cmd).getReturnCode();

            if (ReturnCode.isSuccess(rc) && salida.exists() && salida.length() > 0) {
                actualizarLog("   ✅ OK → Borrando original");
                archivo.delete();
                convertidos++;
            } else {
                actualizarLog("   ❌ Falló la conversión (Original intacto)");
                if (salida.exists()) salida.delete();
                fallidos++;
            }
        }

        int c = convertidos; int f = fallidos;
        actualizarLog("\n🏁 Terminado.\nConvertidos y borrados: " + c + "\nFallidos: " + f);
        runOnUiThread(() -> boton.setEnabled(true));
    }

    private void actualizarLog(String msj) {
        runOnUiThread(() -> {
            logView.append(msj + "\n");
            // Auto-scroll al final
            if (logView.getParent() instanceof ScrollView) {
                ((ScrollView) logView.getParent()).post(() -> ((ScrollView) logView.getParent()).fullScroll(ScrollView.FOCUS_DOWN));
            }
        });
    }
}
