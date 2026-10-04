#!/usr/bin/env python3
"""Render our code-native brand artwork; no downloaded photos or fonts."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
root = Path(__file__).resolve().parents[1]
assets = root/'fastlane/metadata/android/en-US/images'
assets.mkdir(parents=True, exist_ok=True)
font_regular = '/usr/share/fonts/open-sans/OpenSans-Regular.ttf'
font_bold = '/usr/share/fonts/open-sans/OpenSans-Semibold.ttf'
if not Path(font_regular).exists():
    font_regular = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
    font_bold = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
def font(size, bold=False):
    return ImageFont.truetype(font_bold if bold else font_regular, size)
def mark(canvas, x, y, size):
    d = ImageDraw.Draw(canvas)
    d.rounded_rectangle((x,y,x+size,y+size), radius=size*.23, fill='#227466')
    a=x+size*.22; b=y+size*.34; c=x+size*.78; e=y+size*.74
    d.rounded_rectangle((a,b,c,e),radius=size*.045,fill='#E8F3E8')
    d.polygon([(x+size*.34,b),(x+size*.41,y+size*.25),(x+size*.59,y+size*.25),(x+size*.66,b)],fill='#E8F3E8')
    cx=x+size*.50; cy=y+size*.54; radius=size*.14
    d.ellipse((cx-radius,cy-radius,cx+radius,cy+radius),fill='#227466')
    radius=size*.085
    d.ellipse((cx-radius,cy-radius,cx+radius,cy+radius),fill='#E8F3E8')
icon=Image.new('RGBA',(512,512),(0,0,0,0)); mark(icon,0,0,512); icon.save(assets/'icon.png')
feature=Image.new('RGB',(1024,500),'#F5F4EF'); d=ImageDraw.Draw(feature)
mark(feature,64,70,126)
d.text((222,82),'Folder Camera',font=font(57,True),fill='#202C2A')
d.text((68,255),'A place for every photo.',font=font(43),fill='#202C2A')
d.text((68,323),'Capture into folders. Keep originals locally.',font=font(26),fill='#6F7974')
d.text((68,366),'Optional secure transfer to your PC.',font=font(26),fill='#6F7974')
d.text((868,448),'sigmasd',font=font(18,True),fill='#227466')
feature.save(assets/'featureGraphic.png')
site=root/'site/assets'; site.mkdir(parents=True,exist_ok=True)
icon.save(site/'icon.png'); feature.save(site/'feature.png')
print('Generated 512×512 icon and 1024×500 feature graphic from project artwork.')
