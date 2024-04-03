plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsCompose)
    //id("module.publication")
}

kotlin {
    androidTarget {
        //publishLibraryVariants("release")
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
    }

    jvm("desktop")

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val desktopMain by getting

        androidMain.dependencies {
            implementation(libs.androidx.compose.ui.tooling.preview)
        }

        iosMain.dependencies {
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(libs.kotlinx.collections.immutable)

            implementation(libs.napier)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.hym.compose.subsamplingimage"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    dependencies {
        debugImplementation(libs.androidx.compose.ui.tooling)
    }
}
