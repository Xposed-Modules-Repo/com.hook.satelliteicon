package com.hook.satelliteicon;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * SimPinBypassHook
 *
 * Elimina el pedido de PIN/PUK de la SIM en arranque y en Keyguard.
 *
 * Hooks en com.android.phone:
 *   UiccProfile.getState() → READY  (el stack de telefonía cree que la SIM ya está desbloqueada)
 *
 * Hooks en com.android.systemui:
 *   KeyguardUpdateMonitor.isSimPinSecure()           → false
 *   KeyguardUpdateMonitor.isSimPinVoiceCallRequired() → false
 *   KeyguardSimPinView / KeyguardSimPukView: bloquear apertura
 */
public class SimPinBypassHook {

    private static final String TAG = "SatelliteIconHook/SimPin";

    // ─── Llamado desde PhoneHook.init() ───────────────────────────────────────
    public static void initPhone(XC_LoadPackage.LoadPackageParam lpparam) {
        hookUiccProfileState(lpparam);
        hookIccCardProxy(lpparam);
    }

    // ─── Llamado desde SystemUIHook.init() ────────────────────────────────────
    public static void initSystemUI(XC_LoadPackage.LoadPackageParam lpparam) {
        hookKeyguardUpdateMonitor(lpparam);
        hookKeyguardSimViews(lpparam);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // com.android.phone — UiccProfile
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * UiccProfile.getState() normalmente devuelve PIN_REQUIRED o PUK_REQUIRED.
     * Lo forzamos a READY para que el sistema no solicite PIN en ningún punto.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void hookUiccProfileState(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Obtener el valor enum READY
            Class stateClass = XposedHelpers.findClass(
                "com.android.internal.telephony.IccCardConstants$State",
                lpparam.classLoader
            );
            final Object STATE_READY = Enum.valueOf(stateClass, "READY");

            XposedHelpers.findAndHookMethod(
                "com.android.internal.telephony.uicc.UiccProfile",
                lpparam.classLoader,
                "getState",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        return STATE_READY;
                    }
                });
            XposedBridge.log(TAG + ": hooked UiccProfile.getState → READY ✓");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": UiccProfile.getState error: " + t);
        }

        // isPinLocked() → false
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.internal.telephony.uicc.UiccProfile",
                lpparam.classLoader,
                "isPinLocked",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        return false;
                    }
                });
            XposedBridge.log(TAG + ": hooked UiccProfile.isPinLocked → false ✓");
        } catch (Throwable ignored) {}

        // getIccLockEnabled() → false
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.internal.telephony.uicc.UiccProfile",
                lpparam.classLoader,
                "getIccLockEnabled",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        return false;
                    }
                });
            XposedBridge.log(TAG + ": hooked UiccProfile.getIccLockEnabled → false ✓");
        } catch (Throwable ignored) {}
    }

    /**
     * IccCardProxy / UiccController también exponen el estado de la SIM.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void hookIccCardProxy(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] classes = {
            "com.android.internal.telephony.uicc.UiccController",
            "com.android.internal.telephony.uicc.UiccCard",
        };

        for (String cls : classes) {
            try {
                Class stateClass = XposedHelpers.findClass(
                    "com.android.internal.telephony.IccCardConstants$State",
                    lpparam.classLoader
                );
                final Object STATE_READY = Enum.valueOf(stateClass, "READY");

                XposedHelpers.findAndHookMethod(cls, lpparam.classLoader,
                    "getState",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return STATE_READY;
                        }
                    });
                XposedBridge.log(TAG + ": hooked " + cls + ".getState → READY ✓");
            } catch (Throwable ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // com.android.systemui — KeyguardUpdateMonitor
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * KeyguardUpdateMonitor es la fuente de verdad del Keyguard sobre el estado SIM.
     * isSimPinSecure() controla si el Keyguard muestra la pantalla de PIN.
     */
    private static void hookKeyguardUpdateMonitor(XC_LoadPackage.LoadPackageParam lpparam) {
        final String KUM = "com.android.keyguard.KeyguardUpdateMonitor";

        String[] falseMethods = {
            "isSimPinSecure",
            "isSimPinVoiceCallRequired",
            "isSimLocked",
            "isSimPinEnabled",
            "isSimPukLocked",
        };

        for (String method : falseMethods) {
            try {
                XposedHelpers.findAndHookMethod(KUM, lpparam.classLoader,
                    method,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return false;
                        }
                    });
                XposedBridge.log(TAG + ": hooked KeyguardUpdateMonitor." + method + " → false ✓");
            } catch (Throwable ignored) {}
        }

        // Variante con subId como argumento
        for (String method : falseMethods) {
            try {
                XposedHelpers.findAndHookMethod(KUM, lpparam.classLoader,
                    method, int.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return false;
                        }
                    });
            } catch (Throwable ignored) {}
        }

        // getSimState() → READY para cualquier slot
        try {
            Class<?> iccStateClass = XposedHelpers.findClass(
                "com.android.internal.telephony.IccCardConstants$State",
                lpparam.classLoader
            );
            final Object STATE_READY = Enum.valueOf(
                (Class<Enum>) iccStateClass, "READY"
            );
            XposedHelpers.findAndHookMethod(KUM, lpparam.classLoader,
                "getSimState", int.class,
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        return STATE_READY;
                    }
                });
            XposedBridge.log(TAG + ": hooked KeyguardUpdateMonitor.getSimState → READY ✓");
        } catch (Throwable ignored) {}
    }

    /**
     * Bloquear que se muestre la vista de PIN o PUK directamente.
     */
    private static void hookKeyguardSimViews(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] pinPukViews = {
            "com.android.keyguard.KeyguardSimPinView",
            "com.android.keyguard.KeyguardSimPukView",
            "com.android.keyguard.KeyguardSimPinViewController",
            "com.android.keyguard.KeyguardSimPukViewController",
        };

        for (String cls : pinPukViews) {
            // resetState() es el método que activa la pantalla de PIN/PUK
            try {
                XposedHelpers.findAndHookMethod(cls, lpparam.classLoader,
                    "resetState",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + ": " + cls + ".resetState bloqueado ✓");
                            return null; // No mostrar la pantalla de PIN/PUK
                        }
                    });
                XposedBridge.log(TAG + ": hooked " + cls + ".resetState ✓");
            } catch (Throwable ignored) {}

            // onResume() — bloquear activación al volver al Keyguard
            try {
                XposedHelpers.findAndHookMethod(cls, lpparam.classLoader,
                    "onResume", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null); // Cancelar
                        }
                    });
            } catch (Throwable ignored) {}
        }
    }
}
