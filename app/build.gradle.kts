import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

// Load local.properties for API keys
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.example.cityflux"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.cityflux"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        
        // Groq API Key from local.properties (Free AI API)
        buildConfigField("String", "GROQ_API_KEY", "\"${localProperties.getProperty("GROQ_API_KEY", "")}\"")
        // Keep Gemini for backward compatibility
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\"")
        // Razorpay Key ID (test/live) from local.properties
        buildConfigField("String", "RAZORPAY_KEY_ID", "\"${localProperties.getProperty("RAZORPAY_KEY_ID", "")}\"")
        manifestPlaceholders["RAZORPAY_KEY_ID"] = localProperties.getProperty("RAZORPAY_KEY_ID", "")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions { jvmTarget = "1.8" }

    buildFeatures { 
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // 🔥 Firebase (ONE BOM ONLY)
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")   // ✅ ADD THIS
    implementation("com.google.firebase:firebase-database-ktx")  // Realtime Database
    implementation("com.google.firebase:firebase-messaging-ktx") // Cloud Messaging (FCM)
    implementation ("com.google.firebase:firebase-analytics-ktx")

    // 🤖 Google Gemini AI (optional, keeping for compatibility)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    
    // 🚀 Groq AI (Free, Fast Llama 3)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20231013")
    implementation("com.razorpay:checkout:1.6.41")

    // 🎨 Material
    implementation("com.google.android.material:material:1.11.0")

    // 🧩 AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // 🎨 Jetpack Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-text-google-fonts:1.6.0")

    // 📍 Location
    implementation(libs.play.services.location)

    // �️ Google Maps
    implementation("com.google.maps.android:maps-compose:4.3.3")
    implementation("com.google.android.gms:play-services-maps:18.2.0")

    // 🧠 ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // 🖼 Image loader
    implementation("io.coil-kt:coil-compose:2.5.0")

    // 🧭 Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // 📱 QR Code Generation (Phase 4)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // 🧪 Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // 🛠 Debug
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
