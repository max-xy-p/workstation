"""邮件配置升级不覆盖已有用户、不泄露旧 SMTP 凭据，也不重新关闭邮箱验证。"""
import json
import unittest
from unittest.mock import Mock
from scripts.configure_email_verification import configure, email_patch
from scripts.configure_public_registration import registration_patch


class MailConfigurationTests(unittest.TestCase):
    def test_patch_only_updates_sender_and_verification(self):
        realm = {"users": ["existing"], "smtpServer": {"password": "secret", "replyTo": "help@example.com"}}
        patch = email_patch(realm, "noreply@12718080.xyz", "Workstation")
        self.assertEqual(set(patch), {"verifyEmail", "smtpServer"})
        self.assertTrue(patch["verifyEmail"])
        self.assertEqual(patch["smtpServer"]["replyTo"], "help@example.com")
        self.assertNotIn("from", realm["smtpServer"])

    def test_preview_is_read_only_and_does_not_expose_existing_secret(self):
        admin = Mock()
        admin.request.return_value = {"smtpServer": {"password": "secret"}}
        result = configure(admin, "workstation", "noreply@12718080.xyz", "Workstation")
        self.assertEqual(admin.request.call_count, 1)
        self.assertNotIn("secret", json.dumps(result))
        configure(admin, "workstation", "noreply@12718080.xyz", "Workstation", True)
        self.assertEqual(admin.request.call_args.args[0], "PUT")
        self.assertTrue(admin.request.call_args.args[2]["verifyEmail"])

    def test_sender_header_injection_is_rejected(self):
        for address, name in [("bad-address", "Workstation"), ("a@example.com\r\nBcc: b@example.com", "Workstation"),
                              ("a@example.com", "Name\r\nBcc: b@example.com")]:
            with self.assertRaises(ValueError):
                email_patch({}, address, name)

    def test_public_registration_tool_preserves_email_verification(self):
        self.assertTrue(registration_patch({"verifyEmail": True})["verifyEmail"])
        self.assertFalse(registration_patch({"verifyEmail": False})["verifyEmail"])
        self.assertTrue(registration_patch({})["verifyEmail"])

    def test_sender_accepts_subdomains_and_rejects_empty_labels_and_oversized_input(self):
        for sender in ["name+tag@sub.example.com", "a@example.com"]:
            self.assertEqual(email_patch({}, sender, "Workstation")["smtpServer"]["from"], sender)
        for sender in ["a@sub..example.com", "a@.example.com", "a@example.com.",
                       "a" * 100_000 + "@example.com", "a@" + "." * 100_000]:
            with self.assertRaises(ValueError):
                email_patch({}, sender, "Workstation")
