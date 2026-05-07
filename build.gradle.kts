plugins {
    java
    application
}

group = "com.project"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {

    // PostgreSQL драйвер
    implementation("org.postgresql:postgresql:42.7.3")

    // HikariCP - пул соединений с БД
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Caffeine - кэш
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // Dotenv для .env файлов
    implementation("io.github.cdimascio:dotenv-java:3.0.0")

    // Telegram Bot API
    implementation("com.github.pengrad:java-telegram-bot-api:7.4.0")

    // Jackson для JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")

    // Логи
    implementation("org.slf4j:slf4j-simple:2.0.16")

    // JUnit
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Mockito
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.10.0")
}

application {
    mainClass.set("com.project.App")

    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8"
    )
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.jar {

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Main-Class" to "com.project.App"
        )
    }

    from(
        configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        }
    )
}

tasks.test {
    useJUnitPlatform()
}

// ========== Кодировка ==========

tasks.compileJava {
    options.encoding = "UTF-8"
}

tasks.compileTestJava {
    options.encoding = "UTF-8"
}

tasks.javadoc {
    options.encoding = "UTF-8"
}

tasks.withType<JavaExec> {
    systemProperty("file.encoding", "UTF-8")
}

// ==================================