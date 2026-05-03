package com.hook.satelliteicon;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * SatelliteIconHook v2 — Módulo LSPosed
 *
 * DIAGNÓSTICO (logs del 25/04/2026):
 *   - SatelliteController vive en com.android.phone (pid 2497), NO en system_server
 *   - El ícono es CarrierRoamingNtn (NB-IoT NTN), no standalone satellite
 *   - Flujo real: iisInCarrierRoamingNbIotNtn → notifyCarrierRoamingNtnEligibleStateChanged
 *                 → TelephonyRegistry → SystemUI listener → ícono
 *   - MobileState NO tiene campo NTN en MistOS 4.5
 *   - TelephonyRegistry rechaza el evento por subId inválido (SIM dada de baja)
 *
 * SCOPES NECESARIOS:
 *   android          → TelephonyRegistry: bypass validación de subId
 *   com.android.phone → SatelliteController: hooks de estado NTN
 *   com.android.systemui → SystemUI: forzar ícono visible en StatusBar
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "SatelliteIconHook";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log(TAG + ": cargado en → " + lpparam.packageName);

        switch (lpparam.packageName) {

            case "android":
                // system_server: TelephonyRegistry
                XposedBridge.log(TAG + ": aplicando TelephonyRegistryHook");
                TelephonyRegistryHook.init(lpparam);
                break;
                case "com.android.phone":
                    XposedBridge.log(TAG + ": aplicando PhoneHook y SimPinBypass");
                    PhoneHook.init(lpparam);
                    // AGREGA ESTA LÍNEA PARA EL BYPASS DE PIN EN TELEFONÍA
                    SimPinBypassHook.initPhone(lpparam);
                    break;

                case "com.android.systemui":
                    XposedBridge.log(TAG + ": aplicando SystemUIHook y SimPinBypass");
                    SystemUIHook.init(lpparam);
                    // AGREGA ESTA LÍNEA PARA EL BYPASS DE PIN EN PANTALLA DE BLOQUEO
                    SimPinBypassHook.initSystemUI(lpparam);
                    break;
            }
}
        }


