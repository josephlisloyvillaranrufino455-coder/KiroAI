package com.kiro.ai;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.DisplayMetrics;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private boolean kiroActivo = true;
    private TextView textoEstado;
    private TextView textoRespuesta;
    private EditText cajaClave;
    private SharedPreferences memoria;
    private TextToSpeech vozKiro;
    private SpeechRecognizer oidoKiro;
    private AudioManager audioManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        memoria = getSharedPreferences("KiroMemoria", MODE_PRIVATE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

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
                hablar("Sistema encendido y listo para ayudarte.");
            } else {
                btnApagado.setText("ENCENDER KIROAI");
                textoEstado.setText("Estado: APAGADO TOTAL");
                textoRespuesta.setText("KiroAI detenido por seguridad.");
                if (vozKiro != null) vozKiro.stop();
                if (oidoKiro != null) oidoKiro.cancel();
            }
        });

        textoEstado = new TextView(this);
        textoEstado.setText("Estado: ENCENDIDO");
        textoEstado.setPadding(0, 15, 0, 25);

        cajaClave = new EditText(this);
        cajaClave.setHint("Pega aqui tu clave Groq (gsk_...)");
        String claveGuardada = memoria.getString("api_key", "");
        if (!claveGuardada.isEmpty()) {
            cajaClave.setText(claveGuardada);
        }

        Button btnGuardar = new Button(this);
        btnGuardar.setText("GUARDAR CLAVE Y PROBAR VOZ");
        btnGuardar.setOnClickListener(v -> {
            String clave = cajaClave.getText().toString().trim();
            if (clave.isEmpty()) {
                textoRespuesta.setText("Pega tu clave API primero.");
                return;
            }
            memoria.edit().putString("api_key", clave).apply();
            textoRespuesta.setText("Clave guardada en memoria. Probando cerebro y voz...");
            enviarAlCerebro(clave, "Hola Kiro, presentate brevemente de forma educada y natural.");
        });

        Button btnHablar = new Button(this);
        btnHablar.setText("HABLAR CON KIROAI (MICROFONO)");
        btnHablar.setOnClickListener(v -> {
            if (!kiroActivo) {
                textoRespuesta.setText("Enciende KiroAI primero.");
                return;
            }
            String clave = cajaClave.getText().toString().trim();
            if (clave.isEmpty()) {
                textoRespuesta.setText("Guarda tu clave API primero.");
                return;
            }
            memoria.edit().putString("api_key", clave).apply();
            iniciarEscuchaLimpia(clave);
        });

        textoRespuesta = new TextView(this);
        textoRespuesta.setText("Esperando orden...");
        textoRespuesta.setPadding(0, 30, 0, 30);
        textoRespuesta.setTextSize(16);

        layout.addView(titulo);
        layout.addView(infoPantalla);
        layout.addView(btnApagado);
        layout.addView(textoEstado);
        layout.addView(cajaClave);
        layout.addView(btnGuardar);
        layout.addView(btnHablar);
        layout.addView(textoRespuesta);
        scroll.addView(layout);
        setContentView(scroll);

        inicializarVoz();
        pedirPermisoMicrofono();
    }

    private void pedirPermisoMicrofono() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 101);
        }
    }

    private void inicializarVoz() {
        vozKiro = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                vozKiro.setLanguage(new Locale("es", "ES"));
                vozKiro.setPitch(0.95f);
                vozKiro.setSpeechRate(1.02f);
            }
        });
    }

    private void hablar(String texto) {
        if (!kiroActivo || vozKiro == null) return;
        vozKiro.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "KiroVoz");
    }

    private void iniciarEscuchaLimpia(String apiKey) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pedirPermisoMicrofono();
            return;
        }

        if (vozKiro != null && vozKiro.isSpeaking()) {
            vozKiro.stop();
        }

        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        }

        if (oidoKiro != null) {
            oidoKiro.destroy();
        }

        oidoKiro = SpeechRecognizer.createSpeechRecognizer(this);
        oidoKiro.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { textoRespuesta.setText("Escuchando tu voz... Habla ahora."); }
            @Override public void onBeginningOfSpeech() { textoRespuesta.setText("Te escucho..."); }
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { textoRespuesta.setText("Procesando lo que dijiste..."); }

            @Override
            public void onError(int error) {
                if (audioManager != null) audioManager.setMode(AudioManager.MODE_NORMAL);
                textoRespuesta.setText("No pude escucharte bien (Codigo " + error + "). Toca el boton e intentalo de nuevo.");
            }

            @Override
            public void onResults(Bundle results) {
                if (audioManager != null) audioManager.setMode(AudioManager.MODE_NORMAL);
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String loQueDijiste = matches.get(0);
                    textoRespuesta.setText("Tu dijiste: \"" + loQueDijiste + "\"\n\nPensando respuesta...");
                    enviarAlCerebro(apiKey, loQueDijiste);
                }
            }

            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        oidoKiro.startListening(intent);
    }

    private void enviarAlCerebro(String apiKey, String mensajeUsuarioTexto) {
        new Thread(() -> {
            try {
                String host = "https://" + "api.groq" + ".com";
                String ruta = "/openai/v1/chat/completions";
                URL url = new URL(host + ruta);

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);

                JSONObject sistema = new JSONObject();
                sistema.put("role", "system");
                sistema.put("content", "Eres KiroAI, un asistente personal educado, culto, humano y respetuoso. Responde de forma natural, clara y breve, pidiendo permiso antes de hacer cualquier accion.");

                JSONObject mensajeUsuario = new JSONObject();
                mensajeUsuario.put("role", "user");
                mensajeUsuario.put("content", mensajeUsuarioTexto);

                JSONArray mensajes = new JSONArray();
                mensajes.put(sistema);
