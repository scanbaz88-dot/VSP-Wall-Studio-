#!/usr/bin/env python3
"""Verify unique 24 offline room photos and draw real recommended frame positions for QA."""
import json
import hashlib
from pathlib import Path
from PIL import Image, ImageOps, ImageDraw

root=Path(__file__).resolve().parents[1]
assets=root/"app/src/main/assets"
payload=json.loads((assets/"room_scenes.json").read_text(encoding="utf8"))
scenes=payload["scenes"]
assert payload["asset_revision"]>=2
assert len(scenes)==24
assert len({i["photo_id"] for i in scenes})==24
assert sum(i["free"] for i in scenes)==6
assert set(i["category"] for i in scenes)=={"living","bedroom","dining","hallway","office","studio"}
w,h=308,254
cols=4
sheet=Image.new("RGB",(cols*w,((len(scenes)+cols-1)//cols)*h),(245,240,233))
draw=ImageDraw.Draw(sheet)
seen=set()
for n,s in enumerate(scenes):
    sid=s["id"]
    assert sid==f"room_{n+1:02d}"
    full=assets/"rooms"/(sid+".jpg")
    thumb=assets/"rooms"/("thumb_"+sid+".jpg")
    assert full.stat().st_size>10_000 and thumb.stat().st_size>1_000
    img=Image.open(full).convert("RGB")
    assert img.width>=680 and img.height>=530
    digest=hashlib.sha256(full.read_bytes()).hexdigest()
    assert digest not in seen
    seen.add(digest)
    x,y,r=s["anchor"]
    assert .10<=x<=.90 and .10<=y<=.90 and .10<=r<=.50
    left=n%cols*w
    top=n//cols*h
    bg=Image.new("RGB",(w-12,h-56),(210,208,202))
    view=ImageOps.contain(img,(w-12,h-56),Image.Resampling.LANCZOS)
    px=(bg.width-view.width)//2
    py=(bg.height-view.height)//2
    bg.paste(view,(px,py))
    # Bounding box for 4:5 example artwork in real scene pixel coordinates.
    cx=px+x*view.width
    cy=py+y*view.height
    bw=r*view.width
    bh=bw*1.2
    v=ImageDraw.Draw(bg)
    v.rectangle((cx-bw/2,cy-bh/2,cx+bw/2,cy+bh/2),
                outline=(244,187,80),width=4)
    v.rectangle((cx-bw/2+4,cy-bh/2+4,cx+bw/2-4,cy+bh/2-4),
                outline=(68,47,28),width=2)
    sheet.paste(bg,(left+6,top+6))
    draw.text((left+8,top+h-46),f"{sid} — {s['category']} · {'FREE' if s['free'] else 'PREMIUM'}",
              fill=(31,35,31))
    draw.text((left+8,top+h-26),s["title_en"][:39],fill=(76,69,57))
dest=root/"build/v33-wall-fit-review.jpg"
dest.parent.mkdir(exist_ok=True)
sheet.save(dest,quality=85,optimize=True)
print(f"V3.3: {len(scenes)} distinct frame-ready scenes and positions verified; {dest}")
