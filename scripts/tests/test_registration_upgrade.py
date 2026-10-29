"""已有身份配置升级时保留更强策略，避免凭据误发到明文远端。"""
import unittest
from scripts.configure_public_registration import KeycloakAdmin, password_policy, registration_patch, validate_base_url

class RegistrationUpgradeTests(unittest.TestCase):
    def test_only_local_http_or_https_is_allowed(self):
        self.assertEqual(validate_base_url("https://identity.example.com/auth/"), "https://identity.example.com/auth")
        self.assertEqual(validate_base_url("http://localhost:8090/auth"), "http://localhost:8090/auth")
        for value in ["http://identity.example.com/auth", "https://user:pass@identity.example.com", "https://identity.example.com?token=value", "file:///tmp/realm"]:
            with self.assertRaises(ValueError):
                validate_base_url(value)

    def test_policy_keeps_other_restrictions_and_stronger_minimum(self):
        self.assertEqual(password_policy("length(16) and digits(2)"), "length(16) and digits(2)")
        self.assertEqual(password_policy("length(6) and notUsername"), "length(12) and notUsername")
        self.assertEqual(password_policy("digits(1)"), "length(12) and digits(1)")

    def test_patch_does_not_include_users_clients_or_other_realm_settings(self):
        patch = registration_patch({"passwordPolicy": "length(20)", "users": ["existing"], "sslRequired": "external"})
        self.assertEqual(set(patch), {"registrationAllowed", "verifyEmail", "passwordPolicy"})
        self.assertEqual(patch["passwordPolicy"], "length(20)")

    def admin_with_default_child(self, child):
        admin = KeycloakAdmin.__new__(KeycloakAdmin)
        mutations = []
        def request(method, path, body=None):
            if method != "GET":
                mutations.append((method, path, body))
                return None
            if path.endswith("/roles/WORKSTATION_USER"):
                return {"id": "user", "name": "WORKSTATION_USER", "composite": False}
            if "clients?" in path:
                return [{"id": "account"}]
            if path.endswith("/composites"):
                return [child] if child else []
            return {"defaultRole": {"id": "default", "name": "default-roles-workstation", "composite": True}, "passwordPolicy": "length(16)"}
        admin.request = request
        return admin, mutations

    def test_dangerous_default_roles_abort_before_any_write(self):
        for role in [{"id": "admin", "name": "WORKSTATION_ADMIN"},
                     {"id": "client", "name": "view-users", "clientRole": True, "containerId": "realm-management"}]:
            admin, changes = self.admin_with_default_child(role)
            with self.assertRaises(RuntimeError):
                admin.configure("workstation", apply=True)
            self.assertEqual(changes, [])

    def test_preview_is_read_only_and_apply_adds_access_before_opening_registration(self):
        admin, changes = self.admin_with_default_child(None)
        admin.configure("workstation", apply=False)
        self.assertEqual(changes, [])
        admin.configure("workstation", apply=True)
        self.assertEqual([method for method, _, _ in changes], ["POST", "PUT"])
        self.assertEqual(changes[1][2]["passwordPolicy"], "length(16)")

    def test_user_role_cannot_indirectly_grant_management_permissions(self):
        admin, changes = self.admin_with_default_child(None)
        original = admin.request
        def request(method, path, body=None):
            if path.endswith("/roles/WORKSTATION_USER"):
                return {"id": "user", "name": "WORKSTATION_USER", "composite": True}
            if path.endswith("/roles-by-id/user/composites"):
                return [{"id": "admin", "name": "WORKSTATION_ADMIN"}]
            return original(method, path, body)
        admin.request = request
        with self.assertRaises(RuntimeError):
            admin.configure("workstation", apply=True)
        self.assertEqual(changes, [])
