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

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }

    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    // PostgreSQL
    implementation("org.postgresql:postgresql:42.7.3")

    // HikariCP
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Caffeine
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // Dotenv
    implementation("io.github.cdimascio:dotenv-java:3.0.0")

    // Telegram Bot API
    implementation("com.github.pengrad:java-telegram-bot-api:7.4.0")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")

    // SLF4J
    implementation("org.slf4j:slf4j-simple:2.0.16")

    // JUnit
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Mockito
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.10.0")
}

application {
    mainClass.set("com.project.App")
}

tasks.test {
    useJUnitPlatform()
}

// UTF-8
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

tasks.withType<JavaExec> {
    systemProperty("file.encoding", "UTF-8")
}

application {
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}