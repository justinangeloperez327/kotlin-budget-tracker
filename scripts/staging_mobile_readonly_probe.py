#!/usr/bin/env python3
"""Opt-in, read-only probe of the manually deployed Next.js mobile workspace API.

Never logs bearer tokens, passwords, email addresses or workspace financial values.
No registration, logout, PUT, mutations, or automatic deployment.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

MAX_RESPONSE_BYTES = 3 * 1024 * 1024
MAX_TOKEN = re.compile(r"^[0-9a-f]{64}$")


def validated_origin(origin: str) -> str:
    parsed = urllib.parse.urlsplit(origin.strip())
    if (parsed.scheme != "https" or not parsed.hostname or parsed.username
        or parsed.password or parsed.query or parsed.fragment
        or parsed.path not in ("", "/")):
        raise ValueError("Expected an HTTPS origin without credentials, query or path")
    if parsed.port is not None and not (1 <= parsed.port <= 65535):
        raise ValueError("Invalid HTTPS port")
    return "https://" + parsed.netloc.lower()


class DenyRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError("Unexpected redirect from staging workspace API")


def response_data(stream) -> dict:
    content = stream.read(MAX_RESPONSE_BYTES + 1)
    if len(content) > MAX_RESPONSE_BYTES:
        raise ValueError("Staging response exceeded size limit")
    try:
        value = json.loads(content.decode("utf-8"))
    except (ValueError, UnicodeDecodeError):
        raise ValueError("Staging response is not valid UTF-8 JSON") from None
    if not isinstance(value, dict):
        raise ValueError("Staging response must contain a JSON object")
    return value


def validate_snapshot(payload: dict) -> int:
    if not isinstance(payload.get("revision"), int) or payload["revision"] < 0:
        raise ValueError("Staging workspace revision invalid")
    user = payload.get("user")
    if not isinstance(user, dict) or not isinstance(user.get("email"), str) or not user["email"].strip():
        raise ValueError("Staging workspace user format invalid")
    data = payload.get("data")
    if not isinstance(data, dict):
        raise ValueError("Staging workspace data missing")
    for key, kind in (("budgets", dict), ("categoryBudgets", dict), ("expenses", list)):
        if not isinstance(data.get(key), kind):
            raise ValueError("Staging workspace has an unsupported financial module: " + key)
    return payload["revision"]


def probe(origin: str, token: str | None, opener=None) -> dict:
    canonical = validated_origin(origin)
    opener = opener or urllib.request.build_opener(DenyRedirects())
    endpoint = canonical + "/api/mobile/workspace"
    headers = {"Accept": "application/json", "Cache-Control": "no-store",
               "User-Agent": "BudgetTracker-Staging-ReadOnlyProbe/1.0"}
    if token is not None:
        if not MAX_TOKEN.fullmatch(token):
            raise ValueError("Staging bearer token has invalid format")
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(endpoint, headers=headers, method="GET")
    status = None
    try:
        with opener.open(request, timeout=20) as stream:
            status = stream.status
            result = response_data(stream)
    except urllib.error.HTTPError as err:
        status = err.code
        if status in (401, 403) and token is None:
            return {"check": "unauthenticated workspace rejected", "status": status,
                    "tokenUsed": False, "readOnly": True}
        raise ValueError("Staging workspace returned HTTP " + str(status)) from None
    if token is None:
        raise ValueError("Unauthenticated staging workspace unexpectedly returned HTTP 200")
    if status != 200:
        raise ValueError("Staging workspace returned unexpected HTTP " + str(status))
    revision = validate_snapshot(result)
    # Never print email, account identifiers, financial amounts or raw data.
    return {"check": "authenticated workspace schema", "status": status,
            "tokenUsed": True, "readOnly": True, "revisionPresent": revision >= 0}


def main() -> int:
    parser=argparse.ArgumentParser()
    parser.add_argument("--origin", required=True, help="Manually deployed HTTPS staging origin")
    parser.add_argument("--staging-confirmed", action="store_true",
                        help="Acknowledge this is a test environment, not a live account")
    parser.add_argument("--token-env", default="BT_STAGING_MOBILE_TOKEN",
                        help="Environment variable containing an optional staging test token")
    args=parser.parse_args()
    if not args.staging_confirmed:
        print("Probe disabled: explicitly confirm a non-production staging environment",
              file=sys.stderr)
        return 1
    try:
        token = os.environ.get(args.token_env) or None
        result = probe(args.origin, token)
        print(json.dumps(result, indent=2))
        if token is None:
            print("Only unauthenticated rejection checked; supply a test token via environment "
                  "variable to verify authenticated workspace read.")
        return 0
    except (ValueError, OSError, urllib.error.URLError) as err:
        # Never print the request, token, server response or returned account details.
        print("Staging read-only probe FAILED:", type(err).__name__, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
