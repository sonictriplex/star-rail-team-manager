import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Signierung: Zugangsdaten stehen in keystore.properties (nicht im Git, Vorlage: keystore.properties.example)
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "de.sonictriplex.starrailteams"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.sonictriplex.starrailteams"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.4.1"

        // Der Server, mit dem die App spricht (die Web-Version, siehe ../web); auf der Anmeldeseite damit vorbelegt, aber änderbar.
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"https://star-rail.media-matrix.de\"")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Ohne keystore.properties entsteht eine unsignierte APK
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        // Nur zum Testen: wie "release" (R8, ohne Debug), aber mit Debug-Schlüssel und HTTP zum
        // lokalen Entwicklungsserver (10.0.2.2). Läuft parallel zur echten App (anderer Paketname).
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            matchingFallbacks += "release"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // "staging" erlaubt Klartext-HTTP wie "debug" und nutzt dafür dieselbe Konfiguration
    sourceSets["staging"].res.directories.add("src/debug/res")
    sourceSets["staging"].manifest.srcFile("src/debug/AndroidManifest.xml")

    // Gemeinsame Daten des Monorepos (Bilder, Schrift, Stammdaten) direkt als Assets einbinden.
    // Unter assets/ liegen sie dann wie im Repo: assets/images/<typ>/<name>.png, stammdaten.json
    sourceSets["main"].assets.directories.add("../../shared")
    // Spezifikation und Testdaten gehören nicht in die APK
    androidResources.ignoreAssetsPatterns.addAll(listOf("!testdata", "!*.md", "!*.schema.json"))

    testOptions {
        unitTests.all {
            // Tests lesen Referenzdateien aus shared/ (relativ zum Modulordner)
            it.systemProperty("shared.dir", file("../../shared").absolutePath)
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
