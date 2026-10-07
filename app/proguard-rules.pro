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

# Keep the MediaPipe vision task APIs used by Social face filters.
# Do not keep all of com.google.mediapipe: tasks-core 1.0.0 contains optional GraphProfiler/Graph references
# to generated CalculatorProfileProto and GraphTemplateProto classes that are not shipped in its AAR.
-keep class com.google.mediapipe.tasks.vision.** { *; }

# MediaPipe Graph initialization relies on Flogger caller-stack detection; keep it from R8 renaming.
-keep class com.google.common.flogger.** { *; }
