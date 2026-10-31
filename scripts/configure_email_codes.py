"""启用注册及找回密码的邮箱验证码，保留既有账号、角色和客户端；默认只预览。"""
import argparse
import getpass
import json
import os
from urllib.parse import quote

from scripts.configure_public_registration import KeycloakAdmin, validate_base_url

ATTRIBUTE = "workstation.email-code.enabled"
MARKER = "workstationEmailCodeVersion"


def configure(admin, realm_name, apply=False):
    prefix = "/admin/realms/" + quote(realm_name, safe="")
    realm = admin.request("GET", prefix)
    info = admin.request("GET", "/admin/serverinfo")
    for spi, provider in [("required-action", "VERIFY_EMAIL"),
                          ("authenticator", "reset-credential-email")]:
        installed = info.get("providers", {}).get(spi, {}).get("providers", {}).get(provider, {})
        if installed.get("operationalInfo", {}).get(MARKER) != "1":
            raise RuntimeError("请先部署配套 Keycloak 验证码扩展，再启用 Realm 开关")
    flow = realm.get("resetCredentialsFlow") or "reset credentials"
    executions = admin.request("GET", prefix + "/authentication/flows/" + quote(flow, safe="") + "/executions")
    required = {"reset-credentials-choose-user", "reset-credential-email", "reset-password"}
    present = {execution.get("providerId") for execution in executions
               if execution.get("requirement") == "REQUIRED" and execution.get("level") == 0}
    if not required.issubset(present):
        raise RuntimeError("找回密码流程已自定义或缺少必要步骤，请先由管理员核实，工具不会覆盖认证流程")
    attributes = dict(realm.get("attributes") or {})
    attributes[ATTRIBUTE] = "true"
    patch = {"attributes": attributes, "verifyEmail": True,
             "accessCodeLifespanUserAction": min(int(realm.get("accessCodeLifespanUserAction") or 300), 300)}
    if apply:
        admin.request("PUT", prefix, patch)
    return {"realm": realm_name, "emailCodeEnabled": True, "verifyEmail": True,
            "actionLifespanSeconds": patch["accessCodeLifespanUserAction"], "applied": apply}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--realm", default="workstation")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    validate_base_url(args.base_url)
    username = os.environ.get("KEYCLOAK_ADMIN_USERNAME") or input("Keycloak 管理员用户名：")
    password = os.environ.get("KEYCLOAK_ADMIN_PASSWORD") or getpass.getpass("Keycloak 管理员密码：")
    print(json.dumps(configure(KeycloakAdmin(args.base_url, username, password), args.realm, args.apply),
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError) as error:
        raise SystemExit(str(error)) from None
