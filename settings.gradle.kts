rootProject.name = "reconcile-saas"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
    // gradle/libs.versions.toml is auto-discovered as the default "libs" catalog
}

include(
    "shared",
    "tenant",
    "iam",
    "ingestion",
    "ledger",
    "reconciliation",
    "reporting",
    "notification",
    "app",
    "modulith-verification",
)
