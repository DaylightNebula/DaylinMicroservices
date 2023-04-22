plugins {
    kotlin("jvm") version "1.8.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("org.jetbrains.compose") version "1.3.1"
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
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

    // compose
    implementation(compose.desktop.currentOs)
    implementation("br.com.devsrsouza.compose.icons:octicons:1.1.0")
}
tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveFileName.set("DaylinMicroservices-GUI.jar")
//        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to "daylightnebula.daylinmicroservices.gui.GUIKt"))
        }
    }
}