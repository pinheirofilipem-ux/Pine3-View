plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pt.pine3.view"
    compileSdk = 34

    defaultConfig {
        applicationId = "pt.pine3.view.native"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-native"
    }

    buildTypes {
        release {
            // Sem minify/shrink: os ícones MDI são procurados por nome em tempo de execução.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug") // uso pessoal
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Sem dependências externas: só o SDK Android e a stdlib do Kotlin.
