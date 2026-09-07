import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Signierung: Werte kommen entweder aus keystore.properties (lokal, nicht im
// Repo) oder aus Gradle-Properties, die die CI ueber ORG_GRADLE_PROJECT_* setzt.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) FileInputStream(keystorePropertiesFile).use { load(it) }
}

fun signingValue(name: String): String? =
    (project.findProperty(name) as String?) ?: keystoreProperties.getProperty(name)

android {
    namespace = "de.passderdeutschenberge"
    // androidx.core 1.19 und lifecycle 2.11 verlangen laut AAR-Metadaten
    // compileSdk 37. API 37 wird nur mit Minor-Version ausgeliefert.
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "de.passderdeutschenberge"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            val store = signingValue("storeFile")
            if (store != null) {
                storeFile = file(store)
                storePassword = signingValue("storePassword")
                keyAlias = signingValue("keyAlias")
                keyPassword = signingValue("keyPassword")
                // minSdk 24 -> v1 (JAR-Signatur) ist nicht mehr noetig.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // Ohne hinterlegten Keystore faellt der Release-Build auf den
            // Debug-Key zurueck, damit lokale Builds nicht scheitern.
            signingConfig = if (signingValue("storeFile") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        // Befunde direkt ins Buildprotokoll, sonst stehen sie nur im Report.
        textReport = true
        htmlReport = true
        // Fehlende Uebersetzungen sollen den Build brechen, nicht stillschweigend
        // auf Deutsch zurueckfallen.
        error += setOf("MissingTranslation")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
