"""Build a private deployment bundle without interpolating secrets into shell code."""
import io
import json
import os
from pathlib import Path
import re
import tarfile


def required(name):
    value = os.environ.get(name, "")
    if not value:
        raise SystemExit(f"Missing configuration: {name}")
    return value


def main():
    if not re.fullmatch(r"[a-zA-Z0-9._-]+", required("SERVER_HOST")):
        raise SystemExit("SERVER_HOST must be an IP address or hostname")
    if not re.fullmatch(r"[a-z_][a-z0-9_-]*", required("SERVER_USER")):
        raise SystemExit("Invalid SERVER_USER")
    keys = ("DOCKER_USERNAME", "IMAGE_TAG", "DB_USER", "DB_PASSWORD",
            "REDIS_PASSWORD", "JWT_SECRET", "PUBLIC_BASE_URL")
    values = {key: required(key) for key in keys}
    if len(values["JWT_SECRET"].encode()) < 32:
        raise SystemExit("JWT_SECRET must have at least 32 bytes")
    if not re.fullmatch(r"[0-9a-f]{40}", values["IMAGE_TAG"]):
        raise SystemExit("IMAGE_TAG must be a full commit SHA")
    if not values["PUBLIC_BASE_URL"].startswith("https://"):
        raise SystemExit("PRODUCTION_URL must use HTTPS")
    # Double quotes support escaped backslashes/quotes; $$ disables interpolation.
    lines = []
    for key, value in values.items():
        if any(char in value for char in "\r\n\0"):
            raise SystemExit(f"Multiline value is not supported for {key}")
        escaped = json.dumps(value, ensure_ascii=False).replace("$", "$$")
        lines.append(f"{key}={escaped}\n")

    temporary = Path(required("RUNNER_TEMP"))
    for filename, variable in (("deploy_key", "SERVER_SSH_KEY"),
                               ("known_hosts", "SERVER_KNOWN_HOSTS")):
        path = temporary / filename
        path.write_text(required(variable).rstrip() + "\n", encoding="utf-8")
        path.chmod(0o600)
    bundle = temporary / "release.tar.gz"
    with tarfile.open(bundle, "w:gz") as archive:
        for filename in ("docker-compose.prod.yml", "nginx/nginx.conf", "scripts/deploy.sh"):
            archive.add(filename, arcname=filename)
        data = "".join(lines).encode()
        info = tarfile.TarInfo(".env.prod")
        info.size, info.mode = len(data), 0o600
        archive.addfile(info, io.BytesIO(data))
    bundle.chmod(0o600)


if __name__ == "__main__":
    main()
