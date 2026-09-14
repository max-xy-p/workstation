#!/bin/bash

# MySQL 官方镜像会在首次创建数据目录时执行 /docker-entrypoint-initdb.d 下的脚本。
# 此时临时 MySQL 服务只开放 Unix Socket，所以不需要额外的初始化容器或 TCP 重试。

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
: "${KEYCLOAK_DB_USERNAME:?KEYCLOAK_DB_USERNAME is required}"
: "${KEYCLOAK_DB_PASSWORD:?KEYCLOAK_DB_PASSWORD is required}"

if [[ ! "${KEYCLOAK_DB_USERNAME}" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "KEYCLOAK_DB_USERNAME may only contain letters, digits, and underscores" >&2
  exit 1
fi

# 密码将被写入 SQL 字符串，因此先转义反斜杠和单引号。
escaped_password="${KEYCLOAK_DB_PASSWORD//\\/\\\\}"
escaped_password="${escaped_password//\'/\'\'}"

export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"

if ! mysql \
  --protocol=socket \
  --socket="${MYSQL_SOCKET:-/var/run/mysqld/mysqld.sock}" \
  --user=root <<SQL
CREATE DATABASE IF NOT EXISTS keycloak
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${KEYCLOAK_DB_USERNAME}'@'%' IDENTIFIED BY '${escaped_password}';
ALTER USER '${KEYCLOAK_DB_USERNAME}'@'%' IDENTIFIED BY '${escaped_password}';
GRANT ALL PRIVILEGES ON keycloak.* TO '${KEYCLOAK_DB_USERNAME}'@'%';
SQL
then
  echo "Keycloak database initialization failed." >&2
  unset MYSQL_PWD
  exit 1
fi

unset MYSQL_PWD
echo "Keycloak database initialization completed successfully."
