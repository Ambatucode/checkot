# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Firebase models from being obfuscated so Firestore can serialize/deserialize them
-keep class com.app.checkot.model.** { *; }

# Keep Application class (referenced by name in manifest — not in code)
-keep class com.app.checkot.CheckotApplication { *; }

# Keep ViewModels — instantiated reflectively by viewModel()
-keep class com.app.checkot.viewmodel.** { *; }

# Keep navigation — routes referenced by string
-keep class com.app.checkot.navigation.** { *; }

# Keep service classes (FCM, NotificationHelper) from being obfuscated
-keep class com.app.checkot.service.** { *; }

# Keep Google Auth classes used for generating FCM tokens
-keep class com.google.auth.** { *; }

# Keep Firebase SDK internals needed for deserialization
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep Coroutines and Firebase classes safe
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod

# Keep Compose classes from being stripped by R8
-keep class androidx.compose.** { *; }

# Keep Kotlin reflection for Firebase serialization
-keep class kotlin.reflect.** { *; }
-keep class kotlin.Metadata { *; }