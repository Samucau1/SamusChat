"""Validate deployment secrets against Compose's actual dotenv parser."""
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent


class PrepareReleaseTests(unittest.TestCase):
    def test_credentials_survive_compose_parsing(self):
        with tempfile.TemporaryDirectory() as temporary:
            env = dict(os.environ, RUNNER_TEMP=temporary, SERVER_HOST="example.com", SERVER_USER="deploy",
                       SERVER_SSH_KEY="test-key", SERVER_KNOWN_HOSTS="example.com test-host-key",
                       DOCKER_USERNAME="test", IMAGE_TAG="a" * 40, DB_USER="test",
                       JWT_SECRET="test-key-at-least-thirty-two-characters", PUBLIC_BASE_URL="https://example.com")
            for password in ("plain-password", "dollar$and#space here", "single'and\"double", "back\\slash", "trailing\\"):
                with self.subTest(password=password):
                    env.update(DB_PASSWORD=password, REDIS_PASSWORD=password)
                    subprocess.run([sys.executable, "scripts/prepare-release.py"], cwd=ROOT, env=env, check=True)
                    with tarfile.open(Path(temporary) / "release.tar.gz") as archive:
                        self.assertEqual(archive.getmember(".env.prod").mode, 0o600)
                        dotenv = Path(temporary) / ".env.prod"
                        dotenv.write_bytes(archive.extractfile(".env.prod").read())
                    # Values must come from the file, not overriding shell variables.
                    compose_env = {key: value for key, value in os.environ.items()
                                   if key not in env or key not in ("DOCKER_USERNAME", "IMAGE_TAG", "DB_USER",
                                        "DB_PASSWORD", "REDIS_PASSWORD", "JWT_SECRET", "PUBLIC_BASE_URL")}
                    result = subprocess.check_output(["docker", "compose", "--env-file", str(dotenv),
                             "-f", "docker-compose.prod.yml", "config", "--environment"], cwd=ROOT, env=compose_env)
                    resolved = dict(line.split("=", 1) for line in result.decode().splitlines() if "=" in line)
                    self.assertEqual(resolved["DB_PASSWORD"], password)
                    self.assertEqual(resolved["REDIS_PASSWORD"], password)


if __name__ == "__main__":
    unittest.main()
