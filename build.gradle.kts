plugins {
    id("java")
    id("application")
}

group = "com.project"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}


repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("io.github.cdimascio:dotenv-java:3.0.0")

    implementation("com.github.pengrad:java-telegram-bot-api:7.4.0")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("com.project.App")
}