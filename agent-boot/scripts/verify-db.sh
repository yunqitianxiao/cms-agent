#!/usr/bin/env bash
# 在临时 MariaDB 实例里跑 schema.sql + data.sql，不碰本机已有的 cms_db。
# 用法：scripts/verify-db.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d /tmp/cms-agent-db.XXXXXX)"
SOCK="$TMP/mysql.sock"
PORT="${VERIFY_DB_PORT:-33071}"

cleanup() {
    if [ -f "$TMP/mysql.pid" ]; then
        kill "$(cat "$TMP/mysql.pid")" 2>/dev/null || true
        for _ in $(seq 1 20); do
            [ -S "$SOCK" ] || break
            sleep 0.2
        done
    fi
    rm -rf "$TMP"
}
trap cleanup EXIT

mkdir -p "$TMP/data"
mariadb-install-db --no-defaults --datadir="$TMP/data" \
    --auth-root-authentication-method=normal >"$TMP/install.log" 2>&1

mariadbd --no-defaults --datadir="$TMP/data" --socket="$SOCK" \
    --port="$PORT" --bind-address=127.0.0.1 --pid-file="$TMP/mysql.pid" \
    --skip-networking=0 >"$TMP/server.log" 2>&1 &

for _ in $(seq 1 50); do
    [ -S "$SOCK" ] && mariadb --socket="$SOCK" -u root -e "SELECT 1" >/dev/null 2>&1 && break
    sleep 0.2
done

mariadb --socket="$SOCK" -u root -e "CREATE DATABASE cms_verify CHARACTER SET utf8mb4"
mariadb --socket="$SOCK" -u root cms_verify < "$ROOT/src/main/resources/sql/schema.sql"
mariadb --socket="$SOCK" -u root cms_verify < "$ROOT/src/main/resources/sql/data.sql"

echo "表数量："
mariadb --socket="$SOCK" -u root -N -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='cms_verify'"
echo "各表行数："
mariadb --socket="$SOCK" -u root -N -e "
    SELECT 'sys_user', COUNT(*) FROM cms_verify.sys_user UNION ALL
    SELECT 'sys_role', COUNT(*) FROM cms_verify.sys_role UNION ALL
    SELECT 'sys_permission', COUNT(*) FROM cms_verify.sys_permission UNION ALL
    SELECT 'cms_category', COUNT(*) FROM cms_verify.cms_category UNION ALL
    SELECT 'cms_article', COUNT(*) FROM cms_verify.cms_article UNION ALL
    SELECT 'sys_log', COUNT(*) FROM cms_verify.sys_log UNION ALL
    SELECT 'sys_user_role', COUNT(*) FROM cms_verify.sys_user_role UNION ALL
    SELECT 'sys_role_permission', COUNT(*) FROM cms_verify.sys_role_permission"

echo "外键数量："
mariadb --socket="$SOCK" -u root -N -e \
    "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema='cms_verify'"

echo "OK"
