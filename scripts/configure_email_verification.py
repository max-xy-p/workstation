"""为已备份的既有 Realm 配置邮件发件人并启用邮箱验证，默认只预览。"""
import argparse
import getpass
import json
import os
import re
from urllib.parse import quote

from scripts.configure_public_registration import KeycloakAdmin, validate_base_url


def email_patch(realm, sender, name):
    if not re.fullmatch(r"[^\s<>@]+@[^\s<>@]+\.[^\s<>@]+", sender):
        raise ValueError("邮件发件人地址无效")
    if not name or any(char in name for char in "\r\n<>"):
        raise ValueError("邮件发件人名称无效")
    smtp = dict(realm.get("smtpServer") or {})
    smtp.update({"from": sender, "fromDisplayName": name})
    return {"verifyEmail": True, "smtpServer": smtp}


def configure(admin, realm, sender, name, apply=False):
    path = "/admin/realms/" + quote(realm, safe="")
    current = admin.request("GET", path)
    patch = email_patch(current, sender, name)
    if apply:
        admin.request("PUT", path, patch)
    # 既有 smtpServer 可能含凭据，不将完整修改体打印到终端。
    return {"realm": realm, "verifyEmail": True, "from": sender, "fromDisplayName": name, "applied": apply}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--realm", default="workstation")
    parser.add_argument("--apply", action="store_true", help="确认邮件扩展和发信链路已可用后应用")
    args = parser.parse_args()
    validate_base_url(args.base_url)
    username = os.environ.get("KEYCLOAK_ADMIN_USERNAME") or input("Keycloak 管理员用户名：")
    password = os.environ.get("KEYCLOAK_ADMIN_PASSWORD") or getpass.getpass("Keycloak 管理员密码：")
    admin = KeycloakAdmin(args.base_url, username, password)
    result = configure(admin, args.realm, os.environ.get("MAIL_FROM", "noreply@12718080.xyz"),
                       os.environ.get("MAIL_FROM_NAME", "Workstation"), args.apply)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError) as error:
        raise SystemExit(str(error)) from None
