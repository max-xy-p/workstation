#!/bin/bash
set -Eeuo pipefail

if [[ ! "${KEYCLOAK_DB_USERNAME}" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "KEYCLOAK_DB_USERNAME may only contain letters, digits, and underscores" >&2
  exit 1
fi

# 密码将被写入 SQL 字符串，因此先转义反斜杠和单引号。
escaped_password="${KEYCLOAK_DB_PASSWORD//\\/\\\\}"
escaped_password="${escaped_password//\'/\'\'}"

export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"

# 该脚本可以重复执行：数据库和用户不存在时创建，已存在时只同步密码和授权。
mysql --protocol=tcp --host=db --user=root <<SQL
CREATE DATABASE IF NOT EXISTS keycloak
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${KEYCLOAK_DB_USERNAME}'@'%' IDENTIFIED BY '${escaped_password}';
ALTER USER '${KEYCLOAK_DB_USERNAME}'@'%' IDENTIFIED BY '${escaped_password}';
GRANT ALL PRIVILEGES ON keycloak.* TO '${KEYCLOAK_DB_USERNAME}'@'%';
SQL
