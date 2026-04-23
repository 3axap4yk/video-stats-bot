# Video Stats Bot

[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/projects/jdk/17/)
[![Docker](https://img.shields.io/badge/Docker-✓-blue)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-green)](./LICENSE)

**Video Stats Bot** — это сервис для автоматического сбора и агрегации статистики просмотров видео с разных платформ через удобный интерфейс в Telegram.

> **Целевая аудитория:** Владельцы контента, блогеры, SMM-специалисты и digital-агентства, которым нужно в одном месте отслеживать динамику просмотров без ручного захода на каждую платформу.

## 📋 Содержание
- [Возможности](#-возможности)
- [Технологический стек](#-технологический-стек)
- [Требования для развертывания](#-требования-для-развертывания)
- [Быстрый старт (Self-Hosted)](#-быстрый-старт-self-hosted)
- [Конфигурация (.env)](#-конфигурация-env)
- [Структура проекта](#-структура-проекта)
- [Разработка](#-разработка)

## 🚀 Возможности

- **Приём ссылок на видео** через Telegram-бота.
- **Автоматическое определение платформы** (YouTube, VK Video, RuTube, Дзен).
- **Получение и сохранение** актуального количества просмотров через официальные API.
- **Вывод списка** всех добавленных видео с текущей статистикой.
- **Агрегация данных**: общее количество видео и суммарные просмотры.
- **Обновление статистики** по требованию (инлайн-кнопка).
- **Обработка ошибок** при недоступности платформ (сохранение последних данных + понятный статус).
- **Полная контейнеризация** (Docker Compose) для простоты развертывания.

## 🛠️ Технологический стек

| Компонент | Технология |
|-----------|------------|
| **Язык бэкенда** | Java 17 |
| **База данных** | PostgreSQL 17 (Alpine) |
| **Интерфейс** | Telegram Bot API |
| **Внешние API** | YouTube Data API v3 |
| **Контейнеризация** | Docker + Docker Compose |
| **Сборщик** | Maven / Gradle |

## 📋 Требования для развертывания

Для запуска **собственного экземпляра** бота вам понадобится:

### Сервер
- Linux (Ubuntu 20.04+ рекомендуется)
- Docker Engine 20.10+
- Docker Compose 1.29+
- Минимум 1 ГБ RAM, 10 ГБ свободного места на диске

### Учётные записи и ключи
- **Telegram Bot Token** — получить у [@BotFather](https://t.me/BotFather) (создать бота и скопировать токен).
- **YouTube Data API Key** — получить в [Google Cloud Console](https://console.cloud.google.com/apis/credentials) (включить YouTube Data API v3 и создать API-ключ).
- **PostgreSQL** (опционально, если не используете Docker-образ) — база данных.

## 🚀 Быстрый старт (Self-Hosted)

Следуйте этой инструкции, чтобы развернуть сервис на вашем сервере.

### Шаг 1: Клонировать репозиторий

git clone https://github.com/3axap4yk/video-stats-bot.git
cd video-stats-bot

### Шаг 2: Настроить переменные окружения

Создайте файл .env из шаблона и заполните его реальными значениями:

cp .env.example .env
nano .env

Подробное описание всех переменных — в разделе «Конфигурация (.env)».

### Шаг 3: Запустить сервис

docker-compose up -d --build

### Шаг 4: Проверить работу

1. Убедитесь, что контейнеры запущены:

   docker ps

   Вы должны увидеть контейнер video_stats_backend в статусе Up.

2. Проверьте логи:

   docker-compose logs -f

3. Найдите вашего бота в Telegram по имени (указанному в .env) и отправьте команду /start.

### Шаг 5: Обновить на новую версию

Когда выйдет обновление, выполните команды ниже, чтобы получить последнюю версию и перезапустить сервис:

git pull origin main
docker-compose down
docker-compose up -d --build


## ⚙️ Конфигурация (.env)

Все настройки проекта находятся в файле .env. Ниже перечислены все переменные, которые необходимо заполнить.

### База данных

DB_HOST=host.docker.internal
DB_PORT=5432
DB_NAME=hackaton_db
DB_USER=admin
DB_PASSWORD=your_password_here

### YouTube API

YOUTUBE_API_KEY=your_youtube_api_key_here

### Telegram Bot

TELEGRAM_BOT_TOKEN=your_bot_token_here
TELEGRAM_BOT_USERNAME=your_bot_username_here

> Внимание: Файл .env содержит секретные данные. Никогда не добавляйте его в репозиторий — он уже находится в .gitignore.


## 📂 Структура проекта

```
video-stats-bot/
├── src/main/java/com/project/
│ ├── bot/ # Логика Telegram-бота
│ ├── config/ # Конфигурация приложения
│ ├── model/ # Сущности (Entity)
│ ├── repository/ # Слой доступа к данным (JDBC)
│ └── service/ # Бизнес-логика и клиенты API
├── src/main/resources/
│ └── schema.sql # SQL-схема базы данных
├── src/test/ # Тесты
├── Dockerfile # Инструкция сборки Docker-образа
├── docker-compose.yml # Оркестрация контейнеров
├── .env.example # Шаблон переменных окружения
├── build.gradle # Конфигурация сборки Gradle
├── gradlew # Gradle Wrapper
└── README.md # Вы здесь
```

## 💻 Разработка

### Локальный запуск (без Docker)

1. Установите Java 17.
2. Настройте переменные окружения (или временный `.env` файл).
3. Выполните:

# Linux / macOS
./gradlew run

# Windows
gradlew.bat run


### Запуск тестов

# Linux / macOS
./gradlew test

# Windows
gradlew.bat test


### Сборка jar-файла

# Linux / macOS
./gradlew jar

# Windows
gradlew.bat jar

Скомпилированный jar будет находиться в папке build/libs/.


### Подключение к базе данных для отладки

Используйте DBeaver или любой другой SQL-клиент. SQL-схема для создания таблиц находится в файле src/main/resources/schema.sql.