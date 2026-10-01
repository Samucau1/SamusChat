pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral(); maven("https://jitpack.io") }
}
rootProject.name = "SamusChat"
include(":app")
// Optional media source for disposable emulator tests; never packaged into SamusChat.
if (providers.gradleProperty("callTestTone").getOrElse("false") == "true") include(":call-test-tone")
