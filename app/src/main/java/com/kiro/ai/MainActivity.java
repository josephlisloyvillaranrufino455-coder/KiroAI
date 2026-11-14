package com.kiro.ai;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private boolean kiroActivo = true;
    private TextView textoEstado;
    private TextView textoRespuesta;
    private EditText cajaClave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int ancho = metrics.widthPixels;
        int alto = metrics.heightPixels;
        float densidad = metrics.density;

        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 50, 40, 50);

        TextView titulo = new TextView(this);
        titulo.setText("KiroAI - Nucleo Central");
        titulo.setTextSize(22);

        TextView infoPantalla = new TextView(this);
        infoPantalla.setText("Pantalla calibrada: " + ancho + "x" + alto + " px (Densidad: " + densidad + ")");
        infoPantalla.setPadding(0, 15, 0, 25);

        Button btnApagado = new Button(this);
        btnApagado.setText("APAGAR KIROAI (EMERGENCIA)");
        btnApagado.setOnClickListener(v -> {
            kiroActivo = !kiroActivo;
            if (kiroActivo) {
                btnApagado.setText("APAGAR KIROAI (EMERGENCIA)");
                textoEstado.setText("Estado: ENCENDIDO");
            } else {
                btnApagado.setText("ENCENDER KIROAI");
                textoEstado.setText("Estado: APAGADO TOTAL");
                textoRespuesta.setText("KiroAI detenido por seguridad.");
            }
        });

        textoEstado = new TextView(this);
        textoEstado.setText("Estado: ENCENDIDO");
        textoEstado.setPadding(0, 15, 0, 25);

        cajaClave = new EditText(this);
        cajaClave.setHint("Pega aqui tu clave API (AQ.Ab8...)");

        Button btnProbar = new Button(this);
        btnProbar.setText("PROBAR CEREBRO GEMINI");
        btnProbar.setOnClickListener(v -> {
            if (!kiroActivo) {
                textoRespuesta.setText("Enciende KiroAI primero.");
                return;
            }
            String clave = cajaClave.getText().toString().trim();
            if (clave.isEmpty()) {
                textoRespuesta.setText("Pega tu clave API arriba primero.");
                return;
            }
            textoRespuesta.setText("Conectando con el cerebro de Gemini...");
            probarConexionCerebro(clave);
        });

        textoRespuesta = new TextView(this);
        textoRespuesta.setText("Esperando prueba...");
        textoRespuesta.setPadding(0, 30, 0, 30);
        textoRespuesta.setTextSize(16);

        layout.addView(titulo);
        layout.addView(infoPantalla);
        layout.addView(btnApagado);
        layout.addView(textoEstado);
        layout.addView(cajaClave);
        layout.addView(btnProbar);
        layout.addView(textoRespuesta);
        scroll.addView(layout);
        setContentView(scroll);
    }

    private void probarConexionCerebro(String apiKey) {
        new Thread(() -> {
            try {
                String host = "https://" + "aiplatform" + ".googleapis.com";
                String ruta = "/v1/publishers/google/models/gemini-2.5-flash:generateContent?key=" + apiKey;
                URL url = new URL(host + ruta);

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);

                JSONObject parteTexto = new JSONObject();
                parteTexto.put("text", "Hola KiroAI, responde en una frase corta confirmando que tu cerebro esta conectado y listo.");

                JSONArray partes = new JSONArray();
                partes.put(parteTexto);

                JSONObject contenido = new JSONObject();
                contenido.put("role", "user");
                contenido.put("parts", partes);

                JSONArray contenidos = new JSONArray();
                contenidos.put(contenido);

                JSONObject cuerpo = new JSONObject();
                cuerpo.put("contents", contenidos);

                OutputStream os = conn.getOutputStream();
                os.write(cuerpo.toString().getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();
                BufferedReader br;
                if (code >= 200 && code < 300) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                } else {
                    br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                }

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();

                String respuestaCruda = sb.toString();
                runOnUiThread(() -> {
                    if (code >= 200 && code < 300) {
                        try {
                            JSONObject json = new JSONObject(respuestaCruda);
                            String mensaje = json.getJSONArray("candidates")
                                    .getJSONObject(0)
                                    .getJSONObject("content")
                                    .getJSONArray("parts")
                                    .getJSONObject(0)
                                    .getString("text");
                            textoRespuesta.setText("EXITO CEREBRO CONECTADO:\n\n" + mensaje);
                        } catch (Exception e) {
                            textoRespuesta.setText("Conectado (Codigo " + code + "):\n" + respuestaCruda);
                        }
                    } else {
                        textoRespuesta.setText("Error HTTP " + code + ":\n" + respuestaCruda);
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> textoRespuesta.setText("Error de red: " + e.getMessage()));
            }
        }).start();
    }
}
