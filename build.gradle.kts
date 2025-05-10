import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.shadowJar

plugins {
    kotlin("jvm") version libs.versions.kotlin.get()
    alias(libs.plugins.shadow)
    alias(libs.plugins.serialization)
    alias(libs.plugins.userdev)
    alias(libs.plugins.runpaper)
}

group = "dev.munky"
version = "0.0.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    paperweight.paperDevBundle("1.21.5-R0.1-SNAPSHOT")
    annotationProcessor("org.apache.logging.log4j:log4j-core:2.24.3")

    compileOnly(libs.lands)
    compileOnly(libs.vault)
    compileOnly(libs.luckperms)

    implementation(libs.coroutines)
    implementation(libs.bundles.serialization)
    implementation(kotlin("reflect"))

    testImplementation(kotlin("test"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    shadowJar {
        dependsOn(processResources)
        from(sourceSets.main.get().output)
        archiveFileName = "Kozers.jar"
        destinationDirectory.set(rootDir)
    }
    test {
        useJUnitPlatform()
    }
    processResources {
        val resourceDir = sourceSets.main.get().output.resourcesDir!!
        println("Resources dir = $resourceDir")
        resourceDir.mkdirs()

        val plugin = File(resourceDir, "paper-plugin.yml")
        plugin.writeText("""
                name: Kozers
                version: '$version'
                api-version: '1.21'
                description: "The Kozers core plugin"
                main: dev.munky.kozers.KozersPlugin
                # loader: net.titanborn.core.TitanCoreLoader
                authors:
                  - "DevMunky"
            """.trimIndent())
    }
    runDevBundleServer {
        dependsOn(shadowJar)
        systemProperties["log4j2.configurationFile"] = "log4j2.xml"
        systemProperties["IKnowThereAreNoNMSBindingsForv1_21_5ButIWillProceedAnyway"] = "true"
        pluginJars.from(file("plugins/").listFiles())
        downloadPlugins {
            url("https://cdn.modrinth.com/data/FIlZB9L0/versions/c88ENMTx/Terra-bukkit-6.6.1-BETA%2B83bc2c902-shaded.jar")
        }
    }
}