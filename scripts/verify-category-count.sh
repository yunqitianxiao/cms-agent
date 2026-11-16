#!/usr/bin/env bash
# D7 判据：在临时 MariaDB 里导入 schema+data，然后跑 CategoryCountDbTest，
# 断言分类树上的文章数与库里的 COUNT 对得上。不碰本机已有的 cms_db。
# 用法：scripts/verify-category-count.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d /tmp/cms-agent-cat.XXXXXX)"
SOCK="$TMP/mysql.sock"
PORT="${VERIFY_CAT_PORT:-33072}"

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

JDBC_URL="jdbc:mysql://127.0.0.1:${PORT}/cms_verify?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"

echo "库里已发布文章按分类分布（判据的另一把尺子）："
mariadb --socket="$SOCK" -u root -N cms_verify -e "
    SELECT c.id, c.name, COUNT(a.id)
    FROM cms_category c
    LEFT JOIN cms_article a
      ON a.category_id = c.id AND a.deleted = 0 AND a.status = 'PUBLISHED'
    WHERE c.deleted = 0
    GROUP BY c.id, c.name
    ORDER BY c.id"

cd "$ROOT"
MVN_LOG="$TMP/mvn.log"
mvn -o test -Dtest=CategoryCountDbTest \
    -Dcms.it.jdbcUrl="$JDBC_URL" \
    -Dcms.it.jdbcUser=root \
    -Dcms.it.jdbcPassword= >"$MVN_LOG" 2>&1 || {
    tail -60 "$MVN_LOG"
    exit 1
}

# mvn 退出码为 0 也可能是"测试被 @EnabledIf 跳过"——那等于没量。
# 必须看到 3 条都真的执行过，否则按失败处理。
if ! grep -qE "Tests run: 3, Failures: 0, Errors: 0, Skipped: 0.*CategoryCountDbTest" "$MVN_LOG"; then
    echo "判据没有真正执行：期望 CategoryCountDbTest 跑 3 条且全绿，实际日志如下"
    grep -E "Tests run:|Skipped" "$MVN_LOG" | tail -10
    exit 1
fi

grep -E "Tests run: 3, Failures: 0, Errors: 0, Skipped: 0.*CategoryCountDbTest" "$MVN_LOG" | tail -1
echo "OK"
