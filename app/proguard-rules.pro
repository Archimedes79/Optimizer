# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# ── Gson ────────────────────────────────────────────────────────────────────
# The portfolio is persisted with Gson via reflection, so the model classes and
# their field names must survive shrinking. Without these rules an obfuscated
# release build would silently write - and then fail to read back - portfolio.json.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

-keep class de.mm.portfoliooptimizerclassic.Security { *; }
-keep class de.mm.portfoliooptimizerclassic.Portfolio { *; }

# Gson's own reflective machinery
-dontwarn sun.misc.**
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking @interface com.google.gson.annotations.SerializedName

# ── MPAndroidChart ──────────────────────────────────────────────────────────
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ── Apache Commons Math ─────────────────────────────────────────────────────
-dontwarn org.apache.commons.math3.**
