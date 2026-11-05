import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.alarmevaso"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.alarmevaso"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }

    buildTypes {
        release {
            // Assinado com a chave de teste gerada localmente (keystore debug).
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

val detectorAssets = layout.buildDirectory.dir("generated/toiletDetector")
val downloadDetector by tasks.registering {
    val model = detectorAssets.map { it.file("efficientdet_lite0.tflite") }
    outputs.file(model)
    doLast {
        val file = model.get().asFile
        file.parentFile.mkdirs()
        val url = URI("https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite").toURL()
        val temporary = file.resolveSibling(file.name + ".tmp")
        url.openStream().use { input -> temporary.outputStream().use { input.copyTo(it) } }
        check(temporary.length() > 1_000_000)
        temporary.copyTo(file, overwrite = true)
        temporary.delete()
    }
}

android.sourceSets["main"].assets.srcDir(detectorAssets)
android.androidResources.noCompress += "tflite"
tasks.named("preBuild").configure { dependsOn(downloadDetector) }
