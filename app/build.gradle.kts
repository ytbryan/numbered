import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/**
 * Release signing reads Gradle properties, kept out of the repo in ~/.gradle/gradle.properties or
 * passed as ORG_GRADLE_PROJECT_<name> environment variables. Without them, release builds are unsigned.
 */
fun signingProperty(name: String): String? = providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }

val releaseKeystore = signingProperty("numberedKeystore")

android {
    namespace = "com.numbered.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.numbered.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = requireNotNull(signingProperty("numberedKeystorePassword")) { "Set numberedKeystorePassword" }
                keyAlias = requireNotNull(signingProperty("numberedKeyAlias")) { "Set numberedKeyAlias" }
                keyPassword = requireNotNull(signingProperty("numberedKeyPassword")) { "Set numberedKeyPassword" }
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        localeFilters += listOf("en")
    }

    lint {
        // Warnings fail the build too, so nothing slips in quietly. Deliberate exceptions live in lint.xml.
        warningsAsErrors = true
        abortOnError = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // MigrationTestHelper reads the exported schemas from assets, and Robolectric only sees the
    // tested variant's merged assets, so debug builds carry them. Release builds never do.
    sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.glance.appwidget.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
