import struct,sys
d=open(sys.argv[1],'rb').read()
# header
off=8
strings=[]
def u16(o): return struct.unpack_from('<H',d,o)[0]
def u32(o): return struct.unpack_from('<I',d,o)[0]
def i32(o): return struct.unpack_from('<i',d,o)[0]
res_ids=[]
out=[]
depth=0
while off<len(d):
    t=u16(off); hs=u16(off+2); sz=u32(off+4)
    if t==0x0001:
        sc=u32(off+8); flags=u32(off+16); ss=u32(off+20)
        utf8=flags&0x100
        offs=[u32(off+28+4*i) for i in range(sc)]
        base=off+ss
        for o in offs:
            p=base+o
            if utf8:
                l=d[p]; p+=1
                if l&0x80: l=((l&0x7f)<<8)|d[p]; p+=1
                l2=d[p]; p+=1
                if l2&0x80: l2=((l2&0x7f)<<8)|d[p]; p+=1
                strings.append(d[p:p+l2].decode('utf8','replace'))
            else:
                l=u16(p); p+=2
                if l&0x8000: l=((l&0x7fff)<<16)|u16(p); p+=2
                strings.append(d[p:p+l*2].decode('utf16','replace'))
    elif t==0x0180:
        pass
    elif t==0x0102:
        ns=i32(off+16); name=i32(off+20)
        ac=u16(off+28)
        ao=off+36
        attrs=[]
        for i in range(ac):
            a=ao+20*i
            an=i32(a+4); raw=i32(a+8); vt=d[a+15]; vd=u32(a+16)
            nm=strings[an] if an>=0 else '?'
            if raw>=0: v=strings[raw]
            elif vt==0x12: v='true' if vd else 'false'
            elif vt==0x10: v=str(vd)
            elif vt==0x11: v=hex(vd)
            elif vt==0x01: v='@0x%x'%vd
            else: v='(t%x)%x'%(vt,vd)
            attrs.append('%s="%s"'%(nm,v))
        out.append('  '*depth+'<'+strings[name]+' '+' '.join(attrs)+'>')
        depth+=1
    elif t==0x0103:
        depth-=1
    off+=sz
print('\n'.join(out))
