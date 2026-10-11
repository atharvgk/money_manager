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

rootProject.name = "MoneyManager"

// :core  = pure Kotlin (no Android) — SMS parsing, categorisation, analysis. Unit-tested on the JVM.
// :app   = the Android app (Compose UI, Room, SMS receiver, WorkManager).
include(":core", ":app")
