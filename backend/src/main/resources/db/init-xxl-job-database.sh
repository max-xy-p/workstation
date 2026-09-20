#!/bin/bash

# MySQL 首次初始化数据目录时，官方 XXL-JOB 表已由前一个 SQL 脚本创建。
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
: "${XXL_JOB_DB_USERNAME:?XXL_JOB_DB_USERNAME is required}"
: "${XXL_JOB_DB_PASSWORD:?XXL_JOB_DB_PASSWORD is required}"
: "${XXL_JOB_ADMIN_PASSWORD:?XXL_JOB_ADMIN_PASSWORD is required}"

if [[ ! "${XXL_JOB_DB_USERNAME}" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "XXL_JOB_DB_USERNAME may only contain letters, digits, and underscores" >&2
  exit 1
fi

escaped_password="${XXL_JOB_DB_PASSWORD//\\/\\\\}"
escaped_password="${escaped_password//\'/\'\'}"
admin_password_hash="$(printf '%s' "${XXL_JOB_ADMIN_PASSWORD}" | sha256sum | cut -d ' ' -f1)"
export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"

if ! mysql \
  --protocol=socket \
  --socket="${MYSQL_SOCKET:-/var/run/mysqld/mysqld.sock}" \
  --user=root <<SQL
CREATE USER IF NOT EXISTS '${XXL_JOB_DB_USERNAME}'@'%' IDENTIFIED BY '${escaped_password}';
ALTER USER '${XXL_JOB_DB_USERNAME}'@'%' IDENTIFIED BY '${escaped_password}';
GRANT ALL PRIVILEGES ON xxl_job.* TO '${XXL_JOB_DB_USERNAME}'@'%';
UPDATE xxl_job.xxl_job_user
SET password = '${admin_password_hash}'
WHERE username = 'admin';
SQL
then
  echo "XXL-JOB database initialization failed." >&2
  unset MYSQL_PWD
  exit 1
fi

unset MYSQL_PWD
echo "XXL-JOB database initialization completed successfully."
