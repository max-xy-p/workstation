# 基于 XXL-JOB 3.4.2 官方建表脚本，并将默认示例替换为 Workstation 审计任务。
CREATE DATABASE IF NOT EXISTS xxl_job
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE xxl_job;

SET NAMES utf8mb4;

CREATE TABLE xxl_job_group (
    id INT NOT NULL AUTO_INCREMENT,
    app_name VARCHAR(64) NOT NULL COMMENT '执行器 AppName',
    title VARCHAR(64) NOT NULL COMMENT '执行器名称',
    address_type TINYINT NOT NULL DEFAULT 0 COMMENT '0=自动注册，1=手动录入',
    address_list TEXT NULL COMMENT '执行器地址列表',
    update_time DATETIME NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xxl_job_registry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    registry_group VARCHAR(50) NOT NULL,
    registry_key VARCHAR(255) NOT NULL,
    registry_value VARCHAR(255) NOT NULL,
    update_time DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY i_g_k_v (registry_group, registry_key, registry_value) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xxl_job_info (
    id INT NOT NULL AUTO_INCREMENT,
    job_group INT NOT NULL COMMENT '执行器主键 ID',
    job_desc VARCHAR(255) NOT NULL,
    add_time DATETIME NULL,
    update_time DATETIME NULL,
    author VARCHAR(64) NULL,
    alarm_email VARCHAR(255) NULL,
    schedule_type VARCHAR(50) NOT NULL DEFAULT 'NONE',
    schedule_conf VARCHAR(128) NULL,
    misfire_strategy VARCHAR(50) NOT NULL DEFAULT 'DO_NOTHING',
    executor_route_strategy VARCHAR(50) NULL,
    executor_handler VARCHAR(255) NULL,
    executor_param TEXT NULL,
    executor_block_strategy VARCHAR(50) NULL,
    executor_timeout INT NOT NULL DEFAULT 0,
    executor_fail_retry_count INT NOT NULL DEFAULT 0,
    glue_type VARCHAR(50) NOT NULL,
    glue_source MEDIUMTEXT NULL,
    glue_remark VARCHAR(128) NULL,
    glue_updatetime DATETIME NULL,
    child_jobid VARCHAR(255) NULL,
    trigger_status TINYINT NOT NULL DEFAULT 0,
    trigger_last_time BIGINT NOT NULL DEFAULT 0,
    trigger_next_time BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xxl_job_logglue (
    id INT NOT NULL AUTO_INCREMENT,
    job_id INT NOT NULL,
    glue_type VARCHAR(50) NULL,
    glue_source MEDIUMTEXT NULL,
    glue_remark VARCHAR(128) NOT NULL,
    add_time DATETIME NULL,
    update_time DATETIME NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xxl_job_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_group INT NOT NULL,
    job_id INT NOT NULL,
    executor_address VARCHAR(255) NULL,
    executor_handler VARCHAR(255) NULL,
    executor_param TEXT NULL,
    executor_sharding_param VARCHAR(20) NULL,
    executor_fail_retry_count INT NOT NULL DEFAULT 0,
    trigger_time DATETIME NULL,
    trigger_code INT NOT NULL,
    trigger_msg TEXT NULL,
    handle_time DATETIME NULL,
    handle_code INT NOT NULL,
    handle_msg TEXT NULL,
    alarm_status TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY I_trigger_time (trigger_time),
    KEY I_handle_code (handle_code),
    KEY I_jobgroup (job_group),
    KEY I_jobid (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xxl_job_log_report (
    id INT NOT NULL AUTO_INCREMENT,
    trigger_day DATETIME NULL,
    running_count INT NOT NULL DEFAULT 0,
    suc_count INT NOT NULL DEFAULT 0,
    fail_count INT NOT NULL DEFAULT 0,
    update_time DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY i_trigger_day (trigger_day) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xxl_job_lock (
    lock_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (lock_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xxl_job_user (
    id INT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    token VARCHAR(100) NULL,
    role TINYINT NOT NULL COMMENT '0=普通用户，1=管理员',
    permission VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY i_username (username) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO xxl_job_group
    (id, app_name, title, address_type, address_list, update_time)
VALUES
    (1, 'workstation-backend', 'Workstation Backend', 0, NULL, NOW());

INSERT INTO xxl_job_info
    (id, job_group, job_desc, add_time, update_time, author, alarm_email,
     schedule_type, schedule_conf, misfire_strategy, executor_route_strategy,
     executor_handler, executor_param, executor_block_strategy, executor_timeout,
     executor_fail_retry_count, glue_type, glue_source, glue_remark, glue_updatetime,
     child_jobid, trigger_status, trigger_last_time, trigger_next_time)
VALUES
    (1, 1, '审计 Outbox 投递', NOW(), NOW(), 'Workstation', '',
     'FIX_RATE', '5', 'DO_NOTHING', 'FIRST',
     'auditOutboxRelayJob', '', 'SERIAL_EXECUTION', 30,
     1, 'BEAN', '', '初始化', NOW(),
     '', 1, 0, UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL 5 SECOND)) * 1000);

-- 初始化脚本随后会用环境变量提供的密码哈希替换该占位值。
INSERT INTO xxl_job_user (id, username, password, role, permission)
VALUES (1, 'admin', 'replace-by-init-script', 1, NULL);

INSERT INTO xxl_job_lock (lock_name) VALUES ('schedule_lock');

COMMIT;
