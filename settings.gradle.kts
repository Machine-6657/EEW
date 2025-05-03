pluginManagement {
    repositories {
        // Add Aliyun mirrors first
        maven { url=uri( "https://maven.aliyun.com/repository/central") }
        maven { url=uri( "https://maven.aliyun.com/repository/public") }
        maven { url=uri( "https://maven.aliyun.com/repository/google") }
        maven { url=uri( "https://maven.aliyun.com/repository/gradle-plugin") }
        // Keep original repositories as fallbacks
//        maven { url = uri("https://maven.aliyun.com/repository/central") } // Note: Commented out redundant/previous entry if any
//        maven { url = uri("https://maven.aliyun.com/repository/jcenter") } // Note: JCenter is deprecated, removing or keeping commented.
//        maven { url = uri("https://maven.aliyun.com/repository/google") } // Note: Commented out redundant/previous entry if any
//        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") } // Note: Commented out redundant/previous entry if any
//        maven { url = uri("https://maven.aliyun.com/repository/public") } // Note: Commented out redundant/previous entry if any

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
        // Add Aliyun mirrors first
        maven { url=uri( "https://maven.aliyun.com/repository/central") }
        maven { url=uri( "https://maven.aliyun.com/repository/public") }
        maven { url=uri( "https://maven.aliyun.com/repository/google") }
        maven { url=uri( "https://maven.aliyun.com/repository/gradle-plugin") }
        // Keep original repositories as fallbacks
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
//        maven { url = uri("https://maven.aliyun.com/repository/public") } // Note: Commented out redundant/previous entry if any
//        maven { url = uri("https://maven.aliyun.com/repository/google") } // Note: Commented out redundant/previous entry if any
//        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") } // Note: Commented out redundant/previous entry if any
//        maven { url = uri("https://repo.rdc.aliyun.com/repository/129757-release-Lpw4Wn/") } // Note: Commented out previous specific entry
    }
}

rootProject.name = "EEWapp"
include(":app")
