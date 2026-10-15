pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "Ornithe Releases"
            url = uri("https://maven.ornithemc.net/releases")
        }
        maven {
            name = "Ornithe Snapshots"
            url = uri("https://maven.ornithemc.net/snapshots")
        }
        mavenLocal()
        gradlePluginPortal()
    }
}

rootProject.name = "slate"

if (file("slate-vk").exists()) {
    include("slate-vk")
}