plugins {
    id("java")
    kotlin("jvm") version "1.8.21"
}

repositories {
    mavenCentral()
}

dependencies {
//    implementation(kotlin("stdlib-jvm"))
    implementation("org.json:json:20230227")
    testImplementation(kotlin("test"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}