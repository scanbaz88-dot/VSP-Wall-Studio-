#!/usr/bin/env python3
"""Review alternative credited Pexels backgrounds as a contact sheet. Does not alter shipped catalog."""
from __future__ import annotations
import io, pathlib
from PIL import Image, ImageOps, ImageDraw, ImageFont
from prepare_room_assets import download
out=pathlib.Path("build/wall_candidate_review")
out.mkdir(parents=True,exist_ok=True)
candidates=[
("8987432","Sofa white wall","Ksenia Chernaya"),
("4846459","Sofa pillows blank wall","Rachel Claire"),
("10827339","White sofa dresser","Curtis Adams"),
("8580720","White sofa white wall","RDNE Stock project"),
("15571770","Arched living room","Muharrem Alper"),
("7533847","Empty living couch","Max Vakhtbovych"),
("19966784","Wood floor coffee table","Alex Tyson"),
("12277130","Chair and blank wall","dada _design"),
("8251591","White bedroom blank wall","PNW Production"),
("2643","Minimal striped bed","Jaymantri"),
("5710695","Office glass desk","Arina Krasnikova"),
("34446589","Empty room wooden floor","Alexander F Ungerer"),
("6800942","Blank canvas sofa","Artem Podrez"),
("30330475","White couch minimal","SlipcoverKAS .COM"),
("28905874","White sofa plant","Beyza Kilicdere"),
("36976767","Bedside lamp blank wall","Brunxs Monochrome"),
("7533847","Living room alternative","Max Vakhtbovych"),
("8987432","Sofa wall second","Ksenia Chernaya"),
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
