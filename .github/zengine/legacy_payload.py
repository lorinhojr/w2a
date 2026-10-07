#!/usr/bin/env python3
"""
Compatibilidade com o painel web2app (evento "build_event").

Lê o client_payload (variável W2A_PAYLOAD, JSON) e monta a mesma pasta de job
que o fluxo da ZEngine usa: options.json, game.zip, icon.png e secrets.bin.
Baixa zip/ícone só de URLs https, com limite de tamanho.
A keystore personalizada deve vir CIFRADA em "keystore_sealed"
(sodium_crypto_box_seal com a chave pública do builder) — senhas nunca em claro.
"""
import base64
import json
import os
import re
import sys
import urllib.request
from pathlib import Path

LIMIT = 500 * 1024 * 1024


def fail(msg: str) -> None:
    print(f"::error::{msg}")
    sys.exit(1)


def download(url: str, dest: Path, limit: int) -> bool:
    if not re.match(r"^https://[^\s\"'<>]+$", url or ""):
        return False
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (W2A Builder)"})
    with urllib.request.urlopen(req, timeout=120) as r, open(dest, "wb") as f:
        total = 0
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            total += len(chunk)
            if total > limit:
                fail("Arquivo grande demais")
            f.write(chunk)
    return dest.stat().st_size > 0


def main() -> None:
    folder = Path(sys.argv[1])
    folder.mkdir(parents=True, exist_ok=True)
    try:
        p = json.loads(os.environ.get("W2A_PAYLOAD", "{}"))
    except json.JSONDecodeError:
        fail("payload inválido")
    if not isinstance(p, dict):
        fail("payload inválido")

    if not download(str(p.get("zip_url", "")), folder / "game.zip", LIMIT):
        fail("zip_url precisa ser um link https para o .zip do jogo")
    try:
        download(str(p.get("icon_url", "")), folder / "icon.png", 10 * 1024 * 1024)
    except Exception:  # noqa: BLE001
        print("::warning::Não consegui baixar o ícone; usando o padrão")

    sealed = p.get("keystore_sealed")
    signing = "repo"
    if p.get("signing_type") == "custom" and isinstance(sealed, str) and sealed:
        (folder / "secrets.bin").write_bytes(base64.b64decode(sealed))
        signing = "own"
    elif p.get("signing_type") == "custom":
        fail("Keystore personalizada precisa ir cifrada (keystore_sealed). Atualize o painel web2app.")

    version = str(p.get("app_version") or "1.0.0")
    code_raw = str(p.get("version_code") or "")
    code = int(code_raw) if code_raw.isdigit() else int(os.environ.get("GITHUB_RUN_NUMBER", "1"))
    options = {
        "appName": str(p.get("app_name", "")),
        "packageId": str(p.get("package_name", "")),
        "versionName": version,
        "versionCode": code,
        "minSdk": int(p.get("min_sdk") or 24),
        "orientation": str(p.get("orientation") or "landscape"),
        "output": "apk+aab",
        "signing": signing,
    }
    (folder / "options.json").write_text(json.dumps(options), "utf-8")


if __name__ == "__main__":
    main()
