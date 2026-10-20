#!/usr/bin/env python3
"""Review alternative credited Pexels backgrounds as a contact sheet. Does not alter shipped catalog."""
from __future__ import annotations
import io, pathlib
from PIL import Image, ImageOps, ImageDraw, ImageFont
from prepare_room_assets import download
out=pathlib.Path("build/wall_candidate_review")
out.mkdir(parents=True,exist_ok=True)
candidates=[
("7059748","Neutral wall console","Elena"),
("3104527","Concrete dining nook","Vicky Tran"),
("7671235","Marble kitchen dining","Ivan S"),
("6956843","Kitchen brick dining","Max Vakhtbovych"),
("8705762","Minimalist dining with TV","Ron Lach"),
("6758784","Dining room with wall","Max Vakhtbovych"),
]
cellw,cellh=300,244
cols=4
rows=(len(candidates)+cols-1)//cols
canvas=Image.new("RGB",(cols*cellw,rows*cellh),(246,242,236))
draw=ImageDraw.Draw(canvas)
for i,(id,name,credit) in enumerate(candidates):
    x=(i%cols)*cellw
    y=(i//cols)*cellh
    try:
        data=download(int(id))
        image=ImageOps.exif_transpose(Image.open(io.BytesIO(data))).convert("RGB")
        image.thumbnail((cellw-14,cellh-58))
        bg=Image.new("RGB",(cellw-14,cellh-58),(212,211,204))
        bg.paste(image,((bg.width-image.width)//2,(bg.height-image.height)//2))
        canvas.paste(bg,(x+7,y+7))
        image.save(out/f"candidate_{id}.jpg",quality=74,optimize=True)
        draw.text((x+7,y+cellh-47),f"{i+1:02}  Pexels {id}",fill=(24,24,24))
        draw.text((x+7,y+cellh-29),name[:36],fill=(47,47,47))
        print("OK",id,name,flush=True)
    except Exception as e:
        draw.text((x+7,y+23),f"{id}: NOT AVAILABLE",fill=(177,0,0))
        print("FAIL",id,str(e),flush=True)
canvas.save(out/"candidates_contact.jpg",quality=84)
