rootProject.name = "Owlsplit"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google { mavenContent { includeGroupAndSubgroups("androidx"); includeGroupAndSubgroups("com.android"); includeGroupAndSubgroups("com.google") } }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google { mavenContent { includeGroupAndSubgroups("androidx"); includeGroupAndSubgroups("com.android"); includeGroupAndSubgroups("com.google") } }
        mavenCentral()
    }
}

include(":composeApp")

include(
    ":core:common",
    ":core:ui",
    ":core:security",
    ":core:network",
    ":core:datastore",
    ":core:logging",
    ":core:money",
    ":core:testing",
)

include(":data:models", ":data:local", ":data:remote", ":data:repository")

include(":domain:usecases")
