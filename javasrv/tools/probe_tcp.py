#!/usr/bin/env python3
"""直接连 TCP 发 604/606，dump 回包 hex。用法: probe_tcp.py PORT"""
import socket, struct, sys, binascii
port = int(sys.argv[1]) if len(sys.argv) > 1 else 9000
def pkt(t, body=b"", mid=1):
    total = 11 + len(body)
    return b"\x07"+struct.pack(">i",total)+bytes([0,0])+struct.pack(">h",mid)+struct.pack(">h",t)+body
def read(s, n=4, t=1.5):
    out=[]; buf=b""; s.settimeout(t)
    try:
        while len(out)<n:
            d=s.recv(65535)
            if not d: break
            buf+=d
            while len(buf)>=11 and buf[0]==7:
                total=struct.unpack(">i",buf[1:5])[0]
                if len(buf)<total: break
                out.append(buf[:total]); buf=buf[total:]
    except Exception: pass
    return out
s=socket.create_connection(("127.0.0.1",port),3)
hs=read(s,1)
print("握手:", binascii.hexlify(hs[0]).decode() if hs else "无")
s.sendall(pkt(604,b"gamelevel0-1\x00\x00",10)); print("605:", [binascii.hexlify(x).decode() for x in read(s,1)])
body=struct.pack(">ffff",127.0,82.0,-485.0,0.0)+struct.pack(">i",0)+b"\x00"
s.sendall(pkt(606,body,11))
for x in read(s,3):
    print("606→", struct.unpack(">h",x[8:10])[0], binascii.hexlify(x).decode()[:120])
s.close()
