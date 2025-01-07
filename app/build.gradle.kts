plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp")
}

configurations.all {
    exclude(group = "org.jetbrains", module = "annotations-java5")
}

android {
    namespace = "com.example.cpplearner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.cpplearner"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.exifinterface)
    ksp(libs.androidx.room.compiler)

    implementation("io.noties.markwon:core:4.6.2") {
        exclude(group = "com.atlassian.commonmark", module = "commonmark") // Exclude directly in markwon
    }
    implementation(libs.syntax.highlight) {
        exclude(group = "com.atlassian.commonmark", module = "commonmark")
    }
    implementation(libs.ext.tables) {
        exclude(group = "com.atlassian.commonmark", module = "commonmark")
    }
    implementation(libs.ext.strikethrough) {
        exclude(group = "com.atlassian.commonmark", module = "commonmark")
    }
    implementation(libs.linkify) {
        exclude(group = "com.atlassian.commonmark", module = "commonmark")
    }
                                   //DO NOT FUCKING UPDATE THIS
    implementation("com.atlassian.commonmark:commonmark:0.13.0")

    // For text extraction
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("org.apache.commons:commons-compress:1.23.0")
    implementation("org.apache.commons:commons-collections4:4.4")

    implementation(libs.glide)

    implementation(libs.androidx.security.crypto.v110alpha03)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
//    implementation(libs.generativeai)
    implementation(libs.generativeai)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}