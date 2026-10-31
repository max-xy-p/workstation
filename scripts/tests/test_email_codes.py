"""验证码升级只修改策略，缺少扩展或非标准重置流程时拒绝写入。"""
import unittest
from unittest.mock import Mock
from scripts.configure_email_codes import configure, ATTRIBUTE, MARKER


class EmailCodeUpgradeTests(unittest.TestCase):
    def fixture(self):
        admin = Mock()
        realm = {"attributes": {"existing": "preserved"}, "resetCredentialsFlow": "reset credentials",
                 "accessCodeLifespanUserAction": 120, "users": ["untouched"]}
        info = {"providers": {spi: {"providers": {provider: {"operationalInfo": {MARKER: "1"}}}}
                for spi, provider in [("required-action", "VERIFY_EMAIL"),
                                      ("authenticator", "reset-credential-email")]}}
        executions = [{"providerId": provider, "requirement": "REQUIRED", "level": 0}
                      for provider in ["reset-credentials-choose-user", "reset-credential-email", "reset-password"]]
        def request(method, path, body=None):
            if method != "GET":
                return None
            if path == "/admin/serverinfo":
                return info
            if path.endswith("/executions"):
                return executions
            return realm
        admin.request.side_effect = request
        return admin, realm, info, executions

    def test_preview_has_no_writes_and_apply_preserves_attributes_and_shorter_timeout(self):
        admin, realm, _, _ = self.fixture()
        configure(admin, "workstation")
        self.assertTrue(all(call.args[0] == "GET" for call in admin.request.call_args_list))
        self.assertNotIn(ATTRIBUTE, realm["attributes"])
        result = configure(admin, "workstation", True)
        patch = admin.request.call_args.args[2]
        self.assertEqual(set(patch), {"attributes", "verifyEmail", "accessCodeLifespanUserAction"})
        self.assertEqual(patch["attributes"], {"existing": "preserved", ATTRIBUTE: "true"})
        self.assertEqual(patch["accessCodeLifespanUserAction"], 120)
        self.assertTrue(result["applied"])

    def test_missing_extension_aborts_without_any_write(self):
        admin, _, info, _ = self.fixture()
        info["providers"]["authenticator"]["providers"].clear()
        with self.assertRaises(RuntimeError):
            configure(admin, "workstation", True)
        self.assertTrue(all(call.args[0] == "GET" for call in admin.request.call_args_list))

    def test_custom_flow_without_required_email_step_is_not_overwritten(self):
        admin, _, _, executions = self.fixture()
        executions[1]["requirement"] = "DISABLED"
        with self.assertRaises(RuntimeError):
            configure(admin, "workstation", True)
        self.assertTrue(all(call.args[0] == "GET" for call in admin.request.call_args_list))

    def test_action_window_is_capped_and_output_contains_no_existing_attributes(self):
        admin, realm, _, _ = self.fixture()
        realm["accessCodeLifespanUserAction"] = 900
        result = configure(admin, "workstation", True)
        self.assertEqual(result["actionLifespanSeconds"], 300)
        self.assertNotIn("attributes", result)

    def test_new_realm_enables_code_mode_and_retains_password_policy(self):
        import json
        from pathlib import Path
        realm = json.loads((Path(__file__).resolve().parents[2] / "keycloak/workstation-realm.json").read_text(encoding="utf-8"))
        self.assertEqual(realm["attributes"][ATTRIBUTE], "true")
        self.assertTrue(realm["verifyEmail"])
        self.assertTrue(realm["resetPasswordAllowed"])
        self.assertEqual(realm["accessCodeLifespanUserAction"], 300)
        self.assertEqual(realm["passwordPolicy"], "length(12)")
