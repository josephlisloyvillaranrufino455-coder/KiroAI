package com.kiro.ai;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView textoEstado;
    private TextView textoGuiaVoz;
    private EditText cajaClave;
    private SharedPreferences memoria;
    private TextToSpeech vozGuia;
    private SpeechRecognizer grabadorHuella;
    private Handler handler = new Handler(Looper.getMainLooper());

    private int pasoHuella = 0;
    private float sumaRms = 0f;
    private float picoMaximoPaso = 0f;
    private final String[] frasesSugeridas = {
        "Hola Kirito, esta es mi voz normal.",
        "Kirito, escucha cuando te hablo suave.",
        "Listo Kirito, recuerda siempre mi voz."
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        memoria = getSharedPreferences("KiroMemoria", MODE_PRIVATE);

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
        titulo.setText("KiritoAI - Control Central");
        titulo.setTextSize(22);

        TextView infoPantalla = new TextView(this);
        infoPantalla.setText("Pantalla: " + ancho + "x" + alto + " px | Densidad: " + densidad);
        infoPantalla.setPadding(0, 10, 0, 20);

        cajaClave = new EditText(this);
        cajaClave.setHint("Pega aqui tu clave Groq (gsk_...)");
        cajaClave.setText(memoria.getString("api_key", ""));

        Button btnGuardarClave = new Button(this);
        btnGuardarClave.setText("1. GUARDAR CLAVE EN MEMORIA");
        btnGuardarClave.setOnClickListener(v -> {
            String clave = cajaClave.getText().toString().trim();
            if (clave.isEmpty()) {
                textoGuiaVoz.setText("Pega tu clave primero.");
                return;
            }
            memoria.edit().putString("api_key", clave).apply();
            textoGuiaVoz.setText("Clave guardada correctamente.");
            hablarGuia("Clave guardada en mi memoria.");
        });

        Button btnGrabarHuella = new Button(this);
        btnGrabarHuella.setText("2. GRABAR MI HUELLA DE VOZ (3 FRASES)");
        btnGrabarHuella.setOnClickListener(v -> {
            pasoHuella = 0;
            sumaRms = 0f;
            grabarSiguienteFrase();
        });

        textoGuiaVoz = new TextView(this);
        float huellaActual = memoria.getFloat("huella_voz_rms", 0f);
        if (huellaActual > 0f) {
            textoGuiaVoz.setText("Huella de voz registrada (Nivel: " + String.format(Locale.US, "%.1f", huellaActual) + ").");
        } else {
            textoGuiaVoz.setText("Aun no has grabado tu huella de voz. Toca el boton 2.");
        }
        textoGuiaVoz.setPadding(0, 15, 0, 25);
        textoGuiaVoz.setTextSize(15);
        
        Button btnEncenderContinuo = new Button(this);
        btnEncenderContinuo.setText("3. ENCENDER KIRITO (MODO CONTINUO)");
        btnEncenderContinuo.setOnClickListener(v -> {
            String clave = cajaClave.getText().toString().trim();
            if (clave.isEmpty()) {
                textoEstado.setText("Error: Guarda tu clave primero.");
                return;
            }
            memoria.edit().putString("api_key", clave).apply();
            iniciarServicioContinuo();
        });

        Button btnApagarTotal = new Button(this);
        btnApagarTotal.setText("APAGAR KIRITO POR COMPLETO");
        btnApagarTotal.setOnClickListener(v -> detenerServicioContinuo());

        textoEstado = new TextView(this);
        textoEstado.setText(KiroServicioContinuo.estaEncendido ? "Estado: ENCENDIDO EN SEGUNDO PLANO" : "Estado: APAGADO");
        textoEstado.setPadding(0, 25, 0, 25);
        textoEstado.setTextSize(16);

        layout.addView(titulo);
        layout.addView(infoPantalla);
        layout.addView(cajaClave);
        layout.addView(btnGuardarClave);
        layout.addView(btnGrabarHuella);
        layout.addView(textoGuiaVoz);
        layout.addView(btnEncenderContinuo);
        layout.addView(btnApagarTotal);
        layout.addView(textoEstado);
        scroll.addView(layout);
        setContentView(scroll);

        vozGuia = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                vozGuia.setLanguage(new Locale("es", "ES"));
                vozGuia.setPitch(0.95f);
                vozGuia.setSpeechRate(1.02f);
            }
        });

        pedirPermisos();
    }

    private void pedirPermisos() {
        ArrayList<String> permisos = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permisos.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permisos.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!permisos.isEmpty()) {
            ActivityCompat.requestPermissions(this, permisos.toArray(new String[0]), 200);
        }
    }

    private void hablarGuia(String texto) {
        if (vozGuia != null) {
            vozGuia.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "GuiaHuella");
        }
    }

    private void grabarSiguienteFrase() {
        if (pasoHuella >= frasesSugeridas.length) {
            float promedio = sumaRms / frasesSugeridas.length;
            memoria.edit().putFloat("huella_voz_rms", promedio).apply();
            textoGuiaVoz.setText("Huella de voz guardada con exito (Nivel: " + String.format(Locale.US, "%.1f", promedio) + ").");
            hablarGuia("Perfecto, ya reconozco tu tono de voz. Ahora puedes encender el modo continuo.");
            return;
        }

        String frase = frasesSugeridas[pasoHuella];
        textoGuiaVoz.setText("Preparando microfono...");

        if (grabadorHuella != null) {
            grabadorHuella.destroy();
            grabadorHuella = null;
        }

        handler.postDelayed(() -> {
            textoGuiaVoz.setText("Frase " + (pasoHuella + 1) + " de 3. Di ahora:\n\"" + frase + "\"");
            picoMaximoPaso = 0f;
            grabadorHuella = SpeechRecognizer.createSpeechRecognizer(this);
            grabadorHuella.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {
                    if (rmsdB > picoMaximoPaso) picoMaximoPaso = rmsdB;
                }
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int error) {
                    textoGuiaVoz.setText("No se escucho la frase " + (pasoHuella + 1) + ". Toca el boton 2 para reintentar.");
                }
                @Override public void onResults(Bundle results) {
                    sumaRms += (picoMaximoPaso > 0f ? picoMaximoPaso : 5.0f);
                    pasoHuella++;
                    grabarSiguienteFrase();
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE");
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L);
            grabadorHuella.startListening(intent);
        }, 800);
    }

    private void iniciarServicioContinuo() {
        Intent servicio = new Intent(this, KiroServicioContinuo.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(servicio);
        } else {
            startService(servicio);
        }
        textoEstado.setText("Estado: ENCENDIDO (Escuchando continuo hasta que lo apagues)");
    }

    private void detenerServicioContinuo() {
        Intent servicio = new Intent(this, KiroServicioContinuo.class);
        servicio.setAction("APAGAR_KIRO");
        startService(servicio);
        textoEstado.setText("Estado: APAGADO TOTAL");
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (vozGuia != null) vozGuia.shutdown();
        if (grabadorHuella != null) grabadorHuella.destroy();
        super.onDestroy();
    }
}
