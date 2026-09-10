plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.secrets.gradle.plugin)
}

val stableKeystorePath = System.getenv("GODSEYE_KEYSTORE_PATH")
val stableKeystorePassword = System.getenv("GODSEYE_KEYSTORE_PASSWORD")
val stableKeyAlias = System.getenv("GODSEYE_KEY_ALIAS")
val stableKeyPassword = System.getenv("GODSEYE_KEY_PASSWORD")

val stableSigningAvailable = listOf(
    stableKeystorePath,
    stableKeystorePassword,
    stableKeyAlias,
    stableKeyPassword
).all { !it.isNullOrBlank() }

val ciVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.mindlizzard.godseye"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.mindlizzard.godseye"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = ciVersionCode
        versionName = "0.2.$ciVersionCode-native"
    }

    signingConfigs {
        if (stableSigningAvailable) {
            create("stableDebug") {
                storeFile = rootProject.file(stableKeystorePath!!)
                storePassword = stableKeystorePassword
                keyAlias = stableKeyAlias
                keyPassword = stableKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (stableSigningAvailable) {
                signingConfig = signingConfigs.getByName("stableDebug")
            }
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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
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
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.play.services.maps3d)
}

secrets {
    val secretsFile = rootProject.file("secrets.properties")
    if (secretsFile.exists()) {
        propertiesFileName = "secrets.properties"
    }
    defaultPropertiesFileName = "local.defaults.properties"
}
