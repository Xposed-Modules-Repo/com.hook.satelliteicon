# LSPosed Module — Satellite Icon (NTN)

![Android](https://img.shields.io/badge/Android-15--16-green)
![LSPosed](https://img.shields.io/badge/LSPosed-Compatible-green)
![Status](https://img.shields.io/badge/Status-Beta-blue)

## ESPAÑOL

Módulo LSPosed para forzar el ícono de conexión satelital (NTN)  
en la status bar de Android 15 y 16

---

## Requisitos

| Componente | Versión |
|---|---|
| Android Studio | Hedgehog 2023.1.1+ |
| Android Gradle Plugin | 8.3.0 |
| compileSdk | 36 (Android 16) |
| LSPosed | Última versión estable |
| Root | Magisk / KernelSU-Next / APatch |
| SIM | Dada de baja |

---

## Compilar
1. Abrir el proyecto en Android Studio  
2. `Build → Build APK(s)`  
3. APK en `app/build/outputs/apk/`

---

## Instalar

```bash
adb install app-release-unsigned.apk
```

Activar en LSPosed → reiniciar

---

## Notas
- Solo cosmético
- no da red satelital real

## ENGLISH

LSPosed module to force the satellite connection icon (NTN)  
in the status bar of Android 15 & 16

---

## Requirements

| Component | Version |
|---|---|
| Android Studio | Hedgehog 2023.1.1+ |
| Android Gradle Plugin | 8.3.0 |
| compileSdk | 36 |
| LSPosed | Latest stable |
| Root | Magisk / KernelSU-Next / APatch |
| SIM | Deactivated |

---

## Build

1. Open project  
2. Build APK  
3. Output in `/outputs/apk/`

---

## Install

```bash
adb install app-release-unsigned.apk
```

Enable in LSPosed → reboot

---

## Notes

- Cosmetic only  
- No real satellite connectivity
