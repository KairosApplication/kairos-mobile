plugins {
    alias(libs.plugins.android.application)
}

// Permite compilar sem credenciais; a tela explica a configuração pendente.
val isolatedTests = providers.gradleProperty("kairos.isolatedTests").orNull == "true"
if (file("google-services.json").exists() && !isolatedTests) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    buildFeatures { buildConfig = true }
    lint {
        baseline = file("lint-baseline.xml")
    }
    namespace = "com.example.kairos"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        buildConfigField("String", "SPRING_API_URL", "\"${providers.gradleProperty("kairos.springUrl").getOrElse("")}\"")
        buildConfigField("String", "CHAT_API_URL", "\"${providers.gradleProperty("kairos.chatUrl").getOrElse("")}\"")
        applicationId = "com.example.kairos"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (isolatedTests) testApplicationId = "com.example.kairos.customerflow.test"
    }

    buildTypes {
        debug {
            if (isolatedTests) applicationIdSuffix = ".integration"
            buildConfigField("String", "SPRING_API_URL", "\"${providers.gradleProperty("kairos.springUrl").getOrElse("http://127.0.0.1:8081")}\"")
            buildConfigField("String", "CHAT_API_URL", "\"${providers.gradleProperty("kairos.chatUrl").getOrElse("http://127.0.0.1:8000")}\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-ai")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    releaseImplementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.10.0")
    implementation("androidx.lifecycle:lifecycle-livedata-core:2.10.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
