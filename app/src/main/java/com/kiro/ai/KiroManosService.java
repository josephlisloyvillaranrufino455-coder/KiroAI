package com.kiro.ai;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.ByteArrayOutputStream;
import java.util.List;

public class KiroManosService extends AccessibilityService {

    public static KiroManosService instancia;
    private int anchoPantalla = 1220;
    private int altoPantalla = 2712;

    public interface CallbackOjos {
        void onImagenLista(String base64Jpeg);
        void onErrorOjos(String motivo);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instancia = this;

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        anchoPantalla = metrics.widthPixels;
        altoPantalla = metrics.heightPixels;

        if (KiroServicioContinuo.instancia != null) {
            KiroServicioContinuo.instancia.moverBolitaFlotante(anchoPantalla / 2, altoPantalla / 2, true);            KiroServicioContinuo.instancia.hablarAvisoDirecto("Ojos y manos conectados.");

        }
    }

    public String leerPantallaInstantaneo() {
        AccessibilityNodeInfo raiz = getRootInActiveWindow();
        if (raiz == null) return "Pantalla sin elementos legibles.";
        StringBuilder sb = new StringBuilder();
        extraerTextoPantalla(raiz, sb, 0);
        String resultado = sb.toString().trim();
        if (resultado.length() > 1500) {
            resultado = resultado.substring(0, 1500);
        }
        return resultado.isEmpty() ? "Pantalla solo con graficos o video." : resultado;
    }

    private void extraerTextoPantalla(AccessibilityNodeInfo nodo, StringBuilder sb, int nivel) {
        if (nodo == null || nivel > 12 || sb.length() > 1600) return;

        CharSequence texto = nodo.getText();
        CharSequence desc = nodo.getContentDescription();

        if (texto != null && texto.toString().trim().length() > 0) {
            sb.append("[").append(texto.toString().trim()).append("] ");
        } else if (desc != null && desc.toString().trim().length() > 0) {
            sb.append("(Icono: ").append(desc.toString().trim()).append(") ");
        }

        for (int i = 0; i < nodo.getChildCount(); i++) {
            extraerTextoPantalla(nodo.getChild(i), sb, nivel + 1);
        }
    }

    public void mirarMiniaturaRapida(CallbackOjos callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback.onErrorOjos("Android incompatible.");
            return;
        }
        if (KiroServicioContinuo.instancia != null) {
            KiroServicioContinuo.instancia.moverBolitaFlotante(anchoPantalla / 2, 180, true);
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult resultado) {
                try {
                    Bitmap hardwareBmp = Bitmap.wrapHardwareBuffer(resultado.getHardwareBuffer(), resultado.getColorSpace());
                    if (hardwareBmp == null) {
                        callback.onErrorOjos("Error leyendo cuadro.");
                        return;
                    }
                    Bitmap softBmp = hardwareBmp.copy(Bitmap.Config.ARGB_8888, false);
                    hardwareBmp.recycle();
                    resultado.getHardwareBuffer().close();

                    int nuevoAncho = 360;
                    int nuevoAlto = (softBmp.getHeight() * nuevoAncho) / softBmp.getWidth();
                    Bitmap reducida = Bitmap.createScaledBitmap(softBmp, nuevoAncho, nuevoAlto, true);
                    softBmp.recycle();

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    reducida.compress(Bitmap.CompressFormat.JPEG, 55, baos);
                    reducida.recycle();

                    String base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                    callback.onImagenLista(base64);
                } catch (Exception e) {
                    callback.onErrorOjos(e.getMessage());
                }
            }

            @Override
            public void onFailure(int errorCode) {
                callback.onErrorOjos("Fallo captura: " + errorCode);
            }
        });
    }
    public void tocarCoordenadasRapido(int x, int y) {
        tocarMultiplesSimultaneo(new int[][]{{x, y}});
    }

    public void tocarMultiplesSimultaneo(int[][] puntos) {
        if (puntos == null || puntos.length == 0) return;
        GestureDescription.Builder builder = new GestureDescription.Builder();
        int limite = Math.min(puntos.length, 5);

        for (int i = 0; i < limite; i++) {
            int px = puntos[i][0];
            int py = puntos[i][1];
            if (i == 0 && KiroServicioContinuo.instancia != null) {
                KiroServicioContinuo.instancia.moverBolitaFlotante(px, py, true);
            }
            Path trazo = new Path();
            trazo.moveTo(px, py);
            GestureDescription.StrokeDescription dedo = new GestureDescription.StrokeDescription(trazo, 0, 15);
            builder.addStroke(dedo);
        }
        dispatchGesture(builder.build(), null, null);
    }

    public void deslizarPantalla(String direccion) {
        int cx = anchoPantalla / 2;
        int cy = altoPantalla / 2;
        int x1 = cx, y1 = cy, x2 = cx, y2 = cy;

        if ("abajo".equals(direccion)) {
            y1 = (int) (altoPantalla * 0.72);
            y2 = (int) (altoPantalla * 0.28);
        } else if ("arriba".equals(direccion)) {
            y1 = (int) (altoPantalla * 0.28);
            y2 = (int) (altoPantalla * 0.72);
        } else if ("derecha".equals(direccion)) {
            x1 = (int) (anchoPantalla * 0.80);
            x2 = (int) (anchoPantalla * 0.20);
        } else if ("izquierda".equals(direccion)) {
            x1 = (int) (anchoPantalla * 0.20);
            x2 = (int) (anchoPantalla * 0.80);
        }

        if (KiroServicioContinuo.instancia != null) {
            KiroServicioContinuo.instancia.moverBolitaFlotante(x1, y1, true);
        }
        Path trazo = new Path();
        trazo.moveTo(x1, y1);
        trazo.lineTo(x2, y2);
        GestureDescription.StrokeDescription desliz = new GestureDescription.StrokeDescription(trazo, 0, 100);
        GestureDescription gesto = new GestureDescription.Builder().addStroke(desliz).build();
        dispatchGesture(gesto, null, null);
    }

    public boolean tocarElementoOIcono(String objetivo) {
        AccessibilityNodeInfo raiz = getRootInActiveWindow();
        if (raiz == null) return false;

        String buscado = objetivo.toLowerCase().trim();
        AccessibilityNodeInfo encontrado = buscarNodoRecursivo(raiz, buscado);
        if (encontrado != null) {
            Rect caja = new Rect();
            encontrado.getBoundsInScreen(caja);
            if (caja.width() > 0 && caja.height() > 0) {
                tocarCoordenadasRapido(caja.centerX(), caja.centerY());
                return true;
            }
            if (encontrado.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
        }
        return false;
    }

    private AccessibilityNodeInfo buscarNodoRecursivo(AccessibilityNodeInfo nodo, String buscado) {
        if (nodo == null) return null;

        CharSequence texto = nodo.getText();
        CharSequence desc = nodo.getContentDescription();
        String viewId = nodo.getViewIdResourceName();

        if (texto != null && texto.toString().toLowerCase().contains(buscado)) return nodo;
        if (desc != null && desc.toString().toLowerCase().contains(buscado)) return nodo;
        if (viewId != null && viewId.toLowerCase().contains(buscado)) return nodo;

        for (int i = 0; i < nodo.getChildCount(); i++) {
            AccessibilityNodeInfo hijo = buscarNodoRecursivo(nodo.getChild(i), buscado);
            if (hijo != null) return hijo;
        }
        return null;
    }

    public boolean abrirAplicacionPorNombre(String nombreApp) {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        String buscada = nombreApp.toLowerCase().trim();

        for (ApplicationInfo app : apps) {
            String etiqueta = pm.getApplicationLabel(app).toString().toLowerCase();
            if (etiqueta.contains(buscada)) {
                Intent lanzamiento = pm.getLaunchIntentForPackage(app.packageName);
                if (lanzamiento != null) {
                    lanzamiento.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(lanzamiento);
                    return true;
                }
            }
        }
        return false;
    }

    public void presionarAtras() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public void irInicio() {
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instancia = null;
        super.onDestroy();
    }
}
