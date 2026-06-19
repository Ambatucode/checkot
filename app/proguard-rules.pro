# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Firebase models from being obfuscated so Firestore can serialize/deserialize them
-keep class com.app.checkot.model.** { *; }

# Keep service classes (FCM, NotificationHelper) from being obfuscated
-keep class com.app.checkot.service.** { *; }

# Keep Google Auth classes used for generating FCM tokens
-keep class com.google.auth.** { *; }

# Keep Coroutines and Firebase classes safe
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod