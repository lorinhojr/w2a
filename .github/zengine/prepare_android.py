#!/usr/bin/env python3
"""
Prepara o projeto Android a partir de um job da ZEngine (pasta baixada pelo ci.py).

- Valida TODAS as opções (nada do usuário vai para shell ou código-fonte).
- Escreve app/w2a.properties (lido pelo Gradle).
- Extrai o jogo em app/src/main/assets/www (sem path traversal, com limites).
- Gera os ícones (mipmaps legados + adaptativo).
- Assinatura: chave do usuário (secrets.bin, cifrado com a chave pública do
  servidor; aberto com ZE_SEAL_KEY) ou chave nova gerada aqui.
  As senhas são mascaradas no log antes de qualquer uso.

Saídas ($GITHUB_OUTPUT): tasks, new_key, base_name
"""
import base64
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
import unicodedata
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "app"
RES = APP / "src" / "main" / "res"
WWW = APP / "src" / "main" / "assets" / "www"
TMP = Path(os.environ.get("RUNNER_TEMP", "/tmp"))

MAX_FILES = 20000
MAX_TOTAL = 1024 * 1024 * 1024  # 1 GB descompactado


def fail(msg: str) -> None:
    print(f"::error::{msg}")
    sys.exit(1)


def mask(value: str) -> None:
    if value:
        print(f"::add-mask::{value}")


def gh_output(key: str, value: str) -> None:
    out = os.environ.get("GITHUB_OUTPUT")
    if out:
        with open(out, "a", encoding="utf-8") as f:
            f.write(f"{key}={value}\n")


def gh_env(key: str, value: str) -> None:
    out = os.environ.get("GITHUB_ENV")
    if out:
        with open(out, "a", encoding="utf-8") as f:
            f.write(f"{key}={value}\n")


def prop_escape(s: str) -> str:
    """Escapa para .properties (Latin-1 + \\uXXXX)."""
    out = []
    for ch in s:
        if ch in "\\:=#!":
            out.append("\\" + ch)
        elif ch == "\n":
            out.append("\\n")
        elif ord(ch) < 0x20 or ord(ch) > 0x7e:
            out.append(f"\\u{ord(ch):04x}" if ord(ch) <= 0xFFFF else "")
        else:
            out.append(ch)
    s2 = "".join(out)
    return ("\\" + s2) if s2.startswith(" ") else s2


# ── Opções ──────────────────────────────────────────────────────────────────

def load_options(folder: Path) -> dict:
    try:
        o = json.loads((folder / "options.json").read_text("utf-8"))
    except Exception as e:  # noqa: BLE001
        fail(f"options.json inválido: {e}")
    if not isinstance(o, dict):
        fail("options.json inválido")

    name = str(o.get("appName", "")).strip()
    name = re.sub(r"[\x00-\x1f<>&\"'\\]", "", name)[:50]
    if not name:
        fail("Nome do app vazio")

    pkg = str(o.get("packageId", "")).strip()
    if not re.fullmatch(r"[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*){1,7}", pkg) or len(pkg) > 150:
        fail("ID do pacote inválido (use algo como com.seunome.jogo)")
    if any(part in {"java", "android", "kotlin", "do", "if", "for", "new", "class", "package"} for part in pkg.split(".")):
        fail("ID do pacote usa uma palavra reservada")

    ver = str(o.get("versionName", "1.0.0")).strip()
    if not re.fullmatch(r"[0-9][0-9A-Za-z.\-+]{0,29}", ver):
        fail("Versão inválida (ex.: 1.0.0)")

    try:
        code = int(o.get("versionCode", 1))
    except (TypeError, ValueError):
        code = 0
    if not 1 <= code <= 2_100_000_000:
        fail("Código de versão deve ser entre 1 e 2100000000")

    try:
        min_sdk = int(o.get("minSdk", 24))
    except (TypeError, ValueError):
        min_sdk = 0
    if not 24 <= min_sdk <= 36:
        fail("Android mínimo deve ser entre 7.0 (API 24) e 16 (API 36)")

    orient = {"landscape": "sensorLandscape", "portrait": "sensorPortrait", "any": "fullSensor"}.get(str(o.get("orientation", "landscape")))
    if not orient:
        fail("Orientação inválida")

    output = str(o.get("output", "apk+aab"))
    tasks = {"debug-apk": ["assembleDebug"], "apk": ["assembleRelease"], "aab": ["bundleRelease"],
             "apk+aab": ["assembleRelease", "bundleRelease"]}.get(output)
    if not tasks:
        fail("Tipo de saída inválido")

    signing = str(o.get("signing", "auto"))
    if signing not in ("auto", "own", "repo", "vault-new"):
        fail("Assinatura inválida")

    bg = str(o.get("backgroundColor", "#000000"))
    if not re.fullmatch(r"#[0-9a-fA-F]{6}", bg):
        bg = "#000000"

    # Recursos nativos opcionais (o editor marca conforme os plugins usados)
    feats = o.get("features", [])
    if not isinstance(feats, list):
        feats = []
    features = sorted({f for f in feats if f in ("iap", "ads", "firebase", "bgaudio")})
    admob = str(o.get("admobAppId", "")).strip()
    if "ads" in features and not re.fullmatch(r"ca-app-pub-[0-9]{16}~[0-9]{10}", admob):
        fail("ID do app AdMob inválido (formato ca-app-pub-0000000000000000~0000000000). Confira as propriedades do plugin de anúncios.")
    if "ads" not in features:
        admob = ""

    return {"name": name, "pkg": pkg, "ver": ver, "code": code, "min": min_sdk, "orient": orient,
            "output": output, "tasks": tasks, "signing": signing, "bg": bg, "features": features, "admob": admob}


def google_services(folder: Path, pkg: str) -> bool:
    """Copia o google-services.json (Firebase) se ele for deste app. Devolve se o Firebase entra no app."""
    dst = APP / "google-services.json"
    if dst.exists():
        dst.unlink()
    src = folder / "google-services.json"
    if not src.exists():
        return False
    try:
        g = json.loads(src.read_text("utf-8"))
        pkgs = [c["client_info"]["android_client_info"]["package_name"] for c in g.get("client", [])]
    except Exception:  # noqa: BLE001
        fail("O google-services.json não é válido (baixe de novo no console do Firebase).")
    if pkg not in pkgs:
        fail(f"O google-services.json é do app {', '.join(map(str, pkgs)) or '?'}, mas o ID do pacote deste build é {pkg}. "
             "Use o mesmo ID do pacote ou adicione este app no Firebase e baixe o arquivo novo.")
    shutil.copyfile(src, dst)
    return True


# ── Jogo ────────────────────────────────────────────────────────────────────

def extract_game(zip_path: Path) -> None:
    if WWW.exists():
        shutil.rmtree(WWW)
    WWW.mkdir(parents=True)
    root = WWW.resolve()
    total = 0
    with zipfile.ZipFile(zip_path) as z:
        infos = z.infolist()
        if len(infos) > MAX_FILES:
            fail("O jogo tem arquivos demais")
        names = [i.filename.replace("\\", "/") for i in infos]
        # Se tudo estiver dentro de uma pasta só, tira essa pasta
        prefix = ""
        if "index.html" not in names:
            idx = sorted((n for n in names if n.endswith("/index.html")), key=lambda n: n.count("/"))
            if idx:
                prefix = idx[0][: -len("index.html")]
        for info, name in zip(infos, names):
            if not name.startswith(prefix) or name.endswith("/"):
                continue
            rel = name[len(prefix):]
            if not rel or rel.startswith("/") or ".." in rel.split("/") or ":" in rel:
                fail(f"Caminho inválido no zip: {name}")
            total += info.file_size
            if total > MAX_TOTAL:
                fail("O jogo descompactado passa de 1 GB")
            dest = (WWW / rel).resolve()
            if root not in dest.parents:
                fail(f"Caminho inválido no zip: {name}")
            dest.parent.mkdir(parents=True, exist_ok=True)
            with z.open(info) as src, open(dest, "wb") as out:
                shutil.copyfileobj(src, out, 1024 * 1024)
    if not (WWW / "index.html").exists():
        fail("O zip do jogo não tem index.html")
    print(f"jogo extraído ({total // 1024} KB)")


# ── Ícones ──────────────────────────────────────────────────────────────────

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def make_icons(icon_path: Path, bg_hex: str) -> None:
    from PIL import Image, ImageDraw

    # Só ficam os recursos deste template (restos antigos podem citar temas/libs que não existem mais)
    for d in RES.iterdir():
        if d.is_dir() and d.name not in ("values", "values-v28"):
            shutil.rmtree(d)
    for extra in ("test", "androidTest"):
        shutil.rmtree(APP / "src" / extra, ignore_errors=True)

    if icon_path.exists():
        try:
            src = Image.open(icon_path).convert("RGBA")
        except Exception:  # noqa: BLE001
            src = None
    else:
        src = None
    if src is None:
        src = Image.new("RGBA", (512, 512), bg_hex)
        ImageDraw.Draw(src).ellipse((96, 96, 416, 416), fill="#ffffff")

    # Quadrado (corta o excesso no centro)
    w, h = src.size
    s = min(w, h)
    src = src.crop(((w - s) // 2, (h - s) // 2, (w - s) // 2 + s, (h - s) // 2 + s)).resize((512, 512), Image.LANCZOS)

    for dens, k in DENSITIES.items():
        folder = RES / f"mipmap-{dens}"
        folder.mkdir(parents=True, exist_ok=True)
        size = round(48 * k)
        legacy = src.resize((size, size), Image.LANCZOS)
        legacy.save(folder / "ic_launcher.png", optimize=True)
        # Redondo: máscara circular
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        rnd = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        rnd.paste(legacy, (0, 0), mask)
        rnd.save(folder / "ic_launcher_round.png", optimize=True)
        # Adaptativo (Android 8+): 108dp full-bleed — o ícone preenche tudo (evita fundo preto visível).
        fsize = round(108 * k)
        fg = src.resize((fsize, fsize), Image.LANCZOS)
        fg.save(folder / "ic_launcher_foreground.png", optimize=True)

    any_dpi = RES / "mipmap-anydpi-v26"
    any_dpi.mkdir(parents=True, exist_ok=True)
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
           '    <background android:drawable="@color/splash_bg" />\n'
           '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
           '</adaptive-icon>\n')
    (any_dpi / "ic_launcher.xml").write_text(xml, "utf-8")
    (any_dpi / "ic_launcher_round.xml").write_text(xml, "utf-8")


# ── Assinatura ──────────────────────────────────────────────────────────────

def write_signing(store: Path, store_pw: str, alias: str, key_pw: str) -> None:
    props = TMP / "w2a-signing.properties"
    props.write_text(
        f"storeFile={prop_escape(str(store))}\nstorePassword={prop_escape(store_pw)}\n"
        f"keyAlias={prop_escape(alias)}\nkeyPassword={prop_escape(key_pw)}\n", "latin-1", errors="replace")
    os.chmod(props, 0o600)
    gh_env("W2A_SIGNING_FILE", str(props))


def own_key(folder: Path) -> None:
    sealed = folder / "secrets.bin"
    if not sealed.exists():
        fail("Você escolheu usar sua chave, mas ela não chegou ao build")
    sk_b64 = os.environ.get("ZE_SEAL_KEY", "")
    if not sk_b64:
        fail("Segredo ZE_SEAL_KEY não configurado no repositório")
    from nacl.public import PrivateKey, SealedBox
    try:
        data = json.loads(SealedBox(PrivateKey(base64.b64decode(sk_b64))).decrypt(sealed.read_bytes()))
    except Exception:  # noqa: BLE001
        fail("Não consegui abrir os dados da chave (ZE_SEAL_KEY não confere com o servidor)")
    sealed.unlink()
    store_pw = str(data.get("store_password", ""))
    key_pw = str(data.get("key_password", "")) or store_pw
    alias = str(data.get("key_alias", ""))
    for v in (store_pw, key_pw, alias):
        mask(v)
    if not alias or not store_pw:
        fail("Faltou o alias ou a senha da chave")
    store = TMP / "w2a-upload.keystore"
    store.write_bytes(base64.b64decode(str(data.get("keystore_b64", ""))))
    os.chmod(store, 0o600)
    # Confere a senha/alias antes de gastar minutos compilando
    r = subprocess.run(["keytool", "-list", "-keystore", str(store), "-storepass:env", "ZE_KS_PW", "-alias", alias],
                       env={**os.environ, "ZE_KS_PW": store_pw}, capture_output=True, text=True)
    if r.returncode != 0:
        fail("A senha do arquivo ou o alias não conferem com a chave enviada")
    # Confere a senha da chave (do alias): -certreq precisa abrir a chave privada
    r = subprocess.run(["keytool", "-certreq", "-keystore", str(store), "-storepass:env", "ZE_KS_PW",
                        "-alias", alias, "-keypass:env", "ZE_KEY_PW"],
                       env={**os.environ, "ZE_KS_PW": store_pw, "ZE_KEY_PW": key_pw}, capture_output=True, text=True)
    if r.returncode != 0:
        fail("A senha da chave (do alias) não confere")
    write_signing(store, store_pw, alias, key_pw)


def repo_key(app_name: str) -> str:
    """Modo antigo (web2app): chave fixa do repositório (segredos W2A_*), ou nova."""
    b64 = os.environ.get("W2A_REPO_KEYSTORE", "")
    if not b64:
        print("::warning::Sem W2A_REPO_KEYSTORE: usando uma chave nova (atualizações não serão aceitas)")
        new_key(app_name)
        return ""
    store_pw = os.environ.get("W2A_REPO_STOREPASS", "")
    alias = os.environ.get("W2A_REPO_ALIAS", "")
    key_pw = os.environ.get("W2A_REPO_KEYPASS", "") or store_pw
    store = TMP / "w2a-upload.keystore"
    store.write_bytes(base64.b64decode(b64))
    os.chmod(store, 0o600)
    write_signing(store, store_pw, alias, key_pw)
    return ""


def new_key(app_name: str, vault: bool = False) -> Path:
    pw = secrets.token_urlsafe(24)
    mask(pw)
    store = TMP / "w2a-upload.keystore"
    alias = "upload"
    cn = re.sub(r"[,=+<>#;\"\\]", " ", app_name)[:60] or "App"
    r = subprocess.run([
        "keytool", "-genkeypair", "-keystore", str(store), "-storetype", "PKCS12",
        "-alias", alias, "-keyalg", "RSA", "-keysize", "4096", "-validity", "10000",
        "-storepass:env", "ZE_KS_PW", "-keypass:env", "ZE_KS_PW", "-dname", f"CN={cn}, O=ZEngine",
    ], env={**os.environ, "ZE_KS_PW": pw}, capture_output=True, text=True)
    if r.returncode != 0:
        fail("Não consegui gerar a chave nova")
    write_signing(store, pw, alias, pw)
    fp = subprocess.run(["keytool", "-list", "-v", "-keystore", str(store), "-storepass:env", "ZE_KS_PW", "-alias", alias],
                        env={**os.environ, "ZE_KS_PW": pw}, capture_output=True, text=True).stdout
    sha = re.search(r"SHA256:\s*([0-9A-F:]+)", fp)
    if vault:
        # Guardada na conta do usuário: cifrada para o próprio builder (só o CI abre)
        from nacl.public import PrivateKey, SealedBox
        sk_b64 = os.environ.get("ZE_SEAL_KEY", "")
        if not sk_b64:
            fail("Segredo ZE_SEAL_KEY não configurado no repositório")
        plain = json.dumps({"keystore_b64": base64.b64encode(store.read_bytes()).decode(),
                            "store_password": pw, "key_alias": alias, "key_password": pw}).encode()
        out = TMP / "chave.sealed"
        out.write_bytes(SealedBox(PrivateKey(base64.b64decode(sk_b64)).public_key).encrypt(plain))
        os.chmod(out, 0o600)
        return out
    readme = (
        "CHAVE DE ASSINATURA DO SEU APP ANDROID — GUARDE COM CUIDADO!\n"
        "===========================================================\n\n"
        f"App: {app_name}\n"
        "Arquivo: chave-upload.keystore (formato PKCS12)\n"
        f"Alias: {alias}\n"
        f"Senha da chave e do arquivo: {pw}\n"
        f"SHA-256: {sha.group(1) if sha else '(veja com keytool -list -v)'}\n\n"
        "Sem esta chave você NÃO consegue publicar atualizações deste app.\n"
        "Na próxima exportação escolha \"Usar minha chave\" e envie este arquivo\n"
        "com a senha acima. Não compartilhe com ninguém.\n\n"
        "Google Play: ative a \"Assinatura de apps do Google Play\" e use esta\n"
        "chave como chave de upload.\n"
    )
    out = TMP / "chave-android.zip"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(store, "chave-upload.keystore")
        z.writestr("LEIA-ME-CHAVE.txt", readme)
    os.chmod(out, 0o600)
    return out


# ── Principal ───────────────────────────────────────────────────────────────

def main() -> None:
    if len(sys.argv) != 2:
        fail("uso: prepare_android.py <pasta-do-job>")
    folder = Path(sys.argv[1])
    o = load_options(folder)
    # Código antigo do template (pacote trocado por sed) — o novo fica em com/w2a/runtime
    # O código Kotlin fica em zekt/ (ver app/build.gradle.kts); a pasta java antiga não é usada
    shutil.rmtree(APP / "src" / "main" / "java", ignore_errors=True)

    props = (
        "# Gerado pela ZEngine (não edite)\n"
        f"applicationId={o['pkg']}\nversionCode={o['code']}\nversionName={prop_escape(o['ver'])}\n"
        f"minSdk={o['min']}\nappName={prop_escape(o['name'])}\norientation={o['orient']}\n"
        f"backgroundColor={o['bg']}\n"
        f"admobAppId={o['admob']}\n"
    )
    (APP / "w2a.properties").write_text(props, "latin-1")

    extract_game(folder / "game.zip")
    make_icons(folder / "icon.png", o["bg"])

    # Firebase: google-services.json enviado pelo editor (tem que ser deste app)
    feats = [f for f in o["features"] if f != "firebase"]
    if google_services(folder, o["pkg"]):
        feats.append("firebase")
    with open(APP / "w2a.properties", "a", encoding="latin-1") as f:
        f.write(f"features={','.join(sorted(feats))}\n")
    print(f"recursos nativos: {', '.join(sorted(feats)) or 'nenhum'}")

    new = ""
    vault = ""
    if o["output"] != "debug-apk":
        if o["signing"] == "own":
            own_key(folder)
        elif o["signing"] == "repo":
            repo_key(o["name"])
        elif o["signing"] == "vault-new":
            vault = str(new_key(o["name"], vault=True))
        else:
            new = str(new_key(o["name"]))

    ascii_name = unicodedata.normalize("NFKD", o["name"]).encode("ascii", "ignore").decode()
    slug = re.sub(r"[^a-z0-9]+", "-", ascii_name.lower()).strip("-")[:40] or "app"
    gh_output("tasks", " ".join(o["tasks"]))
    gh_output("new_key", new)
    gh_output("vault_key", vault)
    gh_output("base_name", f"{slug}-{o['ver']}")
    print(f"pronto: {o['pkg']} v{o['ver']} ({o['code']}) minSdk {o['min']} → {' '.join(o['tasks'])}")


if __name__ == "__main__":
    main()
