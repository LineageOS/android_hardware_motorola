import struct,sys,re
def u32(d,o): return struct.unpack_from('<I',d,o)[0]
def uleb(d,o):
    r=0;s=0
    while True:
        b=d[o];o+=1
        r|=(b&0x7f)<<s;s+=7
        if not b&0x80:break
    return r,o
def parse(path):
    d=open(path,'rb').read()
    ssz=u32(d,56);soff=u32(d,60);tsz=u32(d,64);toff=u32(d,68)
    psz=u32(d,72);poff=u32(d,76);msz=u32(d,88);moff=u32(d,92)
    csz=u32(d,96);coff=u32(d,100)
    def s(i):
        o=u32(d,soff+4*i);_,o=uleb(d,o)
        e=d.index(b'\0',o);return d[o:e].decode('utf8','replace')
    def t(i): return s(u32(d,toff+4*i))
    def meth(i):
        o=moff+8*i
        c=struct.unpack_from('<H',d,o)[0];p=struct.unpack_from('<H',d,o+2)[0];n=u32(d,o+4)
        return t(c),s(n)
    out={}
    for i in range(csz):
        o=coff+32*i
        ci=u32(d,o);sup=u32(d,o+8);cd=u32(d,o+24)
        name=t(ci)
        sp=t(sup) if sup!=0xffffffff else None
        ms=[]
        if cd:
            p=cd
            sf,p=uleb(d,p);inf,p=uleb(d,p);dm,p=uleb(d,p);vm,p=uleb(d,p)
            for _ in range(sf+inf):
                _,p=uleb(d,p);_,p=uleb(d,p)
            for cnt in (dm,vm):
                idx=0
                for _ in range(cnt):
                    dl,p=uleb(d,p);_,p=uleb(d,p);_,p=uleb(d,p)
                    idx+=dl
                    ms.append(meth(idx)[1])
        out[name]=(sp,ms)
    return out
if __name__=='__main__':
    pat=re.compile(sys.argv[1])
    for f in sys.argv[2:]:
        for n,(sp,ms) in parse(f).items():
            if pat.search(n):
                print(f,n,'extends',sp)
                if len(sys.argv)<0:pass
                print('   ',', '.join(ms))

def protos(path, cls_pat):
    pass
