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
# Keep JPush classes
-dontoptimize
-dontpreverify

-keep class * extends cn.jpush.android.service.JCommonService { *; }
-dontwarn cn.jiguang.**
-keep class cn.jiguang.** { *; }
-keep class cn.jpush.** { *; }
-keep class cn.jiguang.** { *; }
-keep class cn.jiguang.android.** { *; }
-keep class cn.jiguang.api.** { *; }
-keep class cn.jiguang.net.** { *; }
-dontwarn cn.jpush.**

# Keep your Application class
-keep class com.espressif.EspApplication { *; }

# Keep custom annotations
-keepattributes *Annotation*

# Keep all classes in the `com.espressif.rainmaker` package
-keep class com.espressif.rainmaker.** { *; }