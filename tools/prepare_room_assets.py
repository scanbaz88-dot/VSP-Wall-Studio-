#!/usr/bin/env python3
"""
Materialize 24 locally embedded wall scene photos BEFORE building Android.

Input:  app/src/main/assets/room_scenes.json
Output: app/src/main/assets/rooms/room_XX.jpg + thumb_room_XX.jpg
Runtime: OFFLINE. No network permission is required inside the APK.

Pexels photographs are used in an editing application and are credited
in the user-facing scene picker and metadata. This is not a stand-alone
stock-photography download service.
"""
import io
import json
import time
import hashlib
import urllib.error
import urllib.request
from pathlib import Path

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
INDEX = ASSETS / "room_scenes.json"
DEST = ASSETS / "rooms"

def download(photo_id: int) -> bytes:
    url = (f"https://images.pexels.com/photos/{photo_id}/"
           f"pexels-photo-{photo_id}.jpeg?auto=compress&cs=tinysrgb&w=1800")
    error = None
    for attempt in range(4):
        try:
            request = urllib.request.Request(
                url,
                headers={"User-Agent": "Mozilla/5.0 (compatible; VSPWallStudioBuild/3.2)",
                         "Accept": "image/avif,image/webp,image/jpeg,image/*;q=0.8"},
            )
            with urllib.request.urlopen(request, timeout=35) as response:
                data = response.read(10 * 1024 * 1024 + 1)
            if len(data) < 10_000 or len(data) > 10 * 1024 * 1024:
                raise ValueError("invalid stock image size")
            return data
        except Exception as exc:
            error = exc
            time.sleep(2 + attempt * 3)
    raise RuntimeError(f"Photo {photo_id} could not be retrieved from Pexels: {error}")

def photo_bytes(data: bytes, max_edge: int, quality: int) -> tuple[bytes, tuple[int,int]]:
    with Image.open(io.BytesIO(data)) as src:
        if src.width < 680 or src.height < 530:
            raise ValueError(f"source dimensions too small: {src.size}")
        photo = ImageOps.exif_transpose(src).convert("RGB")
        photo.thumbnail((max_edge,max_edge), Image.Resampling.LANCZOS)
        size=photo.size
        memory=io.BytesIO()
        photo.save(memory,"JPEG",quality=quality,progressive=True,optimize=True,
                   subsampling=0)
        return memory.getvalue(),size

def main():
    raw=json.loads(INDEX.read_text(encoding="utf-8"))
    scenes=raw["scenes"]
    assert len(scenes)==24
    assert len({s["id"] for s in scenes})==24
    assert len({s["photo_id"] for s in scenes})==24
    assert sum(bool(s["free"]) for s in scenes)==6
    DEST.mkdir(parents=True, exist_ok=True)
    seen_hashes=set()
    total_bytes=0
    for index,scene in enumerate(scenes,1):
        sid=scene["id"]
        assert sid==f"room_{index:02d}"
        target=DEST/f"{sid}.jpg"
        thumb=DEST/f"thumb_{sid}.jpg"
        if target.exists() and thumb.exists():
            raw_data=target.read_bytes()
            with Image.open(target) as loaded:
                assert loaded.width>=680 and loaded.height>=530
        else:
            data=download(scene["photo_id"])
            raw_data,size=photo_bytes(data,1536,77)
            target.write_bytes(raw_data)
            small,_=photo_bytes(data,480,71)
            thumb.write_bytes(small)
        digest=hashlib.sha256(raw_data).hexdigest()
        if digest in seen_hashes:
            raise ValueError(f"duplicate stock photo hash: {sid}")
        seen_hashes.add(digest)
        total_bytes+=target.stat().st_size+thumb.stat().st_size
        print(f"{index:02d}/24 {sid}: {target.stat().st_size//1024} KiB",flush=True)
    assert len(list(DEST.glob("room_??.jpg")))==24
    assert len(list(DEST.glob("thumb_room_??.jpg")))==24
    print(f"V3.2 complete: 24 unique offline scenes; {total_bytes/1024/1024:.2f} MiB")

if __name__=="__main__":
    main()
