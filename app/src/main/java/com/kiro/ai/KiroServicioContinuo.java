package com.kiro.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class KiroServicioContinuo extends Service {

    public static KiroServicioContinuo instancia;
    public static boolean estaEncendido = false;
    private static final String CANAL_ID = "KiroCanalContinuo";
    private SpeechRecognizer oido;
    private TextToSpeech voz;
    private AudioManager audioManager;
    private SharedPreferences memoria;
    private Handler handler = new Handler(Looper.getMainLooper());
    private float picoVozActual = 0f;

    private WindowManager windowManager;
    private List<TextView> listaBolitas = new ArrayList<>();
    private List<WindowManager.LayoutParams> listaParams = new ArrayList<>();
    private int anchoPantalla = 1220;
    private int altoPantalla = 2712;
    private int tamanoBolitaPx = 90;
    private boolean bolitasFijas = false;

    private String accionPendiente = "";
    private String parametroPendiente = "";

    @Override
    public void onCreate() {
        super.onCreate();
        instancia = this;
        estaEncendido = true;
        memoria = getSharedPreferences("KiroMemoria", MODE_PRIVATE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        anchoPantalla = metrics.widthPixels;
        altoPantalla = metrics.heightPixels;
        tamanoBolitaPx = Math.round(28 * metrics.density);

        crearCanalNotificacion();
        startForeground(1, construirNotificacion("Kirito activo con ojos y manos..."));
        crearBolitasEnPantalla(1);

        voz = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                voz.setLanguage(new Locale("es", "ES"));
                voz.setPitch(0.95f);
                voz.setSpeechRate(1.02f);
                voz.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {}
                    @Override public void onDone(String utteranceId) {
                        if (estaEncendido) handler.postDelayed(() -> iniciarCicloEscucha(), 500);
                    }
                    @Override public void onError(String utteranceId) {
                        if (estaEncendido) handler.postDelayed(() -> iniciarCicloEscucha(), 500);
                    }
                });
                hablar("Estoy despierto. Soy tus manos y tus ojos. Dime que necesitas.");
            }
        });
    }

    private void limpiarBolitas() {
        if (windowManager != null) {
            for (TextView b : listaBolitas) {
                try { windowManager.removeView(b); } catch (Exception ignored) {}
            }
        }
        listaBolitas.clear();
        listaParams.clear();
    }
    private void crearBolitasEnPantalla(int cantidad) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return;
        handler.post(() -> {
            limpiarBolitas();
            bolitasFijas = false;
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            int tipoVentana = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            int total = Math.max(1, Math.min(cantidad, 5));
            for (int i = 0; i < total; i++) {
                TextView bolita = new TextView(this);
                bolita.setText(total > 1 ? String.valueOf(i + 1) : "");
                bolita.setTextColor(Color.WHITE);
                bolita.setTextSize(11);
                bolita.setGravity(Gravity.CENTER);

                GradientDrawable forma = new GradientDrawable();
                forma.setShape(GradientDrawable.OVAL);
                forma.setColor(Color.parseColor("#CC00E5FF"));
                forma.setStroke(3, Color.WHITE);
                bolita.setBackground(forma);

                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        tamanoBolitaPx, tamanoBolitaPx,
                        tipoVentana,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                );
                params.gravity = Gravity.TOP | Gravity.START;
                params.x = (anchoPantalla / 2) - (tamanoBolitaPx / 2) + (i * (tamanoBolitaPx + 20)) - ((total - 1) * 45);
                params.y = (altoPantalla / 2) - (tamanoBolitaPx / 2);

                bolita.setOnTouchListener(new View.OnTouchListener() {
                    private int iniX, iniY;
                    private float toqueX, toqueY;
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (bolitasFijas) return false;
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                iniX = params.x;
                                iniY = params.y;
                                toqueX = event.getRawX();
                                toqueY = event.getRawY();
                                return true;
                            case MotionEvent.ACTION_MOVE:
                                params.x = iniX + (int) (event.getRawX() - toqueX);
                                params.y = iniY + (int) (event.getRawY() - toqueY);
                                windowManager.updateViewLayout(bolita, params);
                                return true;
                        }
                        return false;
                    }
                });

                try {
                    windowManager.addView(bolita, params);
                    listaBolitas.add(bolita);
                    listaParams.add(params);
                } catch (Exception ignored) {}
            }
        });
    }

    private void fijarBolitasTransparentes(boolean fijar) {
        handler.post(() -> {
            bolitasFijas = fijar;
            if (windowManager == null) return;
            for (int i = 0; i < listaBolitas.size(); i++) {
                TextView bolita = listaBolitas.get(i);
                WindowManager.LayoutParams params = listaParams.get(i);
                if (fijar) {
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
                    bolita.setAlpha(0.45f);
                } else {
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
                    bolita.setAlpha(1.0f);
                }
                try { windowManager.updateViewLayout(bolita, params); } catch (Exception ignored) {}
            }
        });
    }

    public void moverBolitaFlotante(int x, int y, boolean accionActiva) {
        handler.post(() -> {
            if (listaBolitas.isEmpty() || windowManager == null) return;
            try {
                TextView b = listaBolitas.get(0);
                WindowManager.LayoutParams p = listaParams.get(0);
                GradientDrawable forma = (GradientDrawable) b.getBackground();
                forma.setColor(Color.parseColor(accionActiva ? "#EE00FF88" : "#CC00E5FF"));
                if (accionActiva && listaBolitas.size() == 1) {
                    p.x = Math.max(0, x - (tamanoBolitaPx / 2));
                    p.y = Math.max(0, y - (tamanoBolitaPx / 2));
                }
                windowManager.updateViewLayout(b, p);
                if (accionActiva) {
                    handler.postDelayed(() -> moverBolitaFlotante(x, y, false), 500);
                }
            } catch (Exception ignored) {}
        });
    }

        private void dispararBolitasAhora() {
        if (KiroManosService.instancia == null || listaParams.isEmpty()) return;
        for (int i = 0; i < listaParams.size(); i++) {
            WindowManager.LayoutParams p = listaParams.get(i);
            int cx = p.x + (tamanoBolitaPx / 2);
            int cy = p.y + (tamanoBolitaPx / 2);
            KiroManosService.instancia.tocarCoordenadasRapido(cx, cy);
        }
    }


    private void guardarPerfilBolitas(String nombrePerfil) {
        if (listaParams.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < listaParams.size(); i++) {
            if (i > 0) sb.append(";");
            sb.append(listaParams.get(i).x).append(",").append(listaParams.get(i).y);
        }
        memoria.edit().putString("perfil_bolitas_" + nombrePerfil, sb.toString()).apply();
    }

    private boolean cargarPerfilBolitas(String nombrePerfil) {
        String guardado = memoria.getString("perfil_bolitas_" + nombrePerfil, "");
        if (guardado.isEmpty()) return false;
        List<String> pares = Arrays.asList(guardado.split(";"));
        crearBolitasEnPantalla(pares.size());
        handler.postDelayed(() -> {
            for (int i = 0; i < pares.size() && i < listaParams.size(); i++) {
                List<String> xy = Arrays.asList(pares.get(i).split(","));
                if (xy.size() == 2) {
                    listaParams.get(i).x = Integer.parseInt(xy.get(0));
                    listaParams.get(i).y = Integer.parseInt(xy.get(1));
                    try { windowManager.updateViewLayout(listaBolitas.get(i), listaParams.get(i)); } catch (Exception ignored) {}
                }
            }
            fijarBolitasTransparentes(true);
        }, 150);
        return true;
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
            if (audioManager != null) audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            if (oido != null) oido.destroy();

            picoVozActual = 0f;
            oido = SpeechRecognizer.createSpeechRecognizer(this);
            oido.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {
                    if (rmsdB > picoVozActual) picoVozActual = rmsdB;
                }
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}

                @Override
                public void onError(int error) {
                    if (estaEncendido) handler.postDelayed(() -> iniciarCicloEscucha(), 1200);
                }

                @Override
                public void onResults(Bundle results) {
                    if (!estaEncendido) return;
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        procesarEntradaVoz(matches.get(0).trim());
                    } else {
                        handler.postDelayed(() -> iniciarCicloEscucha(), 600);
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
            if (estaEncendido) handler.postDelayed(() -> iniciarCicloEscucha(), 1200);
        }
    }

    private void procesarEntradaVoz(String texto) {
        String limpio = texto.toLowerCase().trim();

        if (limpio.contains("apágate") || limpio.contains("apagate") || limpio.contains("kirito duerme")) {
            hablarYApagar("De acuerdo, me apago por completo. Aqui estare cuando me necesites.");
            return;
        }

        if (limpio.equals("presiona") || limpio.equals("toca") || limpio.equals("toca ahí") || limpio.equals("pared") || limpio.equals("ya")) {
            dispararBolitasAhora();
            handler.postDelayed(() -> iniciarCicloEscucha(), 150);
            return;
        }

        if (limpio.contains("multibolitas") || limpio.contains("multi bolitas")) {
            int cant = limpio.contains("4") || limpio.contains("cuatro") ? 4 : (limpio.contains("5") || limpio.contains("cinco") ? 5 : 3);
            crearBolitasEnPantalla(cant);
            hablar("Listo, puse " + cant + " bolitas en pantalla. Arrastralas y dime listo.");
            return;
        }

        if (limpio.equals("listo") || limpio.contains("fijar bolitas") || limpio.contains("fija las bolitas")) {
            fijarBolitasTransparentes(true);
            hablar("Bolitas fijadas y transparentes al tacto.");
            return;
        }

        if (limpio.contains("mover bolitas") || limpio.contains("desbloquear bolitas")) {
            fijarBolitasTransparentes(false);
            hablar("Ya puedes mover las bolitas.");
            return;
        }

        if (limpio.startsWith("guarda esto como ")) {
            String nombre = limpio.replace("guarda esto como ", "").trim();
            guardarPerfilBolitas(nombre);
            hablar("Guardado como " + nombre + " en mi memoria.");
            return;
        }

        if (limpio.startsWith("activa ")) {
            String nombre = limpio.replace("activa ", "").trim();
            if (cargarPerfilBolitas(nombre)) {
                hablar("Posicion " + nombre + " cargada.");
                return;
            }
        }
        if (!accionPendiente.isEmpty()) {
            if (limpio.contains("sí") || limpio.contains("si") || limpio.contains("hazlo") || limpio.contains("adelante") || limpio.contains("claro") || limpio.contains("abre") || limpio.contains("desliza")) {
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

        if (limpio.contains("desliza")) {
            String dir = limpio.contains("arriba") ? "arriba" : (limpio.contains("derecha") ? "derecha" : (limpio.contains("izquierda") ? "izquierda" : "abajo"));
            accionPendiente = "DESLIZAR";
            parametroPendiente = dir;
            hablar("¿Me das permiso para deslizar hacia " + dir + "?");
            return;
        }

        if (limpio.startsWith("abre ") || limpio.contains("abrir ")) {
            String app = limpio.replace("puedes abrir ", "").replace("abre ", "").replace("abrir ", "").trim();
            if (!app.isEmpty()) {
                accionPendiente = "ABRIR";
                parametroPendiente = app;
                hablar("¿Quieres que abra " + app + " ahora?");
                return;
            }
        }

        if (limpio.contains("vuelve atrás") || limpio.contains("regresa") || limpio.equals("atrás")) {
            accionPendiente = "ATRAS";
            parametroPendiente = "nada";
            hablar("¿Me das permiso para volver atras?");
            return;
        }

        if (limpio.contains("ve al inicio") || limpio.contains("pantalla principal")) {
            accionPendiente = "INICIO";
            parametroPendiente = "nada";
            hablar("¿Me das permiso para ir al inicio?");
            return;
        }

        if (audioManager != null && audioManager.isMusicActive()) {
            float huellaUsuario = memoria.getFloat("huella_voz_rms", 5.0f);
            if (picoVozActual < (huellaUsuario - 2.5f)) {
                handler.postDelayed(() -> iniciarCicloEscucha(), 600);
                return;
            }
        }

        String apiKey = memoria.getString("api_key", "");
        if (apiKey.isEmpty()) {
            hablar("No encuentro tu clave guardada en la memoria.");
            return;
        }

        if (limpio.contains("qué ves") || limpio.contains("que ves") || limpio.contains("mira") || limpio.contains("pantalla") || limpio.contains("ves")) {
            mirarYResponder(apiKey, texto);
            return;
        }

        String lecturaRapida = (KiroManosService.instancia != null) ? KiroManosService.instancia.leerPantallaInstantaneo() : "";
        consultarCerebro(apiKey, texto, lecturaRapida, null);
    }

    private void mirarYResponder(String apiKey, String preguntaUsuario) {
        if (KiroManosService.instancia == null) {
            hablar("Activa KiroAI en Accesibilidad para abrir mis ojos. Te llevo a los ajustes ahora.");
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        }
        String lecturaRapida = KiroManosService.instancia.leerPantallaInstantaneo();
        KiroManosService.instancia.mirarMiniaturaRapida(new KiroManosService.CallbackOjos() {
            @Override
            public void onImagenLista(String base64Jpeg) {
                consultarCerebro(apiKey, preguntaUsuario, lecturaRapida, base64Jpeg);
            }
            @Override
            public void onErrorOjos(String motivo) {
                consultarCerebro(apiKey, preguntaUsuario, lecturaRapida, null);
            }
        });
    }

    private void ejecutarManoConfirmada(String accion, String param) {
        if (KiroManosService.instancia == null) {
            hablar("Activa KiroAI en Accesibilidad para usar mis manos. Te abro los ajustes ahora.");
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
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
    private void consultarCerebro(String apiKey, String mensajeUsuarioTexto, String lecturaPantalla, String imagenBase64) {
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

                String recuerdos = memoria.getString("cuaderno_recuerdos", "Ninguno todavia.");

                JSONObject sistema = new JSONObject();
                sistema.put("role", "system");
                sistema.put("content", "Eres Kirito, el asistente integrado dentro de este celular Android. Eres las manos y los ojos del usuario: puedes ver la pantalla, presionar botones o iconos, deslizar en cualquier direccion y abrir aplicaciones. JAMAS digas que solo puedes responder mensajes. Tus recuerdos guardados son: [" + recuerdos + "]. Lo que ves ahora mismo en pantalla es: [" + lecturaPantalla + "]. Si el usuario te ensena algo importante o menciona una fecha clave, agrega al final [MEMORIA:dato_a_recordar]. Cuando el usuario pida tocar un boton en pantalla, pide permiso en una frase corta y agrega obligatoriamente al final: [ACCION:TOCAR:boton].");

                JSONObject mensajeUsuario = new JSONObject();
                mensajeUsuario.put("role", "user");

                if (imagenBase64 != null) {
                    JSONArray contenidoVisual = new JSONArray();
                    JSONObject parteTexto = new JSONObject();
                    parteTexto.put("type", "text");
                    parteTexto.put("text", mensajeUsuarioTexto);

                    JSONObject parteImagen = new JSONObject();
                    parteImagen.put("type", "image_url");
                    JSONObject objUrl = new JSONObject();
                    objUrl.put("url", "data:image/jpeg;base64," + imagenBase64);
                    parteImagen.put("image_url", objUrl);

                    contenidoVisual.put(parteTexto);
                    contenidoVisual.put(parteImagen);
                    mensajeUsuario.put("content", contenidoVisual);
                } else {
                    mensajeUsuario.put("content", mensajeUsuarioTexto);
                }

                JSONArray mensajes = new JSONArray();
                mensajes.put(sistema);
                mensajes.put(mensajeUsuario);

                JSONObject cuerpo = new JSONObject();
                cuerpo.put("model", imagenBase64 != null ? "meta-llama/llama-4-scout-17b-16e-instruct" : "openai/gpt-oss-120b");
                cuerpo.put("messages", mensajes);

                OutputStream os = conn.getOutputStream();
                os.write(cuerpo.toString().getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(), "UTF-8"));

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
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

                            if (mensaje.contains("[MEMORIA:")) {
                                int iniM = mensaje.indexOf("[MEMORIA:");
                                int finM = mensaje.indexOf("]", iniM);
                                if (finM > iniM) {
                                    String nuevoDato = mensaje.substring(iniM + 9, finM).trim();
                                    String prev = memoria.getString("cuaderno_recuerdos", "");
                                    String actualizado = prev.isEmpty() ? nuevoDato : prev + " | " + nuevoDato;
                                    memoria.edit().putString("cuaderno_recuerdos", actualizado).apply();
                                    mensaje = (mensaje.substring(0, iniM) + mensaje.substring(finM + 1)).trim();
                                }
                            }

                            if (mensaje.contains("[ACCION:")) {
                                int inicio = mensaje.indexOf("[ACCION:");
                                int fin = mensaje.indexOf("]", inicio);
                                if (fin > inicio) {
                                    String etiqueta = mensaje.substring(inicio + 8, fin);
                                    List<String> partes = Arrays.asList(etiqueta.split(":", 2));
                                    if (partes.size() == 2) {
                                        accionPendiente = partes.get(0).trim();
                                        parametroPendiente = partes.get(1).trim();
                                    }
                                    mensaje = mensaje.substring(0, inicio).trim();
                                }
                            }

                            hablar(mensaje);
                        } catch (Exception e) {
                            iniciarCicloEscucha();
                        }
                    } else {
                        iniciarCicloEscucha();
                    }
                });
            } catch (Exception e) {
                handler.post(() -> { if (estaEncendido) iniciarCicloEscucha(); });
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
        instancia = null;
        handler.removeCallbacksAndMessages(null);
        limpiarBolitas();
        if (oido != null) { oido.destroy(); oido = null; }
        if (voz != null) { voz.stop(); voz.shutdown(); voz = null; }
        if (audioManager != null) audioManager.setMode(AudioManager.MODE_NORMAL);
        stopForeground(true);
        stopSelf();
    }

    private void crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(CANAL_ID, "Kirito Servicio Continuo", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(canal);
        }
    }

    private Notification construirNotificacion(String texto) {
        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CANAL_ID) : new Notification.Builder(this);
        return builder.setContentTitle("KiritoAI Activo").setContentText(texto)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build();
    }

    @Override
    public void onDestroy() {
        apagarTotalmente();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
