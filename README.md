# Video Stats Bot

[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/projects/jdk/17/)
[![Docker](https://img.shields.io/badge/Docker-✓-blue)](https://www.docker.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-blue)](https://www.postgresql.org/)
[![Telegram](https://img.shields.io/badge/Telegram-Bot-blue)](https://core.telegram.org/bots/api)
[![YouTube](https://img.shields.io/badge/YouTube-API%20v3-red)](https://developers.google.com/youtube/v3)

**Video Stats Bot** — это сервис для автоматического сбора и агрегации статистики просмотров видео с платформ YouTube и ВКонтакте (ВК видео) через удобный интерфейс в Telegram.
## Как это работает?

```
👤 Пользователь
│
│ /add https://youtu.be/...
▼
🤖 Telegram Bot
│
│ Определяет платформу (YouTube / VK)
▼
🔍 API платформы
│
│ Возвращает необходимые данные
▼
🗄️ PostgreSQL
│
│ Сохраняет и считает
▼
📊 Статистика
│
│ Список видео + сумма просмотров
▼
👤 Пользователь
```

> **Целевая аудитория:** Владельцы контента, блогеры, SMM-специалисты и digital-агентства, которым нужно в одном месте отслеживать динамику и статистику просмотров без ручного захода на каждую платформу.

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
- **Автоматическое определение платформы** (YouTube \ VK Video).
- **Получение и сохранение** актуального количества просмотров через официальные API.
- **Вывод списка** всех добавленных видео с текущей статистикой.
- **Агрегация данных**: общее количество видео и суммарные просмотры.
- **Обновление статистики** по требованию (инлайн-кнопка).
- **Обработка ошибок** при недоступности платформ (сохранение последних данных + понятный статус).
- **Полная контейнеризация** (Docker Compose) для простоты развертывания.

## 🛠️ Технологический стек

| Компонент | Технология |
|-----------|--------|
| **Язык бэкенда** | Java 17 |
| **База данных** | PostgreSQL 17 (Alpine) |
| **Интерфейс** | Telegram Bot API |
| **Внешние API** | YouTube Data API v3 |
| **Контейнеризация** | Docker + Docker Compose |
| **Сборщик** | Gradle |

## 📋 Требования для развертывания

Для запуска **собственного экземпляра** бота вам понадобится:

### Сервер
- Linux (Ubuntu 20.04+ рекомендуется)
- Docker Engine 20.10+
- Docker Compose 1.29+

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

./run

Скрипт `run` автоматически:
- собирает проект через Gradle
- пересобирает Docker-образы
- поднимает PostgreSQL и backend через docker-compose
- выводит логи бота

### Краткая проверка работоспособности:

1. Проверьте запуск контейнеров:

docker ps

Вы должны увидеть контейнеры video_stats_bot и video_stats_db в статусе Up.

2. Проверьте логи:

docker-compose logs -f

3. Найдите вашего бота в Telegram по имени (указанному в .env) и отправьте команду /start.

## ⚙️ Конфигурация (.env)

Все настройки проекта находятся в файле .env. Ниже перечислены все переменные, которые необходимо заполнить.

### База данных

DB_HOST=db
DB_PORT=5432
DB_NAME=your_DB-name_here
DB_USER=your_username_here
DB_PASSWORD=your_password_here

### YouTube API

YOUTUBE_API_KEY=your_youtube_api_key_here

### Telegram Bot

TELEGRAM_BOT_TOKEN=your_bot_token_here

> Внимание: Файл .env содержит секретные данные. Никогда не добавляйте его в репозиторий — он уже находится в .gitignore.


## 📂 Структура проекта

```
video-stats-bot/
├── src/main/java/com/project/
│ ├── bot/ # Telegram-бот (команды, кнопки, обработчики)
│ ├── config/ # Конфигурация приложения
│ ├── model/ # Модели данных
│ ├── repository/ # Работа с БД (JDBC)
│ └── service/ # Бизнес-логика и работа с API (YouTube, ВК)
├── src/test/ # Тесты
├── db # SQL схема и инициализация БД
│ └── init.sql
├── Dockerfile # Инструкция сборки Docker-образа
├── docker-compose.yml # Запуск PostgreSQL и бота
├── .env.example # Шаблон переменных окружения
├── build.gradle # Конфигурация сборки Gradle
├── gradlew # Gradle Wrapper
├── run # Скрипт запуска проекта
└── README.md
```

## 💻 Разработка

### Локальный запуск (без Docker)

1. Установите Java 17.
2. Настройте переменные окружения.
Скопируйте и заполните `.env`:
   ```bash
   cp .env.example .env
   nano .env
Подробное описание всех переменных — в разделе «Конфигурация (.env)».
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

Используйте DBeaver или любой другой SQL-клиент. SQL-схема для создания таблиц находится в корневой директории проекта: db/init.sql