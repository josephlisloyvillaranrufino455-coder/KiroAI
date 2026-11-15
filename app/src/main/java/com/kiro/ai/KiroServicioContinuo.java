package com.kiro.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class KiroServicioContinuo extends Service {

    public static boolean estaEncendido = false;
    private static final String CANAL_ID = "KiroCanalContinuo";
    private SpeechRecognizer oido;
    private TextToSpeech voz;
    private AudioManager audioManager;
    private SharedPreferences memoria;
    private Handler handler = new Handler(Looper.getMainLooper());
    private float picoVozActual = 0f;

    private String accionPendiente = "";
    private String parametroPendiente = "";

    @Override
    public void onCreate() {
        super.onCreate();
        estaEncendido = true;
        memoria = getSharedPreferences("KiroMemoria", MODE_PRIVATE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        crearCanalNotificacion();
        Notification notificacion = construirNotificacion("Kirito esta despierto y escuchando tu voz...");
        startForeground(1, notificacion);

        voz = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                voz.setLanguage(new Locale("es", "ES"));
                voz.setPitch(0.95f);
                voz.setSpeechRate(1.02f);
                voz.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {}
                    @Override public void onDone(String utteranceId) {
                        if (estaEncendido) {
                            handler.postDelayed(() -> iniciarCicloEscucha(), 600);
                        }
                    }
                    @Override public void onError(String utteranceId) {
                        if (estaEncendido) {
                            handler.postDelayed(() -> iniciarCicloEscucha(), 600);
                        }
                    }
                });
                hablar("Estoy despierto con mis manos y mi cuerpo listos. Dime que necesitas.");
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "APAGAR_KIRO".equals(intent.getAction())) {
            apagarTotalmente();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void iniciarCicloEscucha() {
        if (!estaEncendido) return;
        if (voz != null && voz.isSpeaking()) return;

        try {
            if (audioManager != null) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            }
            if (oido != null) {
                oido.destroy();
            }

            picoVozActual = 0f;
            oido = SpeechRecognizer.createSpeechRecognizer(this);
            oido.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rmsdB) {
                    if (rmsdB > picoVozActual) {
                        picoVozActual = rmsdB;
                    }
                }

                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}

                @Override
                public void onError(int error) {
                    if (estaEncendido) {
                        handler.postDelayed(() -> iniciarCicloEscucha(), 1500);
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    if (!estaEncendido) return;
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String texto = matches.get(0).trim();
                        procesarEntradaVoz(texto);
                    } else {
                        handler.postDelayed(() -> iniciarCicloEscucha(), 800);
                    }
                }

                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L);
            oido.startListening(intent);

        } catch (Exception e) {
            if (estaEncendido) {
                handler.postDelayed(() -> iniciarCicloEscucha(), 1500);
            }
        }
    }
    private void procesarEntradaVoz(String texto) {
        String limpio = texto.toLowerCase().trim();

        if (limpio.contains("apágate") || limpio.contains("apagate") || limpio.contains("kirito duerme")) {
            hablarYApagar("De acuerdo, me apago por completo. Aqui estare cuando me necesites.");
            return;
        }

        if (!accionPendiente.isEmpty()) {
            if (limpio.equals("sí") || limpio.equals("si") || limpio.contains("hazlo") || limpio.contains("adelante") || limpio.contains("claro") || limpio.contains("por favor")) {
                String acc = accionPendiente;
                String param = parametroPendiente;
                accionPendiente = "";
                parametroPendiente = "";
                ejecutarManoConfirmada(acc, param);
                return;
            } else if (limpio.equals("no") || limpio.contains("cancela") || limpio.contains("no lo hagas")) {
                accionPendiente = "";
                parametroPendiente = "";
                hablar("Entendido, no lo hare. ¿Quieres que te pregunte mas tarde o pasamos a otra cosa?");
                return;
            }
        }

        if (audioManager != null && audioManager.isMusicActive()) {
            float huellaUsuario = memoria.getFloat("huella_voz_rms", 5.0f);
            if (picoVozActual < (huellaUsuario - 2.5f)) {
                handler.postDelayed(() -> iniciarCicloEscucha(), 800);
                return;
            }
        }

        String apiKey = memoria.getString("api_key", "");
        if (apiKey.isEmpty()) {
            hablar("No encuentro tu clave guardada en la memoria.");
            return;
        }

        actualizarNotificacion("Pensando: " + texto);
        consultarCerebro(apiKey, texto);
    }

    private void ejecutarManoConfirmada(String accion, String param) {
        if (KiroManosService.instancia == null) {
            hablar("Aun no has activado mi permiso de manos en Accesibilidad.");
            return;
        }
        if ("ABRIR".equals(accion)) {
            boolean ok = KiroManosService.instancia.abrirAplicacionPorNombre(param);
            hablar(ok ? "Abriendo " + param + "." : "No encontre la aplicacion " + param + ".");
        } else if ("TOCAR".equals(accion)) {
            boolean ok = KiroManosService.instancia.tocarElementoOIcono(param);
            hablar(ok ? "Listo." : "No veo " + param + " en esta pantalla.");
        } else if ("DESLIZAR".equals(accion)) {
            KiroManosService.instancia.deslizarPantalla(param);
            hablar("Deslizando hacia " + param + ".");
        } else if ("ATRAS".equals(accion)) {
            KiroManosService.instancia.presionarAtras();
            hablar("Volviendo atras.");
        } else if ("INICIO".equals(accion)) {
            KiroManosService.instancia.irInicio();
            hablar("Yendo al inicio.");
        }
    }

    private void consultarCerebro(String apiKey, String mensajeUsuarioTexto) {
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
                sistema.put("content", "Eres Kirito, un asistente personal educado, culto y humano. Siempre pide permiso antes de tocar la pantalla o abrir una app. Si el usuario pide hacer una accion en el telefono, responde pidiendo permiso en una frase corta y al final agrega exactamente una etiqueta oculta: [ACCION:ABRIR:nombre], [ACCION:TOCAR:texto_o_icono], [ACCION:DESLIZAR:abajo/arriba/izquierda/derecha], [ACCION:ATRAS:nada] o [ACCION:INICIO:nada].");

                JSONObject mensajeUsuario = new JSONObject();
                mensajeUsuario.put("role", "user");
                mensajeUsuario.put("content", mensajeUsuarioTexto);

                JSONArray mensajes = new JSONArray();
                mensajes.put(sistema);
                mensajes.put(mensajeUsuario);

                JSONObject cuerpo = new JSONObject();
                cuerpo.put("model", "openai/gpt-oss-120b");
                cuerpo.put("messages", mensajes);

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
                handler.post(() -> {
                    if (!estaEncendido) return;
                    if (code >= 200 && code < 300) {
                        try {
                            JSONObject json = new JSONObject(respuestaCruda);
                            String mensaje = json.getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("message")
                                    .getString("content");

                            if (mensaje.contains("[ACCION:")) {
                                int inicio = mensaje.indexOf("[ACCION:");
                                int fin = mensaje.indexOf("]", inicio);
                                if (fin > inicio) {
                                    String etiqueta = mensaje.substring(inicio + 8, fin);
                                    String[] partes = etiqueta.split(":", 2);
                                    if (partes.length == 2) {
                                        accionPendiente = partes[0].trim();
                                        parametroPendiente = partes[1].trim();
                                    }
                                    mensaje = mensaje.substring(0, inicio).trim();
                                }
                            }

                            actualizarNotificacion("Kirito activo");
                            hablar(mensaje);
                        } catch (Exception e) {
                            iniciarCicloEscucha();
                        }
                    } else {
                        iniciarCicloEscucha();
                    }
                });

            } catch (Exception e) {
                handler.post(() -> {
                    if (estaEncendido) iniciarCicloEscucha();
                });
            }
        }).start();
    }

    private void hablar(String texto) {
        if (!estaEncendido || voz == null) return;
        voz.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "KiroContinuo");
    }

    private void hablarYApagar(String texto) {
        estaEncendido = false;
        if (oido != null) oido.cancel();
        if (voz != null) {
            voz.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {}
                @Override public void onDone(String utteranceId) { apagarTotalmente(); }
                @Override public void onError(String utteranceId) { apagarTotalmente(); }
            });
            voz.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "KiroAdios");
        } else {
            apagarTotalmente();
        }
    }

    public void apagarTotalmente() {
        estaEncendido = false;
        handler.removeCallbacksAndMessages(null);
        if (oido != null) {
            oido.destroy();
            oido = null;
        }
        if (voz != null) {
            voz.stop();
            voz.shutdown();
            voz = null;
        }
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_NORMAL);
        }
        stopForeground(true);
        stopSelf();
    }

    private void crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CANAL_ID,
                    "Kirito Servicio Continuo",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(canal);
        }
    }

    private Notification construirNotificacion(String texto) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CANAL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder.setContentTitle("KiritoAI Activo")
                .setContentText(texto)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build();
    }

    private void actualizarNotificacion(String texto) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null && estaEncendido) {
            manager.notify(1, construirNotificacion(texto));
        }
    }

    @Override
    public void onDestroy() {
        estaEncendido = false;
        if (oido != null) oido.destroy();
        if (voz != null) voz.shutdown();
        if (audioManager != null) audioManager.setMode(AudioManager.MODE_NORMAL);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
