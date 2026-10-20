#!/usr/bin/env python3
"""Review alternative credited Pexels backgrounds as a contact sheet. Does not alter shipped catalog."""
from __future__ import annotations
import io, pathlib
from PIL import Image, ImageOps, ImageDraw, ImageFont
from prepare_room_assets import download
out=pathlib.Path("build/wall_candidate_review")
out.mkdir(parents=True,exist_ok=True)
candidates=[
("6489085","Modern corridor room","Max Vakhtbovych"),
("7533922","Gray entrance hallway","Max Vakhtbovych"),
("7031615","Spacious hall glass doors","Max Vakhtbovych"),
("6758526","Light hallway mirror","Max Vakhtbovych"),
("5726022","Minimal green plant wall","kaboompics.com"),
("10554838","Baskets and neutral wall","Ron Lach"),
("7045998","Apartment hallway","Max Vakhtbovych"),
("8490187","Workspace with frame","PNW Production"),
("4910715","Windowlight wall","Inga Seliverstova"),
("4386361","Cabinet and neutral wall","alleksana"),
("8251591","Bedroom white wall","PNW Production"),
("12277130","Blank wall armchair","dada design"),
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
