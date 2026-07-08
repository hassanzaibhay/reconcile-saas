#!/usr/bin/env python3
"""Mint a local-dev HS256 JWT for reconcile-saas.

Standalone. NOT wired into the deployable — there is deliberately no token-issuing endpoint in the
app (prod uses an external OIDC issuer). This only exists so a developer or the UI can obtain a bearer
token against the local/test HS256 stub decoder.

The local decoder (iam SecurityConfig.localJwtDecoder) is NimbusJwtDecoder.withSecretKey(...) with the
default validator set: HMAC-SHA256 signature + exp/nbf timestamps. No issuer/audience is required, so a
minimal {sub, tid, iat, exp} token authenticates. TenantFilter reads the `tid` claim to pick the schema.

Usage:
    RECONCILE_JWT_SECRET=... python tools/mint-jwt.py --tid <tenant-uuid>
    python tools/mint-jwt.py --tid 11111111-1111-1111-1111-111111111111 --sub alice@dev --exp 7200

Then:
    curl -H "Authorization: Bearer $(python tools/mint-jwt.py --tid <uuid>)" \
         http://localhost:8080/api/v1/reports/runs/<runId>
"""
import argparse
import base64
import hashlib
import hmac
import json
import os
import sys
import time
import uuid

# Matches the iam SecurityConfig default (reconcile.security.jwt.secret) used by the local/default
# profiles. The "test" Spring profile overrides this property (app/src/test/resources/application-test.yml)
# to a DIFFERENT value for automated tests only — if minting against a "test"-profile app instance,
# pass RECONCILE_JWT_SECRET explicitly rather than relying on this default.
DEFAULT_SECRET = "change-me-in-env-at-least-32-chars-long!!"


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def mint(secret: str, tid: str, sub: str, ttl_seconds: int) -> str:
    now = int(time.time())
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {"sub": sub, "tid": tid, "iat": now, "exp": now + ttl_seconds}
    signing_input = (
        b64url(json.dumps(header, separators=(",", ":")).encode())
        + "."
        + b64url(json.dumps(payload, separators=(",", ":")).encode())
    )
    signature = hmac.new(secret.encode(), signing_input.encode(), hashlib.sha256).digest()
    return signing_input + "." + b64url(signature)


def main() -> int:
    parser = argparse.ArgumentParser(description="Mint a local-dev HS256 JWT for reconcile-saas.")
    parser.add_argument("--tid", required=True, help="tenant UUID for the `tid` claim")
    parser.add_argument("--sub", default="operator@dev", help="`sub` claim (default: operator@dev)")
    parser.add_argument("--exp", type=int, default=3600, help="token lifetime in seconds (default: 3600)")
    args = parser.parse_args()

    try:
        uuid.UUID(args.tid)
    except ValueError:
        print(f"error: --tid must be a valid UUID, got {args.tid!r}", file=sys.stderr)
        return 2

    secret = os.environ.get("RECONCILE_JWT_SECRET", DEFAULT_SECRET)
    print(mint(secret, args.tid, args.sub, args.exp))
    return 0


if __name__ == "__main__":
    sys.exit(main())
