# ── BizarreX ProGuard Rules ─────────────────────────────────────────────────

# Keep Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep ExoPlayer / Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Keep OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Compose Runtime (needed for reflection-based recomposition)
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep our data models (serialized to/from JSON)
-keep class com.BizarreX.study.utils.DriveVideo { *; }

# Keep BuildConfig (but obfuscate field values via R8 string encryption)
-keep class com.BizarreX.study.BuildConfig { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Remove all logging in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Aggressive obfuscation
-repackageclasses 'bx'
-allowaccessmodification
-optimizationpasses 5