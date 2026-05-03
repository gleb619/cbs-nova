pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "cbs-nove"

include("backend")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
    // The `libs` version catalog is auto-detected from gradle/libs.versions.toml
    // (Gradle 9 standard location — no explicit `from()` call needed).
}
