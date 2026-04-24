# Сборка Java-приложения в Docker-образ
# компиляция -> запуск
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY .env .env

COPY build/libs/*.jar app.jar

CMD ["java", "-jar", "app.jar"]