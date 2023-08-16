import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    kotlin("jvm") version "1.8.21"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    // json
    implementation("org.json:json:20230227")

    // ktor server
    implementation("io.ktor:ktor-server-core:2.2.4")
    implementation("io.ktor:ktor-server-netty:2.2.4")

    // ktor client
    implementation("io.ktor:ktor-client-core:2.2.4")
    implementation("io.ktor:ktor-client-cio:2.2.4")
    implementation("io.ktor:ktor-client-logging:2.2.4")

    // logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.6")

    // other modules
    implementation(project(":DaylinMicroservices-Serializables"))
    implementation(project(":DaylinMicroservices-Core"))
    implementation("com.github.DaylightNebula:DaylinMicroservices-Redis:0.1.1")

    // tests
    implementation(kotlin("test"))
}

tasks.getByName<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveFileName.set("DaylinMicroservices-Core.jar")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}

tasks.jar {
    manifest.attributes["Main-Class"] = "daylightnebula.daylinmicroservices.register.RegisterKt"
}

task<Exec>("Docker Build") {
    dependsOn("shadowJar")
    commandLine("docker build . -t daylinmicroservices/register".split(" "))
}