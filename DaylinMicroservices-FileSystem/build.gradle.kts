plugins {
    kotlin("jvm") version "1.8.20-RC2"
    id("com.github.johnrengelman.shadow") version "4.0.4"
}

repositories {
    mavenCentral()
}

dependencies {
    // microservices
    implementation(project(":DaylinMicroservices-Core"))
    implementation("com.orbitz.consul:consul-client:1.5.3")

    // json
    implementation("org.json:json:20230227")

    // logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.6")

    // test stuff
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveBaseName.set("FileSystemService")
//        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to "daylightnebula.daylinmicroservices.filesystem.FileSystemService"))
        }
    }
}