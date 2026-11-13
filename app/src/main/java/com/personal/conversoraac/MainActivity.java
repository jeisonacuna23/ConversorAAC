package com.personal.conversoraac;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

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

        Button boton = new Button(this);
        boton.setText("Convertir carpeta");
        boton.setOnClickListener(v ->
            Toast.makeText(this, "Aquí irá la conversión", Toast.LENGTH_SHORT).show()
        );
        root.addView(boton);

        setContentView(root);
    }
}
