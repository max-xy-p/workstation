#!/bin/bash
# MySQL 可能 source 此脚本；使用子 Shell，避免改变入口进程的选项和退出处理。
(
set -euo pipefail

# 可供首次初始化或备份后的已有实例升级使用；不读取业务数据库中的身份或邮件。
: "${MYSQL_ROOT_PASSWORD:?必须设置 MYSQL_ROOT_PASSWORD}"
: "${MAIL_DB_USERNAME:?必须设置 MAIL_DB_USERNAME}"
: "${MAIL_DB_PASSWORD:?必须设置 MAIL_DB_PASSWORD}"
if [[ ! "$MAIL_DB_USERNAME" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "邮件数据库用户名只能包含字母、数字和下划线" >&2
  exit 1
fi
escaped_password="${MAIL_DB_PASSWORD//\\/\\\\}"
escaped_password="${escaped_password//\'/\'\'}"
export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
trap 'unset MYSQL_PWD' EXIT
mysql --protocol=socket --socket="${MYSQL_SOCKET:-/var/run/mysqld/mysqld.sock}" --user=root <<SQL
CREATE DATABASE IF NOT EXISTS workstation_mail CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${MAIL_DB_USERNAME}'@'%' IDENTIFIED BY '${escaped_password}';
ALTER USER '${MAIL_DB_USERNAME}'@'%' IDENTIFIED BY '${escaped_password}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX ON workstation_mail.* TO '${MAIL_DB_USERNAME}'@'%';
SQL
)
