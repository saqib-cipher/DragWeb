#!/usr/bin/env python3
import argparse
import html
import json
import mimetypes
import os
import subprocess
import sys
import time
import urllib.request
import uuid


def send_telegram_message(bot_token, chat_id, text, thread_id=None):
    url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
    payload = {
        "chat_id": chat_id,
        "text": text,
        "parse_mode": "HTML",
        "disable_web_page_preview": True,
    }
    if thread_id:
        payload["message_thread_id"] = thread_id

    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        print(f"Error sending message to Telegram: {e}", file=sys.stderr)
        return None


def send_telegram_document(bot_token, chat_id, file_path, caption=None, thread_id=None):
    if not os.path.isfile(file_path):
        print(f"File not found: {file_path}", file=sys.stderr)
        return None

    url = f"https://api.telegram.org/bot{bot_token}/sendDocument"
    boundary = uuid.uuid4().hex
    headers = {"Content-Type": f"multipart/form-data; boundary={boundary}"}

    body = []

    def add_field(name, value):
        body.append(f"--{boundary}\r\n".encode("utf-8"))
        body.append(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("utf-8"))
        body.append(f"{value}\r\n".encode("utf-8"))

    add_field("chat_id", str(chat_id))
    if thread_id:
        add_field("message_thread_id", str(thread_id))
    add_field("parse_mode", "HTML")
    if caption:
        add_field("caption", caption)

    filename = os.path.basename(file_path)
    mime_type = mimetypes.guess_type(file_path)[0] or "application/octet-stream"

    body.append(f"--{boundary}\r\n".encode("utf-8"))
    body.append(f'Content-Disposition: form-data; name="document"; filename="{filename}"\r\n'.encode("utf-8"))
    body.append(f"Content-Type: {mime_type}\r\n\r\n".encode("utf-8"))

    with open(file_path, "rb") as f:
        file_bytes = f.read()

    body.append(file_bytes)
    body.append(f"\r\n--{boundary}--\r\n".encode("utf-8"))

    payload = b"".join(body)
    req = urllib.request.Request(url, data=payload, headers=headers)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        print(f"Error sending document ({filename}) to Telegram: {e}", file=sys.stderr)
        return None


def get_git_info():
    branch = os.getenv("GITHUB_REF_NAME")
    if not branch:
        try:
            branch = subprocess.check_output(["git", "rev-parse", "--abbrev-ref", "HEAD"], text=True, errors="replace").strip()
        except Exception:
            branch = "unknown"

    commit = os.getenv("GITHUB_SHA")
    if commit:
        commit_short = commit[:7]
    else:
        try:
            commit_short = subprocess.check_output(["git", "rev-parse", "--short", "HEAD"], text=True, errors="replace").strip()
        except Exception:
            commit_short = "unknown"

    commit_msg = os.getenv("COMMIT_MESSAGE")
    if not commit_msg:
        try:
            commit_msg = subprocess.check_output(["git", "log", "-1", "--pretty=format:%s"], text=True, errors="replace").strip()
        except Exception:
            commit_msg = "No commit message"

    return branch, commit_short, commit_msg


def get_app_version():
    version_code = None
    version_name = None
    for gradle_file in [os.path.join("app", "build.gradle"), os.path.join("app", "build.gradle.kts")]:
        if os.path.exists(gradle_file):
            with open(gradle_file, "r", encoding="utf-8", errors="replace") as f:
                for line in f:
                    line_clean = line.strip()
                    if line_clean.startswith("versionCode"):
                        parts = line_clean.replace("=", " ").split()
                        if len(parts) >= 2:
                            version_code = parts[1].strip("'\"")
                    elif line_clean.startswith("versionName"):
                        parts = line_clean.replace("=", " ").split()
                        if len(parts) >= 2:
                            version_name = parts[1].strip("'\"")
            if version_code:
                break
    return version_code or "1", version_name or "1.0"


def tag_exists(tag_name):
    try:
        output = subprocess.check_output(["git", "tag", "-l"], text=True, errors="replace")
        tags = [t.strip() for t in output.splitlines()]
        target_tags = {tag_name, f"v{tag_name}", f"release-{tag_name}", f"release-v{tag_name}"}
        return any(t in target_tags for t in tags)
    except Exception:
        return False


def create_and_push_tag(tag_name):
    try:
        print(f"Creating Git tag: {tag_name}")
        subprocess.run(["git", "tag", tag_name], check=True)
        print(f"Pushing Git tag {tag_name} to remote...")
        subprocess.run(["git", "push", "origin", tag_name], check=True)
        return True
    except Exception as e:
        print(f"Error pushing tag {tag_name}: {e}", file=sys.stderr)
        return False


def set_github_output(key, value):
    gh_output = os.getenv("GITHUB_OUTPUT")
    if gh_output:
        try:
            with open(gh_output, "a", encoding="utf-8") as f:
                f.write(f"{key}={value}\n")
        except Exception as e:
            print(f"Error setting GitHub output: {e}", file=sys.stderr)


def get_actions_run_url():
    server = os.getenv("GITHUB_SERVER_URL")
    repo = os.getenv("GITHUB_REPOSITORY")
    run_id = os.getenv("GITHUB_RUN_ID")
    if server and repo and run_id:
        return f"{server}/{repo}/actions/runs/{run_id}"
    return None


def main():
    parser = argparse.ArgumentParser(description="Build executor, version tag checker, and Telegram notifier.")
    parser.add_argument("--cmd", type=str, help="Override build command to execute")
    parser.add_argument("--apk", type=str, help="Override APK path to upload")
    parser.add_argument("--aab", type=str, help="Override AAB path for Play Store release")
    parser.add_argument("--bot-token", type=str, default=os.getenv("TELEGRAM_BOT_TOKEN"), help="Telegram Bot Token")
    parser.add_argument("--chat-id", type=str, default=os.getenv("TELEGRAM_CHAT_ID"), help="Telegram Chat ID")
    parser.add_argument("--thread-id", type=str, default=os.getenv("TELEGRAM_THREAD_ID"), help="Telegram Thread ID")
    parser.add_argument("--thread-r-id", type=str, default=os.getenv("TELEGRAM_THREAD_R_ID"), help="Telegram Thread Release ID")

    args = parser.parse_args()

    bot_token = args.bot_token
    chat_id = args.chat_id

    version_code, version_name = get_app_version()
    tag_name = f"v{version_code}"
    is_tagged = tag_exists(version_code) or tag_exists(tag_name)

    print(f"App Version Code: {version_code}, Version Name: {version_name}")
    print(f"Checking if Git tag '{tag_name}' exists: {is_tagged}")

    keystore_file = os.getenv("KEYSTORE_FILE", "")
    has_keystore = bool(keystore_file and os.path.exists(keystore_file))
    print(f"Shared keystore configured: {has_keystore}")

    if is_tagged:
        build_type = "debug"
        cmd = args.cmd or "./gradlew assembleDebug"
        apk_path = args.apk or "app/build/outputs/apk/debug/app-debug.apk"
        aab_path = ""
        thread_id = args.thread_id
        title = f"<b>Debug Build (v{html.escape(version_name)} - Code {html.escape(version_code)})</b>"
    else:
        build_type = "release"
        # Signing credentials are consumed directly from env by app/build.gradle (never via CLI args)
        cmd = args.cmd or "./gradlew assembleRelease bundleRelease"
        apk_path = args.apk or "app/build/outputs/apk/release/app-release.apk"
        aab_path = args.aab or "app/build/outputs/bundle/release/app-release.aab"
        thread_id = args.thread_r_id or args.thread_id
        title = f"<b>Release Build v{html.escape(version_name)} (Code {html.escape(version_code)})</b>"

    print(f"Selected Build Mode: {build_type.upper()}")
    print(f"Executing command: {cmd}")

    start_time = time.time()
    process = subprocess.run(cmd, shell=True)
    exit_code = process.returncode
    duration = round(time.time() - start_time, 2)
    branch, commit_short, commit_msg = get_git_info()

    print(f"\nBuild finished with exit code: {exit_code} in {duration}s")

    set_github_output("is_release", "true" if (build_type == "release" and exit_code == 0) else "false")
    set_github_output("tag_name", tag_name)
    set_github_output("version_name", version_name)
    set_github_output("version_code", version_code)
    set_github_output("apk_path", apk_path if os.path.exists(apk_path) else "")
    set_github_output("aab_path", aab_path if (aab_path and os.path.exists(aab_path)) else "")

    if bot_token and chat_id:
        if exit_code == 0:
            if build_type == "release" and not is_tagged:
                create_and_push_tag(tag_name)

            caption = (
                f"{title}\n\n"
                f"<b>Branch:</b> {html.escape(branch)}\n"
                f"<b>Commit:</b> #{commit_short}\n"
                f"<b>Tag:</b> {html.escape(tag_name)}\n"
                f"<b>Duration:</b> {duration}s\n\n"
                f"<b>Message:</b> {html.escape(commit_msg)}"
            )

            uploaded_apk = False
            if apk_path and os.path.exists(apk_path):
                print(f"Sending APK ({apk_path}) to Telegram...")
                res = send_telegram_document(bot_token, chat_id, apk_path, caption=caption, thread_id=thread_id)
                if res and res.get("ok"):
                    uploaded_apk = True

            if not uploaded_apk:
                print("Sending text notification to Telegram...")
                send_telegram_message(bot_token, chat_id, caption, thread_id=thread_id)

        else:
            run_url = get_actions_run_url()
            run_line = f'\n<b>Run:</b> <a href="{html.escape(run_url)}">View Workflow Run</a>' if run_url else ""
            caption = (
                f"<b>Build Failed ({build_type.upper()})</b>\n\n"
                f"<b>Branch:</b> {html.escape(branch)}\n"
                f"<b>Commit:</b> #{commit_short}\n"
                f"<b>Version Code:</b> {html.escape(version_code)}\n"
                f"<b>Duration:</b> {duration}s{run_line}\n\n"
                f"<b>Message:</b> {html.escape(commit_msg)}"
            )
            print("Sending build failure summary to Telegram (without build log)...")
            send_telegram_message(bot_token, chat_id, caption, thread_id=thread_id)

    sys.exit(exit_code)


if __name__ == "__main__":
    main()
