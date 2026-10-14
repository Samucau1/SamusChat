"""Prepare a disposable localhost TLS fixture; never change production certificates."""
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent.parent


def main():
    fixture = ROOT / "logs" / "ci-smoke"
    tls = fixture / "tls"
    tls.mkdir(parents=True, exist_ok=True)
    subprocess.run([os.environ.get("SMOKE_OPENSSL", "openssl"), "req", "-x509", "-newkey", "rsa:2048",
                    "-nodes", "-days", "2", "-subj", "/CN=localhost",
                    "-addext", "subjectAltName=DNS:localhost,IP:127.0.0.1",
                    "-keyout", str(tls / "key.pem"), "-out", str(tls / "cert.pem")],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    (tls / "key.pem").chmod(0o600)
    config = (ROOT / "nginx" / "nginx.conf").read_text(encoding="utf-8")
    if config.count("listen 80;") != 1:
        raise SystemExit("Expected one Nginx HTTP listener in the base configuration")
    config = config.replace("listen 80;", "listen 80;\n        listen 443 ssl;\n"
                            "        ssl_certificate /etc/nginx/tls/cert.pem;\n"
                            "        ssl_certificate_key /etc/nginx/tls/key.pem;\n"
                            "        ssl_protocols TLSv1.2 TLSv1.3;")
    (fixture / "nginx.conf").write_text(config, encoding="utf-8")
    print("Prepared disposable localhost TLS; trust only cert.pem in the smoke client.")


if __name__ == "__main__":
    main()
