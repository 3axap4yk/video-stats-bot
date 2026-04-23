# Сборка Java-приложения в Docker-образ
# компиляция -> запуск
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Пока закомментировано, раскомментируем когда появится jar
# COPY target/video-stats-bot.jar app.jar

# Команда запуска (раскомментировать когда будет jar, сейчас в ямлике прописано command: ["sleep", "infinity"] в качестве "заглушки"
# CMD ["java", "-jar", "app.jar"]