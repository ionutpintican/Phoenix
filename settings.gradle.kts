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
        // NewPipeExtractor (YouTube playlist/stream extraction) is published on JitPack.
        // Scoped to its group so nothing else silently resolves from here.
        maven {
            url = uri("https://jitpack.io")
            // NewPipeExtractor is multi-module on JitPack: the root resolves submodules under
            // com.github.TeamNewPipe.NewPipeExtractor (extractor, timeago-parser, …), so match the
            // whole prefix, not just the exact group.
            content { includeGroupByRegex("com\\.github\\.TeamNewPipe.*") }
        }
    }
}

rootProject.name = "Phoenix"
include(":app")
