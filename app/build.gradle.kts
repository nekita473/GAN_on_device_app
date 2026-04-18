import com.android.build.api.dsl.Packaging

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.myganapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myganapp"
        minSdk = 30
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"

        android {
            defaultConfig {
                ndk {
                    abiFilters.add("arm64-v8a")
                }
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        mlModelBinding = true
        viewBinding = true
    }
    packaging {
        jniLibs {
            pickFirsts.add("lib/arm64-v8a/libc++_shared.so")
            pickFirsts.add("lib/arm64-v8a/libhta.so")
            pickFirsts.add("lib/arm64-v8a/libhta_hexagon_runtime_snpe.so")
            pickFirsts.add("lib/arm64-v8a/libSNPE.so")
            keepDebugSymbols.add("*/arm64-v8a/libSnpe*.so")
            useLegacyPackaging = true
            keepDebugSymbols.add("*/arm64-v8a/*.so")
            keepDebugSymbols.add("*/armeabi-v7a/*.so")
            keepDebugSymbols.add("*/x86_64/*.so")
            keepDebugSymbols.add("*/x86/*.so")
        }
        //pickFirst("lib/arm64-v8a/libc++_shared.so")
        //pickFirst("lib/arm64-v8a/libhta.so")
        //pickFirst("lib/arm64-v8a/libhta_hexagon_runtime_snpe.so")
        //pickFirst("lib/arm64-v8a/libSNPE.so")
        //doNotStrip("*/arm64-v8a/libSnpe*.so")
        //jniLibs {
        //    useLegacyPackaging = true
        //}
        //doNotStrip("*/arm64-v8a/*.so")
        //doNotStrip("*/armeabi-v7a/*.so")
        //doNotStrip("*/x86_64/*.so")
        //doNotStrip("*/x86/*.so")
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(files("libs/snpe-release.aar"))
}