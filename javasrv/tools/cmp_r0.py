import socket, struct, json, urllib.request, urllib.parse, zlib, hashlib, subprocess, time, os

A="C*y0p]oPc[YrRi9htGuU4FaAnNg6BDEH7IOJKL5MQSTVWXZbdefjkl3mqs1vwxz28"
def enc(o):
    raw=json.dumps(o,separators=(",",":")); b=raw.encode(); out=[];bits=0;n=0
    for ch in b:
        bits=(bits<<8)|ch; n+=8
        while n>=6: n-=6; out.append(A[(bits>>n)&0x3F])
    if n: out.append(A[(bits<<6-n)&0x3F])
    return hashlib.md5(raw.encode()).hexdigest()+"".join(out)

def pkt(t, body=b"", mid=1):
    total=11+len(body)
    return b"\x07"+struct.pack(">i",total)+bytes([0,0])+struct.pack(">h",mid)+struct.pack(">h",t)+body

def send_608(port, snap):
    s=socket.create_connection(("127.0.0.1",port),3)
    s.recv(4096)
    s.sendall(pkt(608, snap.encode()+b"\x00", 1))
    time.sleep(0.3)
    try: s.recv(4096)
    except: pass
    s.close()

def cb0(port):
    info={"uid":"10001","p":["7|war"]}
    body=urllib.parse.urlencode({"type":"0","info":enc(info)}).encode()
    r=urllib.request.urlopen(urllib.request.Request(f"http://127.0.0.1:{port}/cb.php",data=body),timeout=8)
    d=json.loads(zlib.decompress(r.read()).decode())
    return (d.get("returndata") or {}).get("7",{}).get("war",{}).get("r0",{})

snap=open('/tmp/snapshot.json').read()
for port,name,out in ((8080,"Python","/tmp/r0_py.json"),(8081,"Java  ","/tmp/r0_ja.json")):
    try:
        send_608(port, snap)
        r0=cb0(port)
        json.dump(r0, open(out,'w'), ensure_ascii=False, indent=1, sort_keys=True)
        print(f"{name} r0 字段数 {len(r0)}")
    except Exception as e:
        print(name,"失败:",e)
