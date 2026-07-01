import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    FileInputStream(localPropertiesFile).use { localProperties.load(it) }
}
val razorpayKeyTest = localProperties.getProperty("RAZORPAY_KEY_TEST") ?: "rzp_test_placeholder"
val razorpayKeyLive = localProperties.getProperty("RAZORPAY_KEY_LIVE") ?: "rzp_live_placeholder"
val baseUrl = localProperties.getProperty("BASE_URL") ?: "http://10.0.2.2:8080/api/"
val googleWebClientId = localProperties.getProperty("GOOGLE_WEB_CLIENT_ID") ?: "795291263970-placeholder.apps.googleusercontent.com"

android {
    namespace = "com.lockin.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.lockin.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$googleWebClientId\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "RAZORPAY_KEY_ID", "\"$razorpayKeyTest\"")
            buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
        }
        release {
            isMinifyEnabled = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "RAZORPAY_KEY_ID", "\"$razorpayKeyLive\"")
            buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockk)


  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Hilt DI
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)

  // Room Local Database
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  ksp(libs.room.compiler)

  // Networking (Retrofit + OkHttp)
  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.gson)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)

  // Timber Logging
  implementation(libs.timber)

  // RootBeer Root Detection
  implementation(libs.rootbeer)

  // Razorpay Checkout
  implementation(libs.razorpay)

  // Firebase BOM + FCM Messaging + Crashlytics
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.messaging)
  implementation(libs.firebase.crashlytics)

  // AndroidX Biometrics
  implementation(libs.androidx.biometric)

  // AndroidX Security Crypto (EncryptedSharedPreferences)
  implementation(libs.androidx.security.crypto)

  // WorkManager
  implementation(libs.androidx.work.runtime)

  // Hilt WorkManager Integration
  implementation(libs.androidx.hilt.work)
  implementation(libs.androidx.hilt.navigation.compose)
  ksp(libs.androidx.hilt.compiler)

  // Google Credentials & Identity for Google Sign-in
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
}
