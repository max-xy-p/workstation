"""公开注册的最小角色与授权流程边界。"""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

class RegistrationConfigTests(unittest.TestCase):
    def setUp(self):
        self.realm = json.loads((ROOT / "keycloak/workstation-realm.json").read_text(encoding="utf-8"))

    def test_registration_requires_email_verification(self):
        self.assertTrue(self.realm["registrationAllowed"])
        self.assertTrue(self.realm["verifyEmail"])
        self.assertFalse(self.realm["duplicateEmailsAllowed"])
        self.assertTrue(self.realm["bruteForceProtected"])

    def test_default_role_cannot_grant_admin_or_machine_permissions(self):
        name = self.realm["defaultRole"]["name"]
        roles = {role["name"]: role for role in self.realm["roles"]["realm"]}
        def expand(name):
            role = roles.get(name, {})
            return {name} | {child for item in role.get("composites", {}).get("realm", []) for child in expand(item)}
        granted = expand(name)
        self.assertIn("WORKSTATION_USER", granted)
        self.assertNotIn("WORKSTATION_ADMIN", granted)
        self.assertNotIn("WORKSTATION_SERVICE", granted)
        self.assertNotIn("realm-management", roles[name]["composites"].get("client", {}))

    def test_browser_client_still_requires_code_flow_and_pkce(self):
        browser = next(c for c in self.realm["clients"] if c["clientId"] == "workstation-gateway")
        self.assertTrue(browser["standardFlowEnabled"])
        self.assertFalse(browser["directAccessGrantsEnabled"])
        self.assertFalse(browser["publicClient"])
        self.assertEqual(browser["attributes"]["pkce.code.challenge.method"], "S256")
