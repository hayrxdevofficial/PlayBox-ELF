plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.playbox.elf"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.playbox.elf"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.1"
        externalNativeBuild { cmake { cppFlags += listOf("-std=c++17", "-O2") } }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    buildTypes { release { isMinifyEnabled = false } }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
}
