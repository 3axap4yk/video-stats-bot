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
EXPORT_DIR="migration_$(date +%F_%H-%M-%S)"

mkdir -p "$EXPORT_DIR"

echo "📦 Создаём бэкап базы..."
docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" "$DB_NAME" > "$EXPORT_DIR/backup.sql"

if [ ! -s "$EXPORT_DIR/backup.sql" ]; then
  echo "❌ Бэкап пустой"
  rm -rf "$EXPORT_DIR"
  exit 1
fi

echo "📁 Копируем файлы проекта..."
cp .env "$EXPORT_DIR/.env"
cp docker-compose.yml "$EXPORT_DIR/"
cp Dockerfile "$EXPORT_DIR/"
cp run.sh "$EXPORT_DIR/"
cp -r db "$EXPORT_DIR/" 2>/dev/null || true
cp -r scripts "$EXPORT_DIR/" 2>/dev/null || true

echo "🗜️ Архивируем..."
tar -czf "${EXPORT_DIR}.tar.gz" "$EXPORT_DIR"
rm -rf "$EXPORT_DIR"

echo ""
echo "✅ Миграционный пакет готов: ${EXPORT_DIR}.tar.gz"
echo ""
echo "Перенесите его на новый сервер:"
echo "  scp ${EXPORT_DIR}.tar.gz user@new-server:/home/user/"
echo ""
echo "На новом сервере выполните:"
echo "  tar -xzf ${EXPORT_DIR}.tar.gz"
echo "  cd ${EXPORT_DIR}"
echo "  ./run.sh"
echo "  ./scripts/restore-db.sh backup.sql"