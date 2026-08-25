import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

// Build-time secrets are read from local.properties (gitignored) so they are
// never committed. Empty values degrade gracefully at runtime.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Google Maps API key. Missing key → maps render blank but the build still works.
val mapsApiKey: String = localProps.getProperty("MAPS_API_KEY") ?: ""

// Demo mode credentials: the app silently signs in as the configured demo role
// and skips the login/signup screens. Uncomment exactly ONE role at a time in
// local.properties (owner wins if both are set). Empty → demo mode off.
val demoEmail: String = localProps.getProperty("DEMO_EMAIL") ?: ""
val demoPassword: String = localProps.getProperty("DEMO_PASSWORD") ?: ""
val demoOwnerEmail: String = localProps.getProperty("DEMO_OWNER_EMAIL") ?: ""
val demoOwnerPassword: String = localProps.getProperty("DEMO_OWNER_PASSWORD") ?: ""

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.app.checkot"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.app.checkot"
        minSdk = 26
        targetSdk = 36
        versionCode = 22
        versionName = "3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Injected into AndroidManifest as the Maps API key placeholder.
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey

        // Demo mode (see AuthViewModel.init): the app auto-signs-in as a fixed
        // demo customer. Exposed via BuildConfig so credentials never live in
        // source control.
        buildConfigField("String", "DEMO_EMAIL", "\"${demoEmail.replace("\"", "\\\"")}\"")
        buildConfigField("String", "DEMO_PASSWORD", "\"${demoPassword.replace("\"", "\\\"")}\"")
        buildConfigField("String", "DEMO_OWNER_EMAIL", "\"${demoOwnerEmail.replace("\"", "\\\"")}\"")
        buildConfigField("String", "DEMO_OWNER_PASSWORD", "\"${demoOwnerPassword.replace("\"", "\\\"")}\"")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    // AI car-check: calls the checkCar Callable Cloud Function (Gemini relay)
    implementation("com.google.firebase:firebase-functions-ktx")

    // Google Sign-In via Credential Manager (current API; GoogleSignInClient is
    // deprecated). Phone-auth needs no extra dep — it ships in firebase-auth-ktx.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Biometric / device-credential confirmation for sensitive admin actions
    // (approve/reject). Also brings androidx.fragment, so MainActivity can be a
    // FragmentActivity — which BiometricPrompt requires.
    implementation("androidx.biometric:biometric:1.1.0")

    // Coil — async image loading + disk/memory caching for shop logos (Storage URLs)
    implementation("io.coil-kt:coil-compose:2.7.0")


    // AndroidX Core
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    // For collectAsState with StateFlow
    implementation("androidx.compose.runtime:runtime-livedata:1.11.4")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.android.gms:play-services-base:18.10.0")

    // Google Maps — embedded map (owner picker + client view) and current location
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:maps-compose:6.4.1")
    // Kept at 1.23.0: 1.30+ pulls io.grpc 1.70.x, which clashes with the
    // (NoClassDefFoundError: io.grpc.InternalGlobalInterceptors).
    implementation("com.google.auth:google-auth-library-oauth2-http:1.23.0")
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showStackTraces = true
        showCauses = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

