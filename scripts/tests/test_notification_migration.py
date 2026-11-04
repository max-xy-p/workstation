"""在显式指定的隔离 MySQL 容器中验证通知直写迁移，不连接部署数据库。"""
import os
from pathlib import Path
import re
import subprocess
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[2]
CONTAINER = os.environ.get("NOTIFICATION_MIGRATION_TEST_CONTAINER")


@unittest.skipUnless(CONTAINER, "未指定通知迁移隔离容器")
class NotificationMigrationTests(unittest.TestCase):
    def sql(self, text, *, success=True):
        result = subprocess.run(
            ["docker", "exec", "-i", CONTAINER, "mysql", "-uroot",
             "--default-character-set=utf8mb4", "--batch", "--skip-column-names"],
            input=text, text=True, encoding="utf-8", capture_output=True, check=False,
        )
        if success:
            self.assertEqual(result.returncode, 0, result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, "待处理旧任务必须阻止升级")
        return result

    def test_pending_guard_history_deduplication_and_repeated_upgrade(self):
        database = "workstation_notify_upgrade_" + uuid.uuid4().hex[:12]
        self.assertRegex(database, r"^workstation_notify_upgrade_[0-9a-f]{12}$")
        base = (ROOT / "backend/src/main/resources/db/schema.sql").read_text(encoding="utf-8")
        fresh = (ROOT / "backend/src/main/resources/db/migrate-notifications.sql").read_text(encoding="utf-8")
        migration = (ROOT / "backend/src/main/resources/db/migrate-notifications-direct.sql").read_text(encoding="utf-8")
        legacy_inboxes = """
        CREATE TABLE notification_inboxes (
            recipient VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
            last_sequence BIGINT NOT NULL DEFAULT 0, revision BIGINT NOT NULL DEFAULT 0
        ) ENGINE=InnoDB;
        CREATE TABLE notification_outbox (
            event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
            workspace_id BIGINT NOT NULL, fingerprint CHAR(64) NOT NULL,
            payload_json MEDIUMTEXT NULL, state VARCHAR(16) NOT NULL,
            created_at DATETIME(6) NOT NULL
        ) ENGINE=InnoDB;
        """
        notices = fresh[fresh.index("CREATE TABLE IF NOT EXISTS notifications ("):]
        first = str(uuid.uuid4())
        second = str(uuid.uuid4())
        first_notice = str(uuid.uuid4())
        second_notice = str(uuid.uuid4())
        fingerprint = "a" * 64
        def scoped(text):
            return text.replace("USE workstation;", f"USE `{database}`;")
        try:
            self.sql(f"CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
            self.sql(scoped(base))
            self.sql(f"USE `{database}`;" + legacy_inboxes + notices + f"""
                INSERT INTO security_principals VALUES ('upgrade-user','USER','测试',NULL,NOW(),NOW());
                INSERT INTO workspaces(id,name,type,created_by_subject,created_at,updated_at)
                    VALUES (9001,'升级测试','SHARED','upgrade-user',NOW(),NOW());
                INSERT INTO workspace_members VALUES (9001,'upgrade-user','OWNER',NOW());
                INSERT INTO notification_inboxes VALUES ('upgrade-user',4,7);
                INSERT INTO notification_outbox VALUES
                    ('{first}',9001,'{fingerprint}',NULL,'CONSUMED',NOW()),
                    ('{second}',9001,'{fingerprint}','{{}}','PENDING',NOW());
                INSERT INTO notifications(id,event_id,recipient,sequence,workspace_id,type,title,body,resource_type,created_at)
                    VALUES ('{first_notice}','{first}','upgrade-user',4,9001,'TEST','历史通知','保留正文','WORKSPACE',NOW());
            """)
            self.sql(scoped(fresh))
            rejected = self.sql(scoped(migration), success=False)
            self.assertIn("尚未排空", rejected.stderr)
            self.sql(f"USE `{database}`; INSERT INTO notifications(id,event_id,recipient,sequence,workspace_id,type,title,body,resource_type,created_at)"
                     f" VALUES ('{second_notice}','{second}','upgrade-user',5,9001,'TEST','旧任务完成','第二条正文','WORKSPACE',NOW());"
                     " UPDATE notification_inboxes SET last_sequence=5,revision=8;"
                     " UPDATE notification_outbox SET state='CONSUMED',payload_json=NULL;")
            self.sql(scoped(migration))
            rows = self.sql(f"USE `{database}`; SELECT last_sequence,revision,signal_pending FROM notification_inboxes;"
                            "SELECT COUNT(*) FROM notifications; SELECT COUNT(*) FROM notification_events;").stdout.splitlines()
            self.assertEqual(rows, ["5\t8\t1", "2", "2"])
            self.sql(f"USE `{database}`; UPDATE notification_inboxes SET signal_pending=FALSE, signal_lease_token='stable';")
            self.sql(scoped(migration))
            after = self.sql(f"USE `{database}`; SELECT last_sequence,revision,signal_pending,signal_lease_token FROM notification_inboxes;"
                             "SELECT body FROM notifications WHERE sequence=4;"
                             "SELECT COUNT(*) FROM notification_events;"
                             "SELECT COUNT(*) FROM notification_outbox;").stdout.splitlines()
            self.assertEqual(after, ["5\t8\t0\tstable", "保留正文", "2", "2"])
        finally:
            if re.fullmatch(r"workstation_notify_upgrade_[0-9a-f]{12}", database):
                self.sql(f"DROP DATABASE IF EXISTS `{database}`;")
