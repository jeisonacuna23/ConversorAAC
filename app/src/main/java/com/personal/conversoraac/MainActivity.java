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
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button botonConvertir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(60, 60, 60, 60);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView titulo = new TextView(this);
        titulo.setText("Conversor AAC 64 kbps");
        titulo.setTextSize(22);
        titulo.setGravity(Gravity.CENTER);
        root.addView(titulo);

        TextView subtitulo = new TextView(this);
        subtitulo.setText("44.1 kHz · Estéreo · ADTS");
        subtitulo.setTextSize(14);
        subtitulo.setGravity(Gravity.CENTER);
        subtitulo.setPadding(0, 10, 0, 40);
        root.addView(subtitulo);

        botonConvertir = new Button(this);
        botonConvertir.setText("Verificar Permisos");
        botonConvertir.setOnClickListener(v -> verificarPermisos());
        root.addView(botonConvertir);

        setContentView(root);
    }

    private void verificarPermisos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Toast.makeText(this, "¡Permiso concedido! Listo para convertir.", Toast.LENGTH_LONG).show();
                botonConvertir.setText("Convertir carpeta");
                // Aquí irá la lógica de conversión después
            } else {
                Toast.makeText(this, "Necesitamos acceso a los archivos", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } else {
            // Para Android 10 o anterior, el permiso del manifiesto es suficiente
            Toast.makeText(this, "Permiso listo para convertir.", Toast.LENGTH_LONG).show();
            botonConvertir.setText("Convertir carpeta");
        }
    }
}
