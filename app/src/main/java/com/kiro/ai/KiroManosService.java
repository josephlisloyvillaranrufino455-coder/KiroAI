package com.kiro.ai;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class KiroManosService extends AccessibilityService {

    public static KiroManosService instancia;
    private WindowManager windowManager;
    private View cuerpoFlotante;
    private WindowManager.LayoutParams paramsFlotante;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int anchoPantalla = 1220;
    private int altoPantalla = 2712;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instancia = this;

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        anchoPantalla = metrics.widthPixels;
        altoPantalla = metrics.heightPixels;

        crearCuerpoFlotante();
    }

    private void crearCuerpoFlotante() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return;
        }
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            cuerpoFlotante = new View(this);

            GradientDrawable forma = new GradientDrawable();
            forma.setShape(GradientDrawable.OVAL);
            forma.setColor(Color.parseColor("#CC00E5FF"));
            forma.setStroke(4, Color.WHITE);
            cuerpoFlotante.setBackground(forma);

            int tipoVentana = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            paramsFlotante = new WindowManager.LayoutParams(
                    46, 46,
                    tipoVentana,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            paramsFlotante.gravity = Gravity.TOP | Gravity.START;
            paramsFlotante.x = anchoPantalla - 90;
            paramsFlotante.y = 220;

            windowManager.addView(cuerpoFlotante, paramsFlotante);
        } catch (Exception ignored) {}
    }

    public void moverCuerpoFlotante(int x, int y, boolean accionActiva) {
        handler.post(() -> {
            if (cuerpoFlotante == null || windowManager == null || paramsFlotante == null) return;
            try {
                GradientDrawable forma = (GradientDrawable) cuerpoFlotante.getBackground();
                forma.setColor(Color.parseColor(accionActiva ? "#EE00FF88" : "#CC00E5FF"));
                paramsFlotante.x = Math.max(0, x - 23);
                paramsFlotante.y = Math.max(0, y - 23);
                windowManager.updateViewLayout(cuerpoFlotante, paramsFlotante);

                if (accionActiva) {
                    handler.postDelayed(() -> moverCuerpoFlotante(anchoPantalla - 90, 220, false), 900);
                }
            } catch (Exception ignored) {}
        });
    }
    public void tocarCoordenadasRapido(int x, int y) {
        moverCuerpoFlotante(x, y, true);
        Path trazo = new Path();
        trazo.moveTo(x, y);
        GestureDescription.StrokeDescription toque = new GestureDescription.StrokeDescription(trazo, 0, 35);
        GestureDescription gesto = new GestureDescription.Builder().addStroke(toque).build();
        dispatchGesture(gesto, null, null);
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

        moverCuerpoFlotante(x1, y1, true);
        Path trazo = new Path();
        trazo.moveTo(x1, y1);
        trazo.lineTo(x2, y2);
        GestureDescription.StrokeDescription desliz = new GestureDescription.StrokeDescription(trazo, 0, 140);
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
        if (cuerpoFlotante != null && windowManager != null) {
            try {
                windowManager.removeView(cuerpoFlotante);
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
