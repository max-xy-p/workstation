"""公开注册的最小角色与授权流程边界。"""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def expand_realm_roles(roles, name):
    """迭代遍历角色图；复合角色成环或被多处引用时，每个角色只访问一次。"""
    granted = set()
    pending = [name]
    while pending:
        current = pending.pop()
        if current in granted:
            continue
        granted.add(current)
        pending.extend(roles.get(current, {}).get("composites", {}).get("realm", []))
    return granted


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
        granted = expand_realm_roles(roles, name)
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

    def test_role_traversal_terminates_on_cycles_and_includes_every_branch(self):
        roles = {
            "default": {"composites": {"realm": ["user", "shared"]}},
            "user": {"composites": {"realm": ["shared", "default"]}},
            "shared": {"composites": {"realm": ["WORKSTATION_ADMIN"]}},
        }
        self.assertEqual(expand_realm_roles(roles, "default"),
                         {"default", "user", "shared", "WORKSTATION_ADMIN"})
