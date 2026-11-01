#!/usr/bin/env bash
# 导入全部建表与种子数据到 Docker 中的 MySQL。
# 用法：bash sql/import.sh [容器名] [root密码]
#
# 注意：必须显式指定 --default-character-set=utf8mb4，
# 否则容器内 mysql 客户端可能按 latin1 读取，导致中文乱码。
set -euo pipefail

CONTAINER="${1:-myshop-mysql}"
ROOT_PASSWORD="${2:-root}"
DATABASE="${MYSQL_DATABASE:-myshop}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> 重建数据库 ${DATABASE}"
docker exec -i "${CONTAINER}" mysql -uroot -p"${ROOT_PASSWORD}" --default-character-set=utf8mb4 \
    -e "DROP DATABASE IF EXISTS ${DATABASE};
        CREATE DATABASE ${DATABASE} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

for file in system merchant product order payment discount logistics message platform; do
    echo "==> 导入 ${file}.sql"
    docker exec -i "${CONTAINER}" mysql -uroot -p"${ROOT_PASSWORD}" --default-character-set=utf8mb4 \
        "${DATABASE}" < "${SCRIPT_DIR}/${file}.sql"
done

echo "==> 校验中文"
docker exec -i "${CONTAINER}" mysql -uroot -p"${ROOT_PASSWORD}" --default-character-set=utf8mb4 -N \
    -e "USE ${DATABASE}; SELECT id, name FROM product ORDER BY id LIMIT 3;"
echo "==> 完成"
