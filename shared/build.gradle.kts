plugins { kotlin("jvm") }
java { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }

kotlin {
    jvmToolchain(17)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) }
    sourceSets {
        main {
            kotlin.srcDirs("src/commonMain/kotlin", "../research/acoustic/kotlin")
            kotlin.include("com/heysig/**/*.kt", "ProtoRequest.kt")
        }
        test { kotlin.srcDir("src/commonTest/kotlin") }
    }
}
dependencies { testImplementation("junit:junit:4.13.2") }
