#!/usr/bin/env python3
"""Dump class shapes (super, interfaces, fields + constant values, method signatures) from dex
files inside .jar/.apk/.dex inputs.

  python3 dumpclasses.py '<regex on class descriptor>' file.jar [more files...]
e.g.
  python3 dumpclasses.py 'IMotoFace|core_services/face' system/framework/framework.jar \
      system/framework/moto-core_services.jar > moto_face_classes.txt
"""
import re, struct, sys, zipfile, io

def u32(d, o): return struct.unpack_from('<I', d, o)[0]
def u16(d, o): return struct.unpack_from('<H', d, o)[0]
def uleb(d, o):
    r = s = 0
    while True:
        b = d[o]; o += 1
        r |= (b & 0x7f) << s; s += 7
        if not b & 0x80: return r, o

def dexes(path):
    if path.endswith('.dex'):
        yield path, open(path, 'rb').read(); return
    with zipfile.ZipFile(path) as z:
        for n in sorted(z.namelist()):
            if re.fullmatch(r'classes\d*\.dex', n):
                yield path + ':' + n, z.read(n)

def dump(name, d, pat):
    soff, toff = u32(d, 60), u32(d, 68)
    foff, moff, poff = u32(d, 84), u32(d, 92), u32(d, 76)
    csz, coff = u32(d, 96), u32(d, 100)
    def s(i):
        o = u32(d, soff + 4 * i); _, o = uleb(d, o)
        return d[o:d.index(b'\0', o)].decode('utf8', 'replace')
    def t(i): return s(u32(d, toff + 4 * i))
    def tl(off):
        if not off: return []
        return [t(u16(d, off + 4 + 2 * i)) for i in range(u32(d, off))]
    def proto(i):
        o = poff + 12 * i
        return t(u32(d, o + 4)), tl(u32(d, o + 8))
    def field(i):
        o = foff + 8 * i
        return t(u16(d, o + 2)), s(u32(d, o + 4))
    def meth(i):
        o = moff + 8 * i
        r, ps = proto(u16(d, o + 2))
        return s(u32(d, o + 4)), ps, r
    def value(o):
        h = d[o]; o += 1
        vt, va = h & 0x1f, h >> 5
        if vt in (0x00, 0x02, 0x03, 0x04, 0x06, 0x10, 0x11):
            n = va + 1
            v = int.from_bytes(d[o:o + n], 'little', signed=(vt in (0, 2, 3, 4, 6)))
            return v, o + n
        if vt in (0x17, 0x18, 0x19, 0x1a, 0x1b):  # string/type/field/method/enum
            n = va + 1
            return '#%d' % int.from_bytes(d[o:o + n], 'little'), o + n
        if vt == 0x1e: return None, o
        if vt == 0x1f: return bool(va), o
        return '?%x' % vt, o + va + 1
    for i in range(csz):
        o = coff + 32 * i
        cname = t(u32(d, o))
        if not re.search(pat, cname): continue
        sup = u32(d, o + 8)
        print('%s  [%s]' % (cname, name))
        print('  access=0x%x super=%s' % (u32(d, o + 4), t(sup) if sup != 0xffffffff else None))
        print('  interfaces=%s' % tl(u32(d, o + 12)))
        sv = u32(d, o + 28)
        vals = []
        if sv:
            n, p = uleb(d, sv)
            for _ in range(n):
                v, p = value(p); vals.append(v)
        cd = u32(d, o + 24)
        if not cd: continue
        p = cd
        sf, p = uleb(d, p); inf, p = uleb(d, p); dm, p = uleb(d, p); vm, p = uleb(d, p)
        idx = 0
        for k in range(sf):
            dl, p = uleb(d, p); fl, p = uleb(d, p); idx += dl
            ty, fn = field(idx)
            print('  field 0x%x %s %s%s' % (fl, ty, fn, (' = %s' % vals[k]) if k < len(vals) else ''))
        idx = 0
        for k in range(inf):
            dl, p = uleb(d, p); fl, p = uleb(d, p); idx += dl
            ty, fn = field(idx); print('  ifield 0x%x %s %s' % (fl, ty, fn))
        for kind, cnt in (('direct', dm), ('virtual', vm)):
            idx = 0
            for _ in range(cnt):
                dl, p = uleb(d, p); fl, p = uleb(d, p); _, p = uleb(d, p); idx += dl
                mn, ps, r = meth(idx)
                print('  %s 0x%x %s(%s)%s' % (kind, fl, mn, ', '.join(ps), r))

if __name__ == '__main__':
    pat = sys.argv[1]
    for f in sys.argv[2:]:
        for name, data in dexes(f):
            dump(name, data, pat)
