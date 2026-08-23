import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Učitaj podatke za potpisivanje iz keystore.properties (fajl NIKAD ne ide na
// GitHub - naveden je u .gitignore). Ako fajl ne postoji (npr. na tuđem
// računaru bez keystore-a), release build se jednostavno neće potpisati
// Ultra ključem - i dalje radi assembleDebug/installDebug normalno.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasSigningConfig = keystorePropertiesFile.exists()
if (hasSigningConfig) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.ultra.keyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ultra.keyboard"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.0.2-beta"
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Preimenuj izlazni APK iz podrazumevanog "app-release.apk" u nešto
    // prepoznatljivo, npr. "UltraKeyboard-1.1.apk"
    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "UltraKeyboard-${versionName}.apk"
        }
    }
}

// Preimenuj i izlazni AAB (za bundleRelease) iz podrazumevanog "app-release.aab"
// u "UltraKeyboard-1.1.aab" - AGP ovo ne nudi direktno kao za APK gore, pa se
// posle bundleRelease-a napravi kopija sa lepim imenom (original ostaje i on,
// za svaki slučaj).
tasks.register("renameReleaseBundle") {
    doLast {
        val bundleDir = layout.buildDirectory.dir("outputs/bundle/release").get().asFile
        val original = File(bundleDir, "app-release.aab")
        val renamed = File(bundleDir, "UltraKeyboard-${android.defaultConfig.versionName}.aab")
        if (original.exists()) {
            original.copyTo(renamed, overwrite = true)
        }
    }
}
tasks.matching { it.name == "bundleRelease" }.configureEach {
    finalizedBy("renameReleaseBundle")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
}
