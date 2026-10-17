plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.mispeech.hyperos.asr.example"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mispeech.hyperos.asr.example"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":library"))
}
