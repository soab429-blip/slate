plugins {
    id("fabric-loom") version "1.17-SNAPSHOT"
    id("ploceus") version "1.17-SNAPSHOT"
    `maven-publish`
}

val archivesBaseName = property("archives_base_name")!!.toString()
val modVersion = property("mod_version")!!.toString()
val minecraftVersion = property("minecraft_version")!!.toString()
val fabricLoaderVersion = property("fabric_loader_version")!!.toString()
val featherBuild = property("feather_build")!!.toString()
val oslVersion = property("osl_version")!!.toString()
val mavenGroup = property("maven_group")!!.toString()
val pylonVersion = property("pylon_version")!!.toString()

ploceus {
    setIntermediaryGeneration(2)
}

version = "${modVersion}+mc${minecraftVersion}"
group = mavenGroup

repositories {
    maven("https://maven.ornithemc.net/releases")
    maven("https://maven.cloverclient.com/releases")
}

loom {
    accessWidenerPath.set(file("src/main/resources/slate.accesswidener"))
}

dependencies {
    minecraft("com.mojang:minecraft:${minecraftVersion}")

    mappings(ploceus.featherMappings(featherBuild))

    modImplementation("net.fabricmc:fabric-loader:${fabricLoaderVersion}")

    ploceus.dependOsl(oslVersion)

    modCompileOnly("pl.tomgirl:pylon:${pylonVersion}")
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("fabric.mod.json") {
        expand("version" to version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 8
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_$archivesBaseName" }
    }
}
