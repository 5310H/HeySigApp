import java.util.Properties
import java.net.URI

plugins { id("com.android.application"); kotlin("android") }
apply(from = rootProject.file("gradle/app-signing.gradle"))
apply(from = rootProject.file("gradle/environment-model.gradle"))
apply(from = rootProject.file("gradle/masking-model.gradle"))
apply(from = rootProject.file("gradle/separation-model.gradle"))


val releasePublic = Properties().apply {
    rootProject.file("gradle/release-public.properties").inputStream().use { load(it) }
}
val privacyPolicyUrl = releasePublic.getProperty("privacyPolicyUrl", "").trim()
require(privacyPolicyUrl.isEmpty() || runCatching {
    val uri = URI(privacyPolicyUrl)
    uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.fragment == null &&
        privacyPolicyUrl.none { it == '\"' || it == '\\' || it.isWhitespace() }
}.getOrDefault(false)) { "Use a public HTTPS privacy policy URL" }

android {
    namespace = "com.heysig.watch"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.heysig"
        minSdk = 30
        targetSdk = 35
        versionCode = 13
        versionName = "2026.11.001"
        testInstrumentationRunner = "com.heysig.audio.NoiseAcceptance"
    }
    sourceSets.getByName("debug").java.srcDir("src/direct/kotlin")
    sourceSets.getByName("debug").java.srcDir("../audioAndroid/src/debug/kotlin")
    sourceSets.getByName("androidTest").java.srcDir("../audioAndroid/src/androidTest/kotlin")
    sourceSets.getByName("androidTest").assets.srcDir("../androidApp/src/androidTest/assets")
    sourceSets.getByName("androidTest").assets.srcDir("../shared/src/commonTest/fixtures/environment/scene-head/device")
    sourceSets.getByName("release").java.srcDir("src/direct/kotlin")
    sourceSets.getByName("main").java.srcDir("../audioAndroid/src/main/kotlin")
    sourceSets.getByName("main").assets.srcDir("../audioAndroid/src/main/assets")
    sourceSets.getByName("main").jniLibs.srcDir("../audioAndroid/src/main/jniLibs")
    defaultConfig {
        buildConfigField("String", "GIT_BUILD_ID", "\"${rootProject.extra["gitBuildId"]}\"")
        buildConfigField("String", "GIT_BUILD_DATE", "\"${rootProject.extra["gitBuildDate"]}\"")
    }
    buildFeatures { buildConfig = true }
    defaultConfig { buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"") }
    buildTypes {
        debug {
            // The paired debug apps must have the same package and signer.
            applicationIdSuffix = ".batteryprototype"
            signingConfig = signingConfigs.getByName("debug").apply {
                storeFile = rootProject.file("private/signing/androidApp-debug.keystore")
            }
        }
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
dependencies {
    implementation(project(":shared"))
    implementation("com.google.ai.edge.litert:litert:1.4.2")
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.wear:wear-ongoing:1.1.0")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
}
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { it.versionCode.set(2_000_012) }
    }
}
