package com.hook.satelliteicon;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * TelephonyHook
 *
 * Engaña al stack de telefonía para que reporte el satélite como activo.
 * Corre en el proceso "android" (system_server).
 *
 * Clases objetivo:
 *   - android.telephony.satellite.SatelliteManager         → API pública
 *   - com.android.internal.telephony.satellite.SatelliteController → implementación interna
 *
 * NOTA: Si después de compilar algún hook no se aplica, revisá el logcat con:
 *   adb logcat -s "SatelliteIconHook" "LSPosed"
 * y ajustá los nombres de método según lo que reporta.
 */
public class TelephonyHook {

    private static final String TAG = "SatelliteIconHook/Telephony";

    public static void init(XC_LoadPackage.LoadPackageParam lpparam) {
        hookSatelliteManagerEnabled(lpparam);
        hookSatelliteManagerSupported(lpparam);
        hookSatelliteControllerEnabled(lpparam);
        hookSatelliteControllerSupported(lpparam);
        hookSatelliteControllerAlternateMethods(lpparam);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SatelliteManager (API pública, Android 14+)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Hook requestIsSatelliteEnabled → responder true vía OutcomeReceiver
     * Firma original: void requestIsSatelliteEnabled(int subId, Executor, OutcomeReceiver<Boolean, SatelliteException>)
     */
    private static void hookSatelliteManagerEnabled(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Buscamos la clase en el classloader del sistema
            Class<?> cls = XposedHelpers.findClass(
                "android.telephony.satellite.SatelliteManager",
                lpparam.classLoader
            );

            // Iteramos métodos para encontrar requestIsSatelliteEnabled
            // (evitamos hardcodear firma completa por posibles variantes entre builds)
            for (Method method : cls.getDeclaredMethods()) {
                if (method.getName().equals("requestIsSatelliteEnabled")) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                // param.args[2] = OutcomeReceiver<Boolean, SatelliteException>
                                Object callback = param.args[2];
                                if (callback != null) {
                                    // Invocamos onResult(true) por reflexión
                                    Method onResult = findOnResult(callback.getClass());
                                    if (onResult != null) {
                                        onResult.invoke(callback, Boolean.TRUE);
                                    }
                                }
                                param.setResult(null); // Cancelar llamada original
                                XposedBridge.log(TAG + ": requestIsSatelliteEnabled → true");
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": error en requestIsSatelliteEnabled hook: " + t);
                            }
                        }
                    });
                    XposedBridge.log(TAG + ": hooked SatelliteManager.requestIsSatelliteEnabled");
                    break;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": SatelliteManager.requestIsSatelliteEnabled no disponible: " + t);
        }
    }

    /**
     * Hook requestIsSatelliteSupported → reportar dispositivo compatible
     */
    private static void hookSatelliteManagerSupported(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(
                "android.telephony.satellite.SatelliteManager",
                lpparam.classLoader
            );

            for (Method method : cls.getDeclaredMethods()) {
                if (method.getName().equals("requestIsSatelliteSupported")) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                Object callback = param.args[1]; // Executor es [0], callback es [1]
                                if (callback == null) callback = param.args[2];
                                if (callback != null) {
                                    Method onResult = findOnResult(callback.getClass());
                                    if (onResult != null) onResult.invoke(callback, Boolean.TRUE);
                                }
                                param.setResult(null);
                                XposedBridge.log(TAG + ": requestIsSatelliteSupported → true");
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": error en requestIsSatelliteSupported: " + t);
                            }
                        }
                    });
                    XposedBridge.log(TAG + ": hooked SatelliteManager.requestIsSatelliteSupported");
                    break;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": SatelliteManager.requestIsSatelliteSupported no disponible: " + t);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SatelliteController (implementación interna en telephony framework)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Hook isSatelliteEnabled → true
     * Esta es la fuente de verdad que consulta SystemUI indirectamente
     */
    private static void hookSatelliteControllerEnabled(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.internal.telephony.satellite.SatelliteController",
                lpparam.classLoader,
                "isSatelliteEnabled",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + ": SatelliteController.isSatelliteEnabled → true");
                        return true;
                    }
                }
            );
            XposedBridge.log(TAG + ": hooked SatelliteController.isSatelliteEnabled");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": SatelliteController.isSatelliteEnabled no encontrado: " + t);
        }
    }

    /**
     * Hook isSatelliteSupported → true
     */
    private static void hookSatelliteControllerSupported(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.internal.telephony.satellite.SatelliteController",
                lpparam.classLoader,
                "isSatelliteSupported",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + ": SatelliteController.isSatelliteSupported → true");
                        return true;
                    }
                }
            );
            XposedBridge.log(TAG + ": hooked SatelliteController.isSatelliteSupported");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": SatelliteController.isSatelliteSupported no encontrado: " + t);
        }
    }

    /**
     * Métodos alternativos que pueden existir según la versión exacta del build.
     * Se prueban silenciosamente — si no existen, se ignoran.
     */
    private static void hookSatelliteControllerAlternateMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        // Lista de métodos candidatos a hookear con retorno true/1
        String[] booleanMethods = {
            "isSatelliteEnabledOrBeingEnabled",
            "isSatelliteSessionStarted",
            "isSatelliteConnectedViaCarrierWithinHysteresisTime",
            "isDeviceSatelliteEligible",       // disponible desde Android 14 QPR
            "isSatelliteBeingEnabled",
            "isSatelliteProvisioned",
        };

        for (String methodName : booleanMethods) {
            try {
                XposedHelpers.findAndHookMethod(
                    "com.android.internal.telephony.satellite.SatelliteController",
                    lpparam.classLoader,
                    methodName,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return true;
                        }
                    }
                );
                XposedBridge.log(TAG + ": hooked SatelliteController." + methodName);
            } catch (Throwable ignored) {
                // Método no existe en este build — silencioso
            }
        }

        // getSatelliteModemState → IDLE (1) para indicar activo pero sin sesión
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.internal.telephony.satellite.SatelliteController",
                lpparam.classLoader,
                "getSatelliteModemState",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        // SatelliteModemState.SATELLITE_MODEM_STATE_IDLE = 1
                        return 1;
                    }
                }
            );
            XposedBridge.log(TAG + ": hooked SatelliteController.getSatelliteModemState → IDLE");
        } catch (Throwable ignored) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilidad: buscar onResult() en la jerarquía de clases del callback
    // ─────────────────────────────────────────────────────────────────────────

    private static Method findOnResult(Class<?> cls) {
        Class<?> current = cls;
        while (current != null) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getName().equals("onResult") && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    return m;
                }
            }
            for (Class<?> iface : current.getInterfaces()) {
                for (Method m : iface.getDeclaredMethods()) {
                    if (m.getName().equals("onResult") && m.getParameterCount() == 1) {
                        m.setAccessible(true);
                        return m;
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
