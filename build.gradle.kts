plugins {
    id("com.android.application") version "8.13.2" apply false
    kotlin("android") version "2.2.10" apply false
    kotlin("jvm") version "2.2.10" apply false
}

// Share the checkout revision across phone and watch build labels.
val gitRevision = providers.exec {
    workingDir(rootDir)
    commandLine("git", "rev-parse", "--short=7", "HEAD")
}.standardOutput.asText.get().trim()
require(gitRevision.matches(Regex("[0-9a-f]{7,40}"))) { "Cannot determine Git build ID" }
extra["gitBuildId"] = gitRevision
// The commit date stays consistent across phone/watch and rebuilds of this revision.
val gitCommitDate = providers.exec {
    workingDir(rootDir)
    commandLine("git", "show", "-s", "--format=%cs", "HEAD")
}.standardOutput.asText.get().trim()
extra["gitBuildDate"] = java.time.LocalDate.parse(gitCommitDate)
    .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US))

tasks.register("releaseCandidates") {
    group = "build"
    description = "Build signed validation candidates, not permission to publish."
    dependsOn(":shared:test", ":androidApp:lintRelease", ":wearApp:lintRelease",
        ":androidApp:bundleRelease", ":wearApp:bundleRelease")
}
