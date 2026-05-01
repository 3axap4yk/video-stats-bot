# =====================================================
# Этап 1: Сборка приложения (Gradle)
# =====================================================
FROM gradle:8.5-jdk17 AS build

WORKDIR /app

# Копируем файлы сборки
COPY build.gradle .
COPY settings.gradle* .
COPY gradlew .
COPY gradle/ gradle/

# Копируем исходный код
COPY src/ src/

# Даём права на выполнение
RUN chmod +x gradlew

# Собираем JAR
RUN ./gradlew build -x test --no-daemon

# =====================================================
# Этап 2: Запуск приложения
# =====================================================
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Устанавливаем wait-for-it для ожидания БД
RUN apk add --no-cache bash

# Копируем JAR из этапа сборки
COPY --from=build /app/build/libs/*.jar app.jar

# Копируем .env (если есть)
COPY .env .env

# Копируем скрипт ожидания БД
COPY wait-for-it.sh /wait-for-it.sh
RUN chmod +x /wait-for-it.sh

# Ждём БД и запускаем
CMD ["/wait-for-it.sh", "db:5432", "--", "java", "-jar", "app.jar"]