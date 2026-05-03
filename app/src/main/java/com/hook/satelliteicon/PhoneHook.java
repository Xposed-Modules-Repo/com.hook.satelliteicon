package com.hook.satelliteicon;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * PhoneHook
 *
 * Hook en el proceso com.android.phone (pid ~2497 en MistOS 4.5).
 * Aquí es donde SatelliteController realmente vive y ejecuta.
 *
 * PROBLEMA DIAGNOSTICADO:
 *   SatelliteController.iisInCarrierRoamingNbIotNtn() → "satellite is disabled"
 *   → Phone-0.notifyCarrierRoamingNtnEligibleStateChanged(eligible=false)
 *   → TelephonyRegistry.notifyCarrierRoamingNtnSignalStrengthChanged(strength=0)
 *
 * SOLUCIÓN:
 *   1. Hookear iisInCarrierRoamingNbIotNtn() → true
 *   2. Hookear getCarrierRoamingNtnConnectType() → 2 (NB-IoT)
 *   3. Interceptar notifyCarrierRoamingNtnEligibleStateChanged para forzar eligible=true
 *   4. Interceptar notifyCarrierRoamingNtnSignalStrengthChanged para pasar strength > 0
 */
public class PhoneHook {

    private static final String TAG = "SatelliteIconHook/Phone";

    // Strength a reportar (1-5, donde >0 muestra el ícono)
    private static final int FAKE_NTN_SIGNAL_STRENGTH = 2;

    public static void init(XC_LoadPackage.LoadPackageParam lpparam) {
        hookSatelliteController(lpparam);
        hookPhoneNotifications(lpparam);
        hookNtnSignalStrength(lpparam);
        hookSatelliteAccessController(lpparam);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SatelliteController — clase principal
    // ─────────────────────────────────────────────────────────────────────────

    private static void hookSatelliteController(XC_LoadPackage.LoadPackageParam lpparam) {
        final String SC = "com.android.internal.telephony.satellite.SatelliteController";

        // ── iisInCarrierRoamingNbIotNtn (typo intencional en AOSP) ────────────
        // Este es el método que sigue retornando false. Es la causa raíz.
        try {
            XposedHelpers.findAndHookMethod(SC, lpparam.classLoader,
                "iisInCarrierRoamingNbIotNtn",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + ": iisInCarrierRoamingNbIotNtn → TRUE");
                        return true;
                    }
                });
            XposedBridge.log(TAG + ": hooked iisInCarrierRoamingNbIotNtn ✓");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": iisInCarrierRoamingNbIotNtn no encontrado: " + t);
        }

        // ── Variante sin typo (por si algún build lo corrige) ────────────────
        tryHookBoolean(SC, "isInCarrierRoamingNbIotNtn", lpparam);

        // ── getCarrierRoamingNtnConnectType → 2 (NB_IOT) ────────────────────
        // 0=NONE, 1=DIRECT, 2=NB_IOT_NTN_ROAMING
        try {
            XposedHelpers.findAndHookMethod(SC, lpparam.classLoader,
                "getCarrierRoamingNtnConnectType",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        return 2; // NB_IOT_NTN_ROAMING
                    }
                });
            XposedBridge.log(TAG + ": hooked getCarrierRoamingNtnConnectType → 2 ✓");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": getCarrierRoamingNtnConnectType no encontrado: " + t);
        }

        // ── isSatelliteEnabled ───────────────────────────────────────────────
        tryHookBoolean(SC, "isSatelliteEnabled", lpparam);
        tryHookBoolean(SC, "isSatelliteEnabledOrBeingEnabled", lpparam);
        tryHookBoolean(SC, "isSatelliteAttachEnabledForCarrier", lpparam);
        tryHookBoolean(SC, "isSatelliteSupported", lpparam);

        // ── getSatelliteNtnSignalStrength: retornar fuerza > 0 ──────────────
        try {
            XposedHelpers.findAndHookMethod(SC, lpparam.classLoader,
                "getSatelliteNtnSignalStrength",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        // Si retorna 0, sustituir por FAKE_NTN_SIGNAL_STRENGTH
                        Object result = param.getResult();
                        if (result instanceof Integer && (Integer) result == 2) {
                            param.setResult(FAKE_NTN_SIGNAL_STRENGTH);
                        }
                    }
                });
            XposedBridge.log(TAG + ": hooked getSatelliteNtnSignalStrength ✓");
        } catch (Throwable ignored) {}

        // ── Hookear handleEventOnDeviceAlignedWithSatellite para disparar ────
        // el evento de "conectado al satélite" artificialmente
        try {
            XposedHelpers.findAndHookMethod(SC, lpparam.classLoader,
                "updateNtnSignalStrengthReporting",
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        // Forzar que siempre reporte como si debiera mostrar el ícono
                        param.args[0] = true;
                    }
                });
        } catch (Throwable ignored) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phone — notificaciones hacia TelephonyRegistry
    // ─────────────────────────────────────────────────────────────────────────

    private static void hookPhoneNotifications(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hay dos clases Phone en el stack:
        // com.android.internal.telephony.Phone (base)
        // com.android.internal.telephony.GsmCdmaPhone (implementación)

        String[] phoneClasses = {
            "com.android.internal.telephony.Phone",
            "com.android.internal.telephony.GsmCdmaPhone",
        };

        for (String className : phoneClasses) {
            // notifyCarrierRoamingNtnEligibleStateChanged(boolean eligible)
            try {
                XposedHelpers.findAndHookMethod(className, lpparam.classLoader,
                    "notifyCarrierRoamingNtnEligibleStateChanged",
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            boolean wasEligible = (boolean) param.args[0];
                            if (!wasEligible) {
                                XposedBridge.log(TAG + ": notifyCarrierRoamingNtnEligibleStateChanged: false→TRUE");
                                param.args[0] = true; // Forzar eligible=true
                            }
                        }
                    });
                XposedBridge.log(TAG + ": hooked " + className + ".notifyCarrierRoamingNtnEligibleStateChanged ✓");
            } catch (Throwable ignored) {}

            // notifyCarrierRoamingNtnSignalStrengthChanged(int strength)
            // Si la SIM no tiene subId válido, TelephonyRegistry rechaza el evento.
            // Forzamos un strength > 0.
            try {
                XposedHelpers.findAndHookMethod(className, lpparam.classLoader,
                    "notifyCarrierRoamingNtnSignalStrengthChanged",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            int strength = (int) param.args[0];
                            if (strength == 0) {
                                XposedBridge.log(TAG + ": notifyCarrierRoamingNtnSignalStrengthChanged: 0→" + FAKE_NTN_SIGNAL_STRENGTH);
                                param.args[0] = FAKE_NTN_SIGNAL_STRENGTH;
                            }
                        }
                    });
                XposedBridge.log(TAG + ": hooked " + className + ".notifyCarrierRoamingNtnSignalStrengthChanged ✓");
            } catch (Throwable ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NtnSignalStrength — objeto que encapsula el nivel de señal NTN
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * android.telephony.NtnSignalStrength(int level) - nivel 0-4.
     * TelephonyRegistry crea este objeto con el int que le pasa Phone,
     * y SystemUI lee getLevel() para saber cuántas barras dibujar.
     * Si getLevel() == 0 → barras vacías. Forzamos a FAKE_LEVEL.
     */
    private static void hookNtnSignalStrength(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.telephony.NtnSignalStrength",
                lpparam.classLoader,
                "getLevel",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        return FAKE_NTN_SIGNAL_STRENGTH; // 2 barras rellenas
                    }
                });
            XposedBridge.log(TAG + ": hooked NtnSignalStrength.getLevel → " + FAKE_NTN_SIGNAL_STRENGTH + " ✓");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": NtnSignalStrength.getLevel no encontrado: " + t);
        }

        // También el campo mLevel por si se accede directamente
        try {
            Class<?> cls = XposedHelpers.findClass(
                "android.telephony.NtnSignalStrength", lpparam.classLoader
            );
            // 1. Buscamos todos los constructores de la clase de forma manual
            java.lang.reflect.Constructor<?>[] constructors = cls.getDeclaredConstructors();

            for (java.lang.reflect.Constructor<?> constructor : constructors) {
                XposedBridge.hookMethod(constructor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            // Forzamos el valor usando la herramienta que ya sabemos que te funciona
                            XposedHelpers.setObjectField(param.thisObject, "mLevel", FAKE_NTN_SIGNAL_STRENGTH);
                        } catch (Throwable t) {
                            // Ignorado
                        }
                    }
                });
            }
            XposedBridge.log(TAG + ": hooked NtnSignalStrength constructor → mLevel=" + FAKE_NTN_SIGNAL_STRENGTH + " ✓");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": NtnSignalStrength constructor no encontrado: " + t);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SatelliteAccessController — controla si el dispositivo puede usar satélite
    // ─────────────────────────────────────────────────────────────────────────

    private static void hookSatelliteAccessController(XC_LoadPackage.LoadPackageParam lpparam) {
        final String SAC = "com.android.internal.telephony.satellite.SatelliteAccessController";

        String[] booleanMethods = {
            "isInSatelliteAllowedRegion",
            "isSatelliteAccessAllowed",
            "isSatelliteCommunicationAllowedForCurrentLocation",
        };

        for (String m : booleanMethods) {
            tryHookBoolean(SAC, m, lpparam);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilidad
    // ─────────────────────────────────────────────────────────────────────────

    private static void tryHookBoolean(String className, String methodName,
                                        XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(className, lpparam.classLoader,
                methodName,
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        return true;
                    }
                });
            XposedBridge.log(TAG + ": hooked " + className + "." + methodName + " ✓");
        } catch (Throwable ignored) {}
    }
}
