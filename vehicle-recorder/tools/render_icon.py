"""Render the launcher mark from geometric primitives; Pillow is a design-time dependency only."""
from pathlib import Path
from PIL import Image, ImageDraw
ROOT = Path(__file__).resolve().parents[1]
SCALE = 4
image = Image.new('RGBA', (512*SCALE, 512*SCALE))
d = ImageDraw.Draw(image)
def box(bounds, fill, radius=0, outline=None, width=1):
    d.rounded_rectangle(tuple(round(n*SCALE) for n in bounds), radius=radius*SCALE, fill=fill, outline=outline, width=width*SCALE)
def ellipse(bounds, fill, outline=None, width=1):
    d.ellipse(tuple(n*SCALE for n in bounds), fill=fill, outline=outline, width=width*SCALE)
def polygon(points, fill):
    d.polygon([(x*SCALE,y*SCALE) for x,y in points], fill=fill)
navy='#0B1728'; mint='#42E3BD'
box((8,8,504,504),navy,112, '#213B50',3)
box((38,38,474,474),'#102235',88)
# A raised camera shoulder and body remain legible at 48 pixels.
polygon([(149,169),(169,133),(221,133),(244,169)],mint)
polygon([(169,163),(179,149),(212,149),(225,163)],'#102235')
box((86,158,426,370),mint,38)
box((102,174,410,354),'#102235',23)
ellipse((178,186,334,342), '#F2FAFF')
ellipse((190,198,322,330), '#152F42')
polygon([(248,218),(264,218),(291,308),(221,308)],mint)
box((253,226,259,244),navy,2)
box((252,255,260,276),navy,2)
box((250,289,262,308),navy,2)
ellipse((359,187,383,211),'#FF6471')
box((184,398,328,404),'#24485A',3)
for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    folder=ROOT/'app/src/main/res'/('mipmap-'+density)
    folder.mkdir(parents=True,exist_ok=True)
    image.resize((size,size),Image.Resampling.LANCZOS).save(folder/'ic_launcher.png')
image.resize((512,512),Image.Resampling.LANCZOS).save(ROOT/'docs/assets/vehicle-recorder-logo.png')
print('Rendered 5 launcher densities and 512px preview')
