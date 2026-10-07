#!/usr/bin/env python3
"""
Conversa com o servidor de builds da ZEngine (pasta "conta" na hospedagem).

Toda chamada é assinada com HMAC-SHA256 usando o segredo ZE_BUILD_SECRET
(mesmo valor de builder.callback_secret no config.php do servidor). Nada
sensível passa pelo evento do GitHub: só o id do job.

Uso:
  ci.py fetch <pasta>               baixa options.json, game.zip, icon.png, secrets.bin, google-services.json
  ci.py status <status> [mensagem]  compilando | pronto | erro
  ci.py upload <arquivo> [nome]     envia a chave nova (chave-android.zip / chave.sealed)
  ci.py publish <apk/aab...>        publica na Release build-<job> e avisa o servidor
  ci.py ping                        teste de conexão do assistente de configuração
"""
import hashlib
import hmac
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

JOB = os.environ.get("ZE_JOB", "")
SERVER = os.environ.get("ZE_SERVER", "").rstrip("/")
SECRET = os.environ.get("ZE_BUILD_SECRET", "").encode()

if not re.fullmatch(r"[a-f0-9]{32}", JOB):
    sys.exit("ZE_JOB inválido")
_LOCAL_TEST = os.environ.get("ZE_LOCAL_TEST") == "1" and SERVER.startswith("http://127.0.0.1:")
if not _LOCAL_TEST and not re.match(r"^https://[A-Za-z0-9.\-]+(:\d+)?(/[A-Za-z0-9._~\-/]*)?$", SERVER):
    sys.exit("ZE_SERVER inválido (precisa ser https://...)")
if len(SECRET) < 32:
    sys.exit("ZE_BUILD_SECRET ausente ou curto")


def _signed(action: str, name: str = "") -> dict:
    ts = str(int(time.time()))
    sig = hmac.new(SECRET, f"{JOB}|{action}|{name}|{ts}".encode(), hashlib.sha256).hexdigest()
    return {"X-ZE-Job": JOB, "X-ZE-Action": action, "X-ZE-Name": name, "X-ZE-Time": ts, "X-ZE-Signature": sig,
            "User-Agent": "zengine-ci"}


def _url(action: str, name: str = "") -> str:
    q = urllib.parse.urlencode({"job": JOB, "action": action, "name": name})
    return f"{SERVER}/api/build/ci.php?{q}"


def _request(method: str, action: str, name: str = "", body: bytes | None = None, ctype: str = "application/octet-stream",
             tries: int = 4):
    last = None
    for i in range(tries):
        req = urllib.request.Request(_url(action, name), data=body, method=method, headers=_signed(action, name))
        if body is not None:
            req.add_header("Content-Type", ctype)
        try:
            with urllib.request.urlopen(req, timeout=300) as r:
                return r.status, r.read()
        except urllib.error.HTTPError as e:
            if e.code in (400, 401, 403, 404, 409, 413):
                return e.code, e.read()
            last = e
        except (urllib.error.URLError, TimeoutError) as e:
            last = e
        time.sleep(3 * (i + 1))
    raise SystemExit(f"Falha falando com o servidor: {last}")


def fetch(folder: str) -> None:
    os.makedirs(folder, exist_ok=True)
    for name, required in (("options.json", True), ("game.zip", True), ("icon.png", False), ("secrets.bin", False),
                           ("google-services.json", False)):
        code, data = _request("GET", "input", name)
        if code == 404 and not required:
            continue
        if code != 200:
            sys.exit(f"Não consegui baixar {name} (HTTP {code})")
        with open(os.path.join(folder, name), "wb") as f:
            f.write(data)
        print(f"baixado {name} ({len(data)} bytes)")


def status(st: str, message: str = "") -> None:
    if st not in ("compilando", "pronto", "erro"):
        sys.exit("status inválido")
    run = ""
    if os.environ.get("GITHUB_RUN_ID"):
        run = f"{os.environ.get('GITHUB_SERVER_URL', 'https://github.com')}/{os.environ.get('GITHUB_REPOSITORY', '')}/actions/runs/{os.environ['GITHUB_RUN_ID']}"
    body = json.dumps({"status": st, "message": message[-4000:], "run_url": run}).encode()
    code, data = _request("POST", "status", "", body, "application/json")
    if code != 200:
        print(f"aviso: status não aceito (HTTP {code}): {data[:200]!r}")


def upload(path: str, name: str = "") -> None:
    name = name or os.path.basename(path)
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._\-]{0,99}", name):
        sys.exit("nome de arquivo inválido")
    with open(path, "rb") as f:
        data = f.read()
    code, resp = _request("POST", "upload", name, data)
    if code != 200:
        sys.exit(f"Upload de {name} falhou (HTTP {code}): {resp[:300]!r}")
    print(f"enviado {name} ({len(data)} bytes)")


def publish(paths: list[str]) -> None:
    """
    Publica o APK/AAB na Release "build-<job>" deste repositório e avisa o
    servidor com os links (o servidor não guarda os arquivos grandes).
    """
    import subprocess
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    if not re.fullmatch(r"[A-Za-z0-9_.\-]+/[A-Za-z0-9_.\-]+", repo):
        sys.exit("GITHUB_REPOSITORY inválido")
    files = []
    for path in paths:
        name = os.path.basename(path)
        if os.path.isfile(path) and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._\-]{0,99}\.(apk|aab)", name) \
                and name not in (os.path.basename(f) for f in files):
            files.append(path)
    if not files:
        status("erro", "O build terminou sem gerar o APK/AAB.")
        sys.exit(1)
    tag = f"build-{JOB}"
    gh = ["gh", "release"]
    # Release fora da lista "Latest", sem notas pessoais; se já existe (re-run), reaproveita
    r = subprocess.run(gh + ["view", tag], capture_output=True, text=True)
    if r.returncode != 0:
        r = subprocess.run(gh + ["create", tag, "--title", tag, "--notes", "Build Android gerado pela ZEngine.",
                                 "--latest=false"], capture_output=True, text=True)
        if r.returncode != 0:
            sys.exit(f"Não consegui criar a Release: {r.stderr[:300]}")
    r = subprocess.run(gh + ["upload", tag, *files, "--clobber"], capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit(f"Upload para a Release falhou: {r.stderr[:300]}")
    assets = [{"name": os.path.basename(p), "size": os.path.getsize(p),
               "url": f"https://github.com/{repo}/releases/download/{tag}/{urllib.parse.quote(os.path.basename(p))}"}
              for p in files]
    run = f"{os.environ.get('GITHUB_SERVER_URL', 'https://github.com')}/{repo}/actions/runs/{os.environ.get('GITHUB_RUN_ID', '')}"
    body = json.dumps({"status": "pronto", "message": "Pronto!", "run_url": run, "assets": assets}).encode()
    code, data = _request("POST", "status", "", body, "application/json")
    if code != 200:
        sys.exit(f"O servidor não aceitou o aviso de pronto (HTTP {code}): {data[:200]!r}")
    print(f"publicado: {', '.join(a['name'] for a in assets)} na Release {tag}")


def ping() -> None:
    """Prova ao servidor que este repositório tem a chave ZE_SEAL_KEY certa (manda só a pública)."""
    import base64
    from nacl.public import PrivateKey
    sk = os.environ.get("ZE_SEAL_KEY", "")
    try:
        pub = base64.b64encode(bytes(PrivateKey(base64.b64decode(sk)).public_key)).decode()
    except Exception:  # noqa: BLE001
        pub = "invalida"
    body = json.dumps({"status": "pronto", "message": f"ping:{pub}", "run_url": ""}).encode()
    code, data = _request("POST", "status", "", body, "application/json")
    if code != 200:
        sys.exit(f"O servidor recusou o teste (HTTP {code}): {data[:200]!r}")
    print("conexão com o servidor OK")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    cmd, args = sys.argv[1], sys.argv[2:]
    if cmd == "fetch" and len(args) == 1:
        fetch(args[0])
    elif cmd == "status" and 1 <= len(args) <= 2:
        status(args[0], args[1] if len(args) > 1 else "")
    elif cmd == "upload" and 1 <= len(args) <= 2:
        upload(*args)
    elif cmd == "publish":
        publish(args)
    elif cmd == "ping" and not args:
        ping()
    else:
        sys.exit(__doc__)
