package com.hook.satelliteicon;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * TelephonyRegistryHook
 *
 * Hook en system_server (proceso "android").
 *
 * PROBLEMA DIAGNOSTICADO:
 *   TelephonyRegistry.notifyCarrierRoamingNtnSignalStrengthChanged: "invalid subscription id"
 *
 *   La SIM dada de baja tiene subId=-1 (no registrada en el carrier).
 *   TelephonyRegistry valida que el subId sea válido antes de propagar el evento
 *   hacia los listeners de SystemUI. Al fallar la validación, SystemUI nunca
 *   recibe el evento y el ícono no se muestra.
 *
 * SOLUCIÓN:
 *   Hookear el método de validación de TelephonyRegistry para aceptar subId inválidos
 *   en el contexto de notificaciones NTN/satélite.
 */
public class TelephonyRegistryHook {

    private static final String TAG = "SatelliteIconHook/TelephonyRegistry";

    public static void init(XC_LoadPackage.LoadPackageParam lpparam) {
        hookNtnSignalStrengthNotification(lpparam);
        hookNtnEligibleNotification(lpparam);
    }

    /**
     * Hookear notifyCarrierRoamingNtnSignalStrengthChanged en TelephonyRegistry.
     * El problema: valida "if (!checkNotifyPermission...)" o "if (subId == INVALID...)" y aborta.
     * Solución: interceptar y redispachar con subId corregido si el original es inválido.
     */
    private static void hookNtnSignalStrengthNotification(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Buscar todos los métodos que tengan "NtnSignalStrength" en el nombre
            Class<?> cls = XposedHelpers.findClass(
                "com.android.server.TelephonyRegistry", lpparam.classLoader
            );

            for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
                if (m.getName().contains("NtnSignalStrength") ||
                    m.getName().contains("NTNSignalStrength")) {

                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            // El primer argumento int suele ser subId o slotIndex
                            // Si es -1 (INVALID_SUBSCRIPTION_ID), sustituir por 0 o 1
                            for (int i = 0; i < param.args.length; i++) {
                                if (param.args[i] instanceof Integer) {
                                    int val = (Integer) param.args[i];
                                    if (val == -1) {
                                        param.args[i] = 0; // usar slot 0
                                        XposedBridge.log(TAG + ": " + m.getName()
                                            + " arg[" + i + "]: -1 → 0");
                                    }
                                }
                            }
                        }
                    });
                    XposedBridge.log(TAG + ": hooked TelephonyRegistry." + m.getName() + " ✓");
                }
            }

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": TelephonyRegistry no encontrado en system_server: " + t);
        }
    }

    /**
     * Lo mismo para notifyCarrierRoamingNtnEligibleStateChanged.
     */
    private static void hookNtnEligibleNotification(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(
                "com.android.server.TelephonyRegistry", lpparam.classLoader
            );

            for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
                if (m.getName().contains("NtnEligible") ||
                    m.getName().contains("NTNEligible") ||
                    m.getName().contains("CarrierRoamingNtn")) {

                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            // Forzar eligible=true si hay boolean en los args
                            for (int i = 0; i < param.args.length; i++) {
                                if (param.args[i] instanceof Boolean) {
                                    param.args[i] = true;
                                }
                                if (param.args[i] instanceof Integer && (Integer)param.args[i] == -1) {
                                    param.args[i] = 0;
                                }
                            }
                            XposedBridge.log(TAG + ": " + m.getName() + " → forzado eligible=true");
                        }
                    });
                    XposedBridge.log(TAG + ": hooked TelephonyRegistry." + m.getName() + " ✓");
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": error hookeando NtnEligible en TelephonyRegistry: " + t);
        }
    }
}
