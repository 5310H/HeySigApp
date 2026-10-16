plugins {
    id("com.android.application") version "8.13.2" apply false
    kotlin("android") version "2.2.10" apply false
    kotlin("jvm") version "2.2.10" apply false
}

tasks.register("releaseCandidates") {
    group = "build"
    description = "Build signed validation candidates, not permission to publish."
    dependsOn(":shared:test", ":androidApp:lintRelease", ":wearApp:lintRelease",
        ":androidApp:bundleRelease", ":wearApp:bundleRelease")
}
