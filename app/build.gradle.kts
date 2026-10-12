plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "pt.tiago.blocknight"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "pt.tiago.blocknight"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "0.3.9"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Release signing is read from the developer's ~/.gradle/gradle.properties and never committed.
    // Without these properties (CI, forks) the release build is produced unsigned.
    val releaseStoreFile = providers.gradleProperty("blockNight.release.storeFile").orNull
    signingConfigs {
        if (releaseStoreFile != null) create("release") {
            storeFile = file(releaseStoreFile)
            storePassword = providers.gradleProperty("blockNight.release.storePassword").get()
            keyAlias = providers.gradleProperty("blockNight.release.keyAlias").get()
            keyPassword = providers.gradleProperty("blockNight.release.keyPassword").get()
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            // Keep the git revision out of the APK so release builds are reproducible from source.
            vcsInfo.include = false
        }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets["main"].assets.directories.add(layout.buildDirectory.dir("generated/blockNightAssets").get().asFile.path)
}
dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.datastore)
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
}

// O conteúdo validado vai incluído no APK (PLANO §8). Falha de validação ou de replay falha o build.
tasks.named("preBuild") { dependsOn(":leveltool:exportGameAssets") }
