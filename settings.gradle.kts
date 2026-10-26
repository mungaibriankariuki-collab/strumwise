pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Strumwise"

// Pure Kotlin modules: no Android SDK needed, fully unit tested.
include(":core-music")
include(":core-game")

// Set STRUMWISE_JVM_ONLY=1 to work on the pure Kotlin modules on a machine
// without the Android SDK (e.g. running core-music / core-game tests on a phone).
if (System.getenv("STRUMWISE_JVM_ONLY") == null) {
    include(":core-audio")
    include(":core-data")
    include(":app")
}
