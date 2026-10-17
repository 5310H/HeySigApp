import java.util.Properties
import java.net.URI

plugins { id("com.android.application"); kotlin("android") }
apply(from = rootProject.file("gradle/app-signing.gradle"))


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
    // Exercise the native watch style on isolated round-display contexts; never ship it in the phone app.
    sourceSets.getByName("androidTest").java.srcDir("../wearApp/src/main/kotlin")
    defaultConfig {
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")
    }
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
dependencies {
    implementation(project(":shared"))
    implementation("androidx.core:core:1.17.0")
    "releaseImplementation"("com.google.android.gms:play-services-wearable:20.0.1")
}
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { it.versionCode.set(1_000_009) }
    }
}
