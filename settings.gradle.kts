pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "docs-office"

include(":app")

include(":core:model")
include(":core:common")
include(":core:database")
include(":core:datastore")
include(":core:files")
include(":core:designsystem")
include(":core:crypto")

include(":renderer:api")
include(":renderer:pdf")
include(":renderer:text")
include(":renderer:image")
include(":renderer:ooxml")

include(":core:billing")
include(":core:pdftools")

include(":feature:onboarding")
include(":feature:home")
include(":feature:files")
include(":feature:search")
include(":feature:viewer")
include(":feature:scan")
include(":feature:vault")
include(":feature:settings")
