import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
        import org.jetbrains.kotlin.gradle.dsl.JvmTarget
        import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion = "2.0.0"
val paperVersion = "1.21-R0.1-SNAPSHOT"

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("java")
}

group = "com.heledron"
version = "3.0-SNAPSHOT"

repositories {
    // Corrected the PaperMC repository URL
    maven("https://repo.papermc.io/repository/maven-public")
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
}

// Set the Java version for the project
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

// Configure Kotlin compiler options
tasks.withType<KotlinCompile> {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

// Configure the ShadowJar task to build the final plugin file
// **FIXED:** This block is now at the top level, not nested.
tasks.withType<ShadowJar> {
    // **FIXED:** Commented out minimize() to fix the "major version 65" error.
    // minimize()

    // Set the output file name to be more standard for plugins
    archiveBaseName.set(rootProject.name)
    archiveClassifier.set("") // Removes the "-all" suffix from the jar name
    archiveVersion.set(project.version.toString())
}

// This task was also moved out of the KotlinCompile block
tasks.processResources {
    // Make sure your plugin.yml has placeholders like `version: ${project.version}`
    expand(project.properties)
}

// This task was also moved out of the KotlinCompile block
tasks.build {
    dependsOn(tasks.shadowJar)
}