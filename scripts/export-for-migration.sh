#!/bin/bash

cd "$(dirname "$0")/.."

set -e

if [ -f .env ]; then
  set -a
  source .env
  set +a
else
  echo "❌ Файл .env не найден"
  exit 1
fi

DB_CONTAINER="video_stats_db"
BACKUP_DIR="backups"
BACKUP_FILE="${BACKUP_DIR}/backup_$(date +%F_%H-%M-%S).sql"

# Создаём папку для бэкапов, если её нет
mkdir -p "$BACKUP_DIR"

echo "📦 Создаём дамп базы данных..."
docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" "$DB_NAME" > "$BACKUP_FILE"

if [ ! -s "$BACKUP_FILE" ]; then
  echo "❌ Дамп пустой"
  rm -f "$BACKUP_FILE"
  exit 1
fi

echo "✅ Дамп базы создан: $BACKUP_FILE ($(du -h "$BACKUP_FILE" | cut -f1))"
echo ""
echo "📋 Инструкция по миграции БД на новый сервер:"
echo ""
echo "1️⃣  Скопируйте дамп на новый сервер:"
echo "    scp $BACKUP_FILE user@new-server:/path/to/project/backups/"
echo ""
echo "2️⃣  На новом сервере перейдите в папку с проектом:"
echo "    cd /path/to/project"
echo ""
echo "3️⃣  В корневой папке проекта выполните:"
echo "    ./scripts/restore-db.sh $BACKUP_FILE"