# LSPosed module — No ofuscar
# El módulo usa reflection extensamente; ofuscar rompe los hooks

-dontobfuscate
-dontoptimize
-dontshrink

-keep class com.hook.satelliteicon.** { *; }
-keep class de.robv.android.xposed.** { *; }

-dontwarn de.robv.android.xposed.**
