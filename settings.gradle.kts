pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Only used when the sample is built against the published artifact (-PuseMavenLocal).
        mavenLocal()
    }
}

rootProject.name = "beacon"
include(":beacon")  // the library (AAR)
include(":sample")  // demo app
