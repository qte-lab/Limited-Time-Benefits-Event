import java.util.*

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

fun getBuildDate(): String {
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    return String.format("%04d%02d%02d", year, month, day)
}

fun getBuildTime(): String {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    return String.format("%02d%02d", hour, minute)
}

// Use system timestamp to generate version code
val timestamp = System.currentTimeMillis()
val generatedVersionCode = (timestamp / 1000).toInt()

// Generate versionName: 1.yyyymmdd.hhmm
val datePart = getBuildDate()
val defaultBuildNumber = getBuildTime()
val buildNumber = project.findProperty("buildNumber") as? String ?: defaultBuildNumber

android {
    namespace = "com.chronie.gift"
    compileSdk {
        version = release(37)
    }

    signingConfigs {
        create("release") {
            storeFile = keystoreProperties["STORE_FILE"]?.let { file(it as String) }
            storePassword = keystoreProperties["STORE_PASSWORD"] as? String
            keyAlias = keystoreProperties["KEY_ALIAS"] as? String
            keyPassword = keystoreProperties["KEY_PASSWORD"] as? String
        }
    }

    defaultConfig {
        applicationId = "com.chronie.gift"
        minSdk = 24
        targetSdk = 37
        
        // Use generated version code
        versionCode = generatedVersionCode
        versionName = "1.$datePart.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    // Navigation dependencies
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // HTTP client libraries
    implementation("io.ktor:ktor-client-core:3.4.3")
    implementation("io.ktor:ktor-client-android:3.4.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.4.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.3")
    
    // Image loading library
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
    
    // Miuix dependencies
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    
    // No longer need Markdown rendering library, we use our own renderer
}