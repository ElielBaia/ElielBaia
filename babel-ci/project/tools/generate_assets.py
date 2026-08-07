import os, math, random, struct, zlib, wave
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DRAW=os.path.join(ROOT,'app','src','main','res','drawable-nodpi')
RAW=os.path.join(ROOT,'app','src','main','res','raw')
os.makedirs(DRAW,exist_ok=True);os.makedirs(RAW,exist_ok=True)

def png(path,w,h,pixel):
    raw=bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            r,g,b,a=pixel(x,y)
            raw.extend((max(0,min(255,int(r))),max(0,min(255,int(g))),max(0,min(255,int(b))),max(0,min(255,int(a)))))
    def chunk(t,data):
        return struct.pack('>I',len(data))+t+data+struct.pack('>I',zlib.crc32(t+data)&0xffffffff)
    data=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(bytes(raw),7))+chunk(b'IEND',b'')
    with open(path,'wb') as f:f.write(data)

def noise2(x,y,seed=0):
    n=(x*374761393+y*668265263+seed*69069)&0xffffffff
    n=(n^(n>>13))*1274126177 & 0xffffffff
    return ((n^(n>>16))&0xffff)/65535.0

def stone(x,y):
    n=noise2(x//2,y//2,11); n2=noise2(x//11,y//11,31)
    vein=abs(math.sin(x*.021+y*.015+5*n2))
    base=150+35*n+16*n2
    if vein<.045: base-=45
    return base*1.02,base*.92,base*.72,255

def clay(x,y):
    n=noise2(x//4,y//4,19); bands=math.sin(y*.08+math.sin(x*.025)*1.8)*8
    return 172+22*n+bands,92+13*n+bands*.25,58+9*n,255

def wood(x,y):
    n=noise2(x//3,y//3,27)
    grain=math.sin(y*.18+math.sin(x*.018)*4+math.sin(x*.071)*1.2)
    knot=math.sin(math.hypot(x-210,y-165)*.09)
    return 132+34*n+18*grain,80+20*n+8*grain,42+12*n+6*knot,255

def adobe(x,y):
    n=noise2(x//2,y//2,41); coarse=noise2(x//10,y//10,3)
    speck=-38 if n<.035 else (22 if n>.97 else 0)
    return 191+20*coarse+speck,151+16*coarse+speck,101+12*coarse+speck,255

def brick(x,y):
    mortar=6
    bw,bh=116,62
    row=y//bh; off=(bw//2 if row%2 else 0)
    xx=(x+off)%bw; yy=y%bh
    if xx<mortar or yy<mortar:
        n=noise2(x//3,y//3,5);return 188+14*n,171+14*n,143+12*n,255
    n=noise2(x//3,y//3,77);return 159+28*n,72+18*n,48+14*n,255

for name,fn in [('tex_stone',stone),('tex_clay',clay),('tex_wood',wood),('tex_adobe',adobe),('tex_brick',brick)]:
    png(os.path.join(DRAW,name+'.png'),384,384,fn)

def icon(x,y):
    cx=256
    d=math.hypot(x-cx,y-270)/360
    r=25+max(0,1-d)*30; g=22+max(0,1-d)*22; b=19+max(0,1-d)*15
    if (x-256)**2+(y-215)**2<125**2: r,g,b=191,126,63
    yy=470-y
    if yy>=0:
        level=int(yy//54)
        half=max(34,205-level*18)
        if abs(x-cx)<half:
            r,g,b=39,32,27
            if abs((x+y*0.12)-256)<8:r,g,b=222,166,91
    return r,g,b,255
png(os.path.join(DRAW,'icon_babel.png'),512,512,icon)

def grain(x,y):
    n=noise2(x,y,123); v=118+int((n-.5)*46)
    return v,int(v*.83),int(v*.61),42
png(os.path.join(DRAW,'ui_grain.png'),512,512,grain)

RATE=22050

def wav(name,duration,fn,volume=.7):
    frames=[]
    count=int(RATE*duration)
    for i in range(count):
        t=i/RATE
        v=max(-1,min(1,fn(t,i,count)))*volume
        frames.append(struct.pack('<h',int(v*32767)))
    with wave.open(os.path.join(RAW,name+'.wav'),'wb') as w:
        w.setnchannels(1);w.setsampwidth(2);w.setframerate(RATE);w.writeframes(b''.join(frames))

rnd=random.Random(81)
wav('stone_thud',.28,lambda t,i,n:(math.sin(2*math.pi*(75-35*t)*t)*math.exp(-15*t)+(.18*(rnd.random()*2-1))*math.exp(-20*t)),.8)
rnd=random.Random(18)
wav('crack',.38,lambda t,i,n:((rnd.random()*2-1)*math.exp(-7*t)*(1 if (i%19)<9 else .35)+.16*math.sin(2*math.pi*180*t)*math.exp(-12*t)),.72)
rnd=random.Random(33)
wav('clay_set',.30,lambda t,i,n:(.45*math.sin(2*math.pi*(48+22*math.sin(t*14))*t)*math.exp(-9*t)+.12*(rnd.random()*2-1)*math.exp(-8*t)),.55)
wav('wood_knock',.24,lambda t,i,n:(math.sin(2*math.pi*240*t)*math.exp(-18*t)+.42*math.sin(2*math.pi*410*t)*math.exp(-24*t)),.55)
rnd=random.Random(101)
wav('wind_loop',2.5,lambda t,i,n:((rnd.random()*2-1)*.16 + math.sin(2*math.pi*.21*t)*.09 + math.sin(2*math.pi*.43*t)*.05),.48)
print('generated assets in',DRAW,'and',RAW)
