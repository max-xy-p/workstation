"""为已有 Realm 开放注册，只修改注册策略并追加普通准入角色，保留用户与其他配置。"""
import argparse
import getpass
import json
import os
import re
from urllib.error import HTTPError
from urllib.parse import quote, urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def validate_base_url(value):
    parsed = urlsplit(value)
    local = parsed.hostname in {"localhost", "127.0.0.1", "::1"}
    if not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("身份中心地址必须是没有凭据、查询参数和片段的服务地址")
    if parsed.scheme != "https" and not (parsed.scheme == "http" and local):
        raise ValueError("身份中心必须使用 HTTPS；仅本机回环地址允许 HTTP")
    return value.rstrip("/")


def password_policy(value):
    value = value or ""
    pattern = r"(?<![\w])length\((\d+)\)"
    match = re.search(pattern, value)
    if match:
        return re.sub(pattern, lambda item: f"length({max(12, int(item[1]))})", value)
    return "length(12)" + (" and " + value if value else "")


def registration_patch(realm):
    return {"registrationAllowed": True, "verifyEmail": realm.get("verifyEmail", True),
            "passwordPolicy": password_policy(realm.get("passwordPolicy"))}


class KeycloakAdmin:
    def __init__(self, base_url, username, password):
        self.base_url = validate_base_url(base_url)
        self.opener = build_opener(NoRedirect())
        credentials = urlencode({"grant_type": "password", "client_id": "admin-cli",
                                 "username": username, "password": password}).encode()
        response = self.request("POST", "/realms/master/protocol/openid-connect/token", credentials,
                                "application/x-www-form-urlencoded")
        self.token = response["access_token"]

    def request(self, method, path, body=None, content_type="application/json"):
        headers = {"Content-Type": content_type}
        if hasattr(self, "token"):
            headers["Authorization"] = "Bearer " + self.token
        if body is not None and not isinstance(body, bytes):
            body = json.dumps(body).encode()
        request = Request(self.base_url + path, data=body, method=method, headers=headers)
        try:
            with self.opener.open(request, timeout=15) as response:
                data = response.read()
                return json.loads(data) if data else None
        except HTTPError as error:
            raise RuntimeError(f"身份中心请求失败，HTTP {error.code}；请检查管理员权限与地址") from None

    def configure(self, realm_name, apply=False):
        prefix = "/admin/realms/" + quote(realm_name, safe="")
        realm = self.request("GET", prefix)
        default = realm["defaultRole"]
        role = self.request("GET", prefix + "/roles/WORKSTATION_USER")
        # 在开放入口前检查整个默认组合角色，防止已有配置将管理权限授予所有注册者。
        account_clients = self.request("GET", prefix + "/clients?clientId=account")
        account_ids = {client["id"] for client in account_clients}
        visited = set()
        def inspect(current):
            if current["id"] in visited:
                return
            visited.add(current["id"])
            dangerous = {"WORKSTATION_ADMIN", "WORKSTATION_SERVICE", "realm-admin", "manage-users",
                         "manage-realm", "manage-clients", "create-client", "impersonation"}
            if current.get("clientRole") and current.get("containerId") not in account_ids:
                raise RuntimeError("默认角色包含非账号自助管理的客户端权限，请先由管理员核实")
            if current["name"] in dangerous:
                raise RuntimeError("默认角色包含管理或机器权限，请先由管理员核实并移除危险的默认授权")
            if current.get("composite"):
                children = self.request("GET", prefix + "/roles-by-id/" + quote(current["id"], safe="") + "/composites")
                for child in children:
                    inspect(child)
        inspect(default)
        inspect(role)
        patch = registration_patch(realm)
        result = {"realm": realm_name, "defaultRole": default["name"], "settings": patch,
                  "addRole": "WORKSTATION_USER", "applied": apply}
        if apply:
            # 先追加准入角色，再开放注册，避免短暂创建出无法进入工作台的账号。
            self.request("POST", prefix + "/roles-by-id/" + quote(default["id"], safe="") + "/composites", [role])
            self.request("PUT", prefix, patch)
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True, help="Keycloak 地址，包含 /auth 等实际前缀")
    parser.add_argument("--realm", default="workstation")
    parser.add_argument("--apply", action="store_true", help="应用修改；默认仅检查并显示计划")
    args = parser.parse_args()
    # 先检查地址，再收集管理员凭据；不将密码或 Token 写入文件、参数或输出。
    validate_base_url(args.base_url)
    username = os.environ.get("KEYCLOAK_ADMIN_USERNAME") or input("Keycloak 管理员用户名：")
    password = os.environ.get("KEYCLOAK_ADMIN_PASSWORD") or getpass.getpass("Keycloak 管理员密码：")
    result = KeycloakAdmin(args.base_url, username, password).configure(args.realm, args.apply)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError) as error:
        raise SystemExit(str(error)) from None
