pluginManagement {
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
        // Pleos Connect SDK (Vehicle, NaviHelper, FusedLocation, TextToSpeech, SpeechToText)
        maven {
            url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")
        }
    }
}

rootProject.name = "PleosEvApp"
include(":app")
// Charging advisor: DTOs/geo/scoring shared by the app and the relay server, and the server itself.
include(":shared", ":server")
// Vehicle helpers shared by :app and :drivecoach (Vehicle SDK error codes, CarUxRestrictions monitor).
include(":core-vehicle")
// Driving-habit coach app (habit reports + parking practice), see docs/DRIVECOACH.md.
include(":drivecoach")
// Drive coach "폰으로 보기" share-link server (keeps only encrypted copies), see docs/DRIVECOACH.md.
include(":share-server")
