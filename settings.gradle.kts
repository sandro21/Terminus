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

rootProject.name = "Terminus"
include(":shared", ":server", ":worker")

// Container builds only need the two JVM services. Local builds include the Android app.
if (!startParameter.projectProperties.containsKey("serverOnly")) {
    include(":app")
}
