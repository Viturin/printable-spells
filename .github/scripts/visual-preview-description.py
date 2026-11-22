import base64
import binascii
import io
import json
import os
import re
import struct
import subprocess
import zipfile
import zlib
from pathlib import Path

repo = os.environ["GITHUB_REPOSITORY"]
event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
run = event.get("workflow_run", {})
base_id = event["repository"]["id"]
head_repo = run.get("head_repository")
images = {
    "card-grid.png": "Desktop grid of rendered spell cards",
    "mobile.png": "Printable Spells on a mobile viewport",
}
max_archive = 8 * 1024 * 1024
max_image = 2 * 1024 * 1024
start = "<!-- visual-preview:start -->"
end = "<!-- visual-preview:end -->"
inline_start = "<!-- visual-preview:images:start -->"
inline_end = "<!-- visual-preview:images:end -->"

def api(path, *args, method="GET", data=None, missing_ok=False):
    command = ["gh", "api", f"repos/{repo}/{path}", *args]
    command += ["--method", method]
    if data is not None:
        command += ["--input", "-"]
    result = subprocess.run(
        command, input=json.dumps(data) if data is not None else None,
        text=True, capture_output=True,
    )
    if missing_ok and result.returncode and "(HTTP 404)" in result.stderr:
        return None
    result.check_returncode()
    return json.loads(result.stdout) if result.stdout else None

def matches(pr):
    head = pr["head"]
    return (
        pr["state"] == "open"
        and pr["base"]["repo"]["id"] == base_id
        and (head["repo"] or {}).get("id") == head_repo["id"]
        and head["ref"] == run["head_branch"]
        and head["sha"] == run["head_sha"]
        and pr["created_at"] <= run["created_at"]
    )

def png_chunk(kind, data):
    payload = kind + data
    return struct.pack(">I", len(data)) + payload + struct.pack(">I", binascii.crc32(payload))


def clean_png(raw):
    # Accept bounded, non-interlaced Chromium screenshot formats. Rebuild the
    # image from its scanlines so metadata and trailing bytes cannot be published.
    signature = b"\x89PNG\r\n\x1a\n"
    if not raw.startswith(signature):
        raise ValueError("Expected a PNG screenshot")
    offset = len(signature)
    header = None
    compressed = bytearray()
    ended = False
    while offset + 12 <= len(raw):
        size, kind = struct.unpack_from(">I4s", raw, offset)
        end = offset + size + 12
        if end > len(raw):
            raise ValueError("Truncated PNG chunk")
        data = raw[offset + 8:end - 4]
        if binascii.crc32(kind + data) != struct.unpack_from(">I", raw, end - 4)[0]:
            raise ValueError("Invalid PNG checksum")
        if offset == len(signature) and kind == b"IHDR" and size == 13:
            header = data
        elif kind == b"IDAT" and header:
            compressed.extend(data)
        elif kind == b"IEND" and size == 0:
            ended = end == len(raw)
            break
        elif kind not in {b"sRGB", b"gAMA", b"cHRM", b"pHYs", b"iCCP", b"tEXt", b"iTXt", b"zTXt"}:
            raise ValueError("Unsupported PNG chunk")
        offset = end
    if not header or not ended or not compressed:
        raise ValueError("Incomplete PNG")
    width, height, bits, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", header)
    if not (1 <= width <= 1600 and 1 <= height <= 2400 and
            bits == 8 and color in {2, 6} and compression == filtering == interlace == 0):
        raise ValueError("Unsupported screenshot size or format")
    stride = 1 + width * (3 if color == 2 else 4)
    expected = stride * height
    decoder = zlib.decompressobj()
    scanlines = decoder.decompress(compressed, expected + 1)
    if (len(scanlines) != expected or not decoder.eof or decoder.unused_data
            or any(filter_type > 4 for filter_type in scanlines[::stride])):
        raise ValueError("Invalid PNG scanlines")
    png = (signature + png_chunk(b"IHDR", header) +
           png_chunk(b"IDAT", zlib.compress(scanlines)) + png_chunk(b"IEND", b""))
    if len(png) > max_image:
        raise ValueError("Published screenshot is too large")
    return png


def screenshots(run_path):
    pages = api(f"{run_path}/artifacts?per_page=100", "--paginate", "--slurp")
    artifacts = [a for page in pages for a in page["artifacts"]
                 if a["name"] == "visual-preview" and not a["expired"]]
    if len(artifacts) != 1 or artifacts[0]["size_in_bytes"] > max_archive:
        raise ValueError("Missing or oversized visual preview artifact")

    endpoint = f"repos/{repo}/actions/artifacts/{artifacts[0]['id']}/zip"
    with subprocess.Popen(["gh", "api", endpoint], stdout=subprocess.PIPE) as download:
        archive = download.stdout.read(max_archive + 1)
        if len(archive) > max_archive:
            download.kill()
            raise ValueError("Visual preview archive exceeded the size limit")
        if download.wait() != 0:
            raise ValueError("Could not download the visual preview artifact")

    with zipfile.ZipFile(io.BytesIO(archive)) as artifact:
        result = {}
        for name in images:
            entries = [entry for entry in artifact.infolist() if entry.filename == name]
            if len(entries) != 1 or not 0 < entries[0].file_size <= max_image:
                raise ValueError(f"Missing or oversized screenshot: {name}")
            result[name] = clean_png(artifact.read(entries[0]))
        return result


def image_ref(number):
    return f"heads/pr-previews/{number}"


def publish(number, screenshots):
    ref = image_ref(number)
    tree = []
    for name, data in screenshots.items():
        blob = api("git/blobs", method="POST", data={
            "content": base64.b64encode(data).decode("ascii"), "encoding": "base64",
        })
        tree.append({"path": name, "mode": "100644", "type": "blob", "sha": blob["sha"]})
    tree_sha = api("git/trees", method="POST", data={"tree": tree})["sha"]
    sha = api("git/commits", method="POST", data={
        "message": f"Publish visual preview for PR {number}", "tree": tree_sha, "parents": [],
    })["sha"]
    if api(f"git/ref/{ref}", missing_ok=True):
        api(f"git/refs/{ref}", method="PATCH", data={"sha": sha, "force": True})
    else:
        api("git/refs", method="POST", data={"ref": f"refs/{ref}", "sha": sha})
    base = f"https://raw.githubusercontent.com/{repo}/refs/{ref}"
    markup = [
        f'<img src="{base}/{name}?v={sha}" alt="{alt}" width="{720 if name == "card-grid.png" else 260}">'
        for name, alt in images.items()
    ]
    return "\n\n" + "\n".join([inline_start, "Inline preview images are public.", "", *markup, inline_end])


def cleanup():
    number = event["number"]
    path = f"pulls/{number}"
    pr = api(path)
    if pr["state"] != "closed" or pr["base"]["repo"]["id"] != base_id:
        return
    remove_images(number)
    body = pr["body"] or ""
    preview = re.compile(
        r"<!-- visual-preview:start -->.*?<!-- visual-preview:end -->", re.DOTALL,
    )
    image_block = re.compile(r"\n\n" + inline_start + r".*?" + inline_end, re.DOTALL)
    updated = preview.sub(lambda match: image_block.sub("", match[0]), body)
    if updated != body:
        api(path, method="PATCH", data={"body": updated})


def remove_images(number):
    ref = image_ref(number)
    if api(f"git/ref/{ref}", missing_ok=True):
        api(f"git/refs/{ref}", method="DELETE", missing_ok=True)


def find_preview(body):
    existing = re.search(re.escape(start) + r".*?" + re.escape(end), body, re.DOTALL)
    if body.count(start) != body.count(end) or body.count(start) > 1 or (start in body and not existing):
        raise SystemExit("The PR description contains malformed visual preview markers.")
    return existing


def main():
    if os.environ["GITHUB_EVENT_NAME"] == "pull_request_target":
        cleanup()
        return
    if (
        run["event"] != "pull_request"
        or run["repository"]["id"] != base_id
        or run["path"] != ".github/workflows/ci.yml"
        or not head_repo
    ):
        return

    associated = run["pull_requests"]
    if associated:
        candidates = [api(f"pulls/{pr['number']}") for pr in associated]
    else:
        # GitHub can omit the PR association for fork runs.
        candidates = [
            pr
            for page in api(
                "pulls", "--paginate", "--slurp",
                "-f", "state=open",
                "-f", f"head={head_repo['owner']['login']}:{run['head_branch']}",
                "-f", "per_page=100",
            )
            for pr in page
        ]
    candidates = [pr for pr in candidates if matches(pr)]
    if len(candidates) != 1:
        print("No unique open PR matches this run and its current head.")
        return

    run_path = f"actions/runs/{run['id']}"
    current_run = api(run_path)
    if current_run["run_attempt"] != run["run_attempt"] or current_run["status"] != "completed":
        return
    jobs = api(f"{run_path}/jobs?per_page=100", "--paginate", "--slurp")
    capture = [job for page in jobs for job in page["jobs"] if job["name"] == "Visual capture"]
    if len(capture) != 1 or capture[0]["conclusion"] == "skipped":
        return

    conclusion = capture[0]["conclusion"]
    if conclusion == "success":
        status = "Passed. Download **visual-preview** from the run’s Artifacts section."
    elif conclusion == "cancelled":
        status = "Cancelled. See the run for details."
    else:
        status = "Failed. See the capture job logs and any available diagnostics in **visual-preview**."

    # Only publish inline images in public repositories. Private repositories
    # retain the original authenticated artifact link.
    thumbnails = {}
    if conclusion == "success" and event["repository"].get("private") is False:
        try:
            thumbnails = screenshots(run_path)
        except (ValueError, OSError, RuntimeError, zipfile.BadZipFile, zlib.error,
                subprocess.CalledProcessError) as error:
            print(f"Inline screenshots unavailable: {error}")

    # Refresh the body and head immediately before writing.
    pr_path = f"pulls/{candidates[0]['number']}"
    pr = api(pr_path)
    if not matches(pr):
        return
    body = pr["body"] or ""
    existing = find_preview(body)

    order = (run["run_number"], run["run_attempt"])
    if existing:
        previous = re.search(r"<!-- visual-preview:run (\d+) (\d+) -->", existing[0])
        if previous and tuple(map(int, previous.groups())) > order:
            return

    inline = ""
    if thumbnails:
        try:
            inline = publish(pr["number"], thumbnails)
        except subprocess.CalledProcessError as error:
            print(f"Inline screenshots could not be published: {error}")
    # Publication can take several API calls. Use the latest author text.
    refreshed = api(pr_path)
    if not matches(refreshed):
        if inline:
            remove_images(pr["number"])
        return
    body = refreshed["body"] or ""
    existing = find_preview(body)

    url = f"{os.environ['GITHUB_SERVER_URL']}/{repo}/actions/runs/{run['id']}"
    block = (
        f"{start}\n<!-- visual-preview:run {order[0]} {order[1]} -->\n"
        f"## Visual preview\n\n[Actions run]({url}) for `{run['head_sha'][:12]}`. {status}{inline}\n{end}"
    )
    if existing:
        updated = body[:existing.start()] + block + body[existing.end():]
    else:
        updated = body + ("\n\n" if body else "") + block
    if updated != body:
        api(pr_path, method="PATCH", data={"body": updated})
    if not inline:
        remove_images(pr["number"])


if __name__ == "__main__":
    main()
