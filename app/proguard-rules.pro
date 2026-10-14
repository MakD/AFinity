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

# Crash readability
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepnames class com.makd.afinity.** { *; }

# Google Cast SDK
-keep class com.google.android.gms.cast.** { *; }
-keep class com.makd.afinity.cast.CastOptionsProvider { *; }