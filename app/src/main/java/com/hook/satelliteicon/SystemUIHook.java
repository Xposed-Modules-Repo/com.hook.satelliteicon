package com.hook.satelliteicon;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * SystemUIHook v2
 *
 * DIAGNÓSTICO:
 *   - MobileState NO tiene campo NTN/isNonTerrestrialNetwork en MistOS 4.5
 *   - El ícono CarrierRoamingNtn se maneja fuera de MobileSignalController
 *   - El evento viene por TelephonyCallback.onCarrierRoamingNtnEligibleStateChanged
 *     que llega al MobileIconInteractor o similar en el nuevo pipeline de SystemUI
 *
 * ESTRATEGIA (3 capas de defensa):
 *   1. Hookear el TelephonyCallback en SystemUI que escucha los eventos NTN
 *   2. Hookear StatusBarIconController para forzar visibilidad del slot NTN
 *   3. Hookear MobileIconInteractor (pipeline nuevo) métodos NTN
 */
public class SystemUIHook {

    private static final String TAG = "SatelliteIconHook/SystemUI";

    // Nombres de slot del ícono NTN en la status bar
    private static final String[] NTN_ICON_SLOTS = {
            "ntn_indicator",
            "satellite_indicator",
            "carrier_roaming_ntn",
            "ntn",
    };

    public static void init(XC_LoadPackage.LoadPackageParam lpparam) {
        hookTelephonyCallback(lpparam);
        hookMobileIconInteractor(lpparam);
        hookStatusBarIconController(lpparam);
        hookCarrierRoamingNtnController(lpparam);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Hookear TelephonyCallback en SystemUI
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * En Android 16, SystemUI registra TelephonyCallback para escuchar eventos de red.
     * Cuando llega onCarrierRoamingNtnEligibleStateChanged(false), el ícono no se muestra.
     * Hookeamos para forzar el argumento a true.
     */
    private static void hookTelephonyCallback(XC_LoadPackage.LoadPackageParam lpparam) {
        // Buscar clases que implementen TelephonyCallback.OnCarrierRoamingNtnEligibleStateChangedListener
        String[] candidateClasses = {
                "com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.FullMobileConnectionRepository",
                "com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.MobileConnectionRepositoryImpl",
                "com.android.systemui.statusbar.pipeline.mobile.data.datasource.MobileTelephonyDataSource",
                "com.android.systemui.statusbar.connectivity.MobileSignalController",
        };

        for (String className : candidateClasses) {
            try {
                Class<?> cls = XposedHelpers.findClass(className, lpparam.classLoader);
                for (Method m : cls.getDeclaredMethods()) {
                    String name = m.getName();
                    if (name.contains("CarrierRoamingNtn") ||
                            name.contains("NtnEligible") ||
                            name.contains("NtnSignal") ||
                            name.contains("onCarrierRoaming")) {

                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                // Forzar eligible=true y strength>0
                                for (int i = 0; i < param.args.length; i++) {
                                    if (param.args[i] instanceof Boolean) {
                                        param.args[i] = true;
                                    }
                                    if (param.args[i] instanceof Integer && (int)param.args[i] == 0) {
                                        param.args[i] = 2; // signal strength 2
                                    }
                                }
                                XposedBridge.log(TAG + ": " + m.getName() + " interceptado → NTN=true");
                            }
                        });
                        XposedBridge.log(TAG + ": hooked " + className + "." + name + " ✓");
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Hookear la clase base del TelephonyCallback directamente
        try {
            Class<?> cbClass = XposedHelpers.findClass(
                    "android.telephony.TelephonyCallback", lpparam.classLoader
            );
            // Buscar subclases anónimas en el proceso SystemUI que implementen la interfaz NTN
            // Esto es difícil de hacer directamente, así que usamos un enfoque diferente:
            // hookear la clase interna OnCarrierRoamingNtnEligibleStateChangedListener
            XposedBridge.log(TAG + ": TelephonyCallback encontrado, buscando interfaces NTN...");

            for (Class<?> inner : cbClass.getDeclaredClasses()) {
                if (inner.getSimpleName().contains("Ntn") ||
                        inner.getSimpleName().contains("NTN") ||
                        inner.getSimpleName().contains("CarrierRoaming")) {
                    XposedBridge.log(TAG + ": Encontrada interfaz NTN: " + inner.getName());
                }
            }
        } catch (Throwable ignored) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. MobileIconInteractor — pipeline nuevo de SystemUI (Android 14+)
    // ─────────────────────────────────────────────────────────────────────────

    private static void hookMobileIconInteractor(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] interactorClasses = {
                "com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconInteractorImpl",
                "com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconInteractor",
                "com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractor",
                "com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractorImpl",
        };

        for (String className : interactorClasses) {
            try {
                Class<?> cls = XposedHelpers.findClass(className, lpparam.classLoader);
                for (Method m : cls.getDeclaredMethods()) {
                    String nameLower = m.getName().toLowerCase();
                    if ((nameLower.contains("ntn") || nameLower.contains("satellite") ||
                            nameLower.contains("nonterrestrial") || nameLower.contains("carrierroaming"))
                            && m.getReturnType() == boolean.class) {
                        XposedBridge.hookMethod(m, new XC_MethodReplacement() {
                            @Override
                            protected Object replaceHookedMethod(MethodHookParam param) {
                                XposedBridge.log(TAG + ": MobileIconInteractor." + m.getName() + " → true");
                                return true;
                            }
                        });
                        XposedBridge.log(TAG + ": hooked " + className + "." + m.getName() + " ✓");
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. StatusBarIconController — inyección directa del ícono
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cuando el slot del ícono NTN intenta ser ocultado (visible=false),
     * interceptamos y lo forzamos a visible=true.
     */
    private static void hookStatusBarIconController(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] controllerClasses = {
                "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl",
                "com.android.systemui.statusbar.phone.StatusBarIconController",
        };

        for (String className : controllerClasses) {
            // setIconVisibility(String slot, boolean visible)
            try {
                XposedHelpers.findAndHookMethod(
                        className, lpparam.classLoader,
                        "setIconVisibility",
                        String.class, boolean.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                String slot = (String) param.args[0];
                                if (isNtnSlot(slot)) {
                                    XposedBridge.log(TAG + ": setIconVisibility(" + slot + ") → true");
                                    param.args[1] = true;
                                }
                            }
                        });
                XposedBridge.log(TAG + ": hooked " + className + ".setIconVisibility ✓");
            } catch (Throwable ignored) {}

            // removeIcon(String slot) — evitar que eliminen el ícono
            try {
                XposedHelpers.findAndHookMethod(
                        className, lpparam.classLoader,
                        "removeIcon",
                        String.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                String slot = (String) param.args[0];
                                if (isNtnSlot(slot)) {
                                    XposedBridge.log(TAG + ": removeIcon(" + slot + ") → bloqueado");
                                    param.setResult(null); // cancelar la eliminación
                                }
                            }
                        });
            } catch (Throwable ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. CarrierRoamingNtnController — controlador específico NTN en SystemUI
    // ─────────────────────────────────────────────────────────────────────────

    private static void hookCarrierRoamingNtnController(XC_LoadPackage.LoadPackageParam lpparam) {
        // En Android 16 QPR2, puede existir un controlador dedicado para el ícono NTN
        String[] controllerCandidates = {
                "com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarCarrierNtnView",
                "com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconViewModel",
                "com.android.systemui.statusbar.pipeline.satellite.ui.viewmodel.DeviceBasedSatelliteViewModel",
        };

        for (String className : controllerCandidates) {
            try {
                Class<?> cls = XposedHelpers.findClass(className, lpparam.classLoader);
                for (Method m : cls.getDeclaredMethods()) {
                    String name = m.getName();
                    if ((name.contains("ntn") || name.contains("Ntn") || name.contains("NTN") ||
                            name.contains("satellite") || name.contains("Satellite") ||
                            name.contains("visible") || name.contains("Visible") ||
                            name.contains("show") || name.contains("Show"))
                            && m.getReturnType() == boolean.class
                            && m.getParameterCount() == 0) {
                        XposedBridge.hookMethod(m, new XC_MethodReplacement() {
                            @Override
                            protected Object replaceHookedMethod(MethodHookParam param) {
                                return true;
                            }
                        });
                        XposedBridge.log(TAG + ": hooked " + className + "." + name + " ✓");
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // Hookear MobileSignalController.updateTelephony como fallback
        // (aunque MobileState no tenga NTN, al menos confirmamos que se llama)
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.systemui.statusbar.connectivity.MobileSignalController",
                    lpparam.classLoader,
                    "updateTelephony",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object state = XposedHelpers.getObjectField(param.thisObject, "mCurrentState");
                                if (state != null) {
                                    setNtnFieldIfExists(state);

                                    // ── Forzar level=2 para que se vean 2 barras rellenas ──
                                    // MobileState.level (int, 0-4) controla cuántas barras
                                    // se dibujan dentro del ícono satelital.
                                    // Con level=0, el ícono aparece pero vacío.
                                    try {
                                        int currentLevel = (Integer) XposedHelpers.getObjectField(state, "level");
                                        if (currentLevel == 0) {
                                            XposedHelpers.setObjectField(state, "level", 2);
                                            XposedBridge.log(TAG + ": MobileState.level: 0→2 ✓");
                                        }
                                    } catch (Throwable ignored) {
                                    }

                                    // rssi también puede usarse para calcular el nivel visualmente
                                    try {
                                        int rssi = (Integer) XposedHelpers.getObjectField(state, "rssi");
                                        if (rssi == 0 || rssi == Integer.MAX_VALUE) {
                                            XposedHelpers.setObjectField(state, "rssi", -80); // valor razonable

                                        }
                                    } catch (Throwable ignored) {
                                    }

                                    triggerNotify(param.thisObject);
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }
        // Hookear NtnSignalStrength.getLevel() también en SystemUI
        // (el mismo objeto viaja desde TelephonyRegistry hasta aquí)
        try {
            XposedHelpers.findAndHookMethod(
                    "android.telephony.NtnSignalStrength",
                    lpparam.classLoader,
                    "getLevel",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return 2; // 2 barras rellenas
                        }
                    });
            XposedBridge.log(TAG + ": hooked NtnSignalStrength.getLevel en SystemUI → 2 ✓");
        } catch (Throwable ignored) {
        }
    }
        // ─────────────────────────────────────────────────────────────────────────
        // Utilidades
        // ─────────────────────────────────────────────────────────────────────────

        private static boolean isNtnSlot(String slot) {
            if (slot == null) return false;
            String lower = slot.toLowerCase();
            for (String candidate : NTN_ICON_SLOTS) {
                if (lower.contains(candidate.replace("_", "")) ||
                        lower.contains(candidate)) return true;
            }
            return lower.contains("satellite") || lower.contains("ntn");
        }

        private static boolean ntnFieldLogged = false;
        private static void setNtnFieldIfExists(Object stateObj) {
            String[] candidates = {
                    "isNonTerrestrialNetwork", "mIsNtn", "ntn", "isSatellite",
                    "isNtn", "satelliteMode", "isCarrierRoamingNtn"
            };
            for (String f : candidates) {
                try {
                    Field field = findFieldInHierarchy(stateObj.getClass(), f);
                    if (field != null && (field.getType() == boolean.class || field.getType() == Boolean.class)) {
                        field.setAccessible(true);
                        field.set(stateObj, true);
                        XposedBridge.log(TAG + ": campo NTN seteado: " + f);
                        return;
                    }
                } catch (Throwable ignored) {}
            }
            if (!ntnFieldLogged) {
                ntnFieldLogged = true;
                XposedBridge.log(TAG + ": MobileState sin campo NTN — usando hooks de pipeline");
            }
        }

        private static Field findFieldInHierarchy(Class<?> cls, String name) {
            Class<?> c = cls;
            while (c != null && c != Object.class) {
                try { return c.getDeclaredField(name); }
                catch (NoSuchFieldException e) { c = c.getSuperclass(); }
            }
            return null;
        }

        private static void triggerNotify(Object controller) {
            for (String m : new String[]{"notifyListenersIfNecessary", "notifyListeners"}) {
                try { XposedHelpers.callMethod(controller, m); return; }
                catch (Throwable ignored) {}
            }
        }
    }
