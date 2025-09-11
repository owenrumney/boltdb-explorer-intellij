import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
    id("org.jetbrains.changelog") version "2.2.1"
}

group = property("pluginGroup")
version = property("pluginVersion")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    implementation("com.google.code.gson:gson:${property("gsonVersion")}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("kotlinxSerializationVersion")}")

    intellijPlatform {
        create(property("platformType").toString(), property("platformVersion").toString())
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add necessary plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = property("pluginSinceBuild").toString()

        }

        changeNotes = """
      Initial version
    """.trimIndent()
    }
}

changelog {
    groups.empty()
}

tasks {
    val javaVersion = project.property("javaVersion").toString()
    
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(JvmTarget.fromTarget(javaVersion))
    }

    publishPlugin {
        token = environment("PUBLISH_TOKEN")
    }
}
