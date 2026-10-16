plugins { id("com.android.application"); kotlin("android") }
apply(from = rootProject.file("gradle/app-signing.gradle"))

android {
    namespace = "com.heysig.app"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.heysig"
        minSdk = 29
        targetSdk = 36
        versionCode = 9
        versionName = "0.9"
        testInstrumentationRunner = "com.heysig.app.Api36Smoke"
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        debug { applicationIdSuffix = ".batteryprototype" }
        release {
            applicationIdSuffix = ".app"
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    lint { abortOnError = true; checkReleaseBuilds = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) } }
dependencies { implementation(project(":shared")) }
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { it.versionCode.set(1_000_009) }
    }
}
