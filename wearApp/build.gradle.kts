plugins { id("com.android.application"); kotlin("android") }
apply(from = rootProject.file("gradle/app-signing.gradle"))

android {
    namespace = "com.heysig.watch"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.heysig"
        minSdk = 30
        targetSdk = 35
        versionCode = 3
        versionName = "0.3"
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        debug { applicationIdSuffix = ".watchdev" }
        release {
            applicationIdSuffix = ".app"
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    sourceSets.getByName("main").java.srcDir("../shared/src/commonMain/kotlin/com/heysig/control")
    sourceSets.getByName("main").java.include("VoicePreference.kt", "com/heysig/**/*.kt")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    lint { abortOnError = true; checkReleaseBuilds = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) } }
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { it.versionCode.set(2_000_003) }
    }
}
