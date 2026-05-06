#!/bin/bash

cd "$(dirname "$0")/.."

set -e

# Загружаем .env
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
else
  echo "❌ Файл .env не найден"
  exit 1
fi

DB_CONTAINER="video_stats_db"

if [ -z "$1" ]; then
  echo "❌ Укажи файл бэкапа"
  echo "Пример:"
  echo "./scripts/restore-db.sh backups/backup_2026-05-01.sql"
  exit 1
fi

BACKUP_FILE="$1"

if [ ! -f "$BACKUP_FILE" ]; then
  echo "❌ Файл не найден: $BACKUP_FILE"
  exit 1
fi

echo "⚠️ ВНИМАНИЕ: Текущая база будет перезаписана!"
read -p "Продолжить? (y/n): " confirm

if [ "$confirm" != "y" ]; then
  echo "❌ Отменено"
  exit 0
fi

echo "🧹 Очищаем БД..."

docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

echo "📥 Восстанавливаем из бэкапа..."

docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" < "$BACKUP_FILE"

echo "✅ БД успешно восстановлена"