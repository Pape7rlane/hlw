package com.hlw.srv;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TCP 服务端（9000）：游戏二进制协议。
 * 包结构：[0x07][int32 len][byte ver][byte 0][int16 id][int16 type][body]
 * len 含 11 字节头，大端。
 */
public final class Tcp {

    public static final int PORT = 9000;

    private Tcp() {}

    public static void start() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    ServerSocket ss = new ServerSocket(PORT);
                    Log_.i("TCP 监听 " + PORT);
                    while (true) {
                        final Socket s = ss.accept();
                        Log_.i("客户端接入 " + s.getRemoteSocketAddress());
                        Thread th = new Thread(new Runnable() {
                            public void run() { serve(s); }
                        });
                        th.setDaemon(true);
                        th.start();
                    }
                } catch (Throwable e) {
                    Log_.e("TCP 启动失败", e);
                }
            }
        }, "hlw-tcp");
        t.setDaemon(true);
        t.start();
    }

    private static void serve(Socket s) {
        try {
            OutputStream out = s.getOutputStream();
            // 握手：先下发 GCCryptKey(116)，零密钥 => 协议保持明文
            out.write(cryptKey());
            out.flush();
            DataInputStream in = new DataInputStream(s.getInputStream());
            while (true) {
                int magic = in.read();
                if (magic < 0) break;
                byte[] hdr = new byte[10];
                in.readFully(hdr);
                int len = ((hdr[0] & 0xFF) << 24) | ((hdr[1] & 0xFF) << 16)
                        | ((hdr[2] & 0xFF) << 8) | (hdr[3] & 0xFF);
                int id = ((hdr[6] & 0xFF) << 8) | (hdr[7] & 0xFF);
                int type = ((hdr[8] & 0xFF) << 8) | (hdr[9] & 0xFF);
                int blen = len - 11;
                byte[] body = new byte[Math.max(blen, 0)];
                if (blen > 0) in.readFully(body);
                byte[] resp = handle(type, id, body);
                if (resp != null) { out.write(resp); out.flush(); }
            }
        } catch (Throwable e) {
            // 连接断开属正常
        } finally {
            try { s.close(); } catch (Throwable ignored) {}
        }
    }

    private static byte[] handle(int type, int id, byte[] body) {
        try {
        switch (type) {
            case 1:   // CGHeartBeat
                return build(2, new byte[0], id);
            case 115: // CGCryptKey 回执，无需响应
                return null;
            case 128: // CGVersion -> GCVersion
                return build(129, new byte[0], id);
            case 117: // CGLoginAccount -> GCLoginAccount
                return build(118, new byte[0], id);
            case 119: { // CGLoginPlayer -> GCLoginPlayer
                return build(120, new byte[0], id);
            }
            case 604: { // CSLockLevel -> SCLockLevel(605)
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                writeShortStr(bo, "gamelevel2000-0");
                bo.write(new byte[]{0, 0, 0, 0});
                bo.write(new byte[]{0, 0, 0, 0});
                bo.write(new byte[]{0, 0, 0, 0});
                bo.write(new byte[]{0, 0, 0, 0});
                return build(605, bo.toByteArray(), id);
            }
            case 606: { // CSEnterLevel -> 607 + WCGameDriver(1265)
                byte[] a = build(607, new byte[0], id);
                byte[] b = build(1265, new byte[0], id);
                byte[] r = new byte[a.length + b.length];
                System.arraycopy(a, 0, r, 0, a.length);
                System.arraycopy(b, 0, r, a.length, b.length);
                Log_.i("进关卡，已回 607 + 1265");
                return r;
            }
            case 612: // CSLeaveLevel -> 613
                return build(613, new byte[0], id);
            case 608: // CSInitPlayer（客户端上报自身快照，单机版忽略）
                return null;
            default:
                return null;
        }
        } catch (Throwable t) {
            Log_.e("handle type=" + type + " 失败", t);
            return null;
        }
    }

    /** GCCryptKey(116)：int32 algorithm, uint32 len, key, uint32 len, key（全 0 密钥） */
    private static byte[] cryptKey() {
        try {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bo.write(new byte[]{0, 0, 0, 0});             // algorithm
        bo.write(new byte[]{0, 0, 0, 4});             // elen
        bo.write(new byte[]{0, 0, 0, 0});             // encryptKey
        bo.write(new byte[]{0, 0, 0, 4});             // dlen
        bo.write(new byte[]{0, 0, 0, 0});             // decryptKey
        return build(116, bo.toByteArray(), 0);
        } catch (Throwable e) { return build(116, new byte[0], 0); }
    }

    static byte[] build(int type, byte[] body, int id) {
        try {
        int total = 11 + body.length;
        ByteArrayOutputStream bo = new ByteArrayOutputStream(total);
        bo.write(0x07);
        bo.write((total >>> 24) & 0xFF);
        bo.write((total >>> 16) & 0xFF);
        bo.write((total >>> 8) & 0xFF);
        bo.write(total & 0xFF);
        bo.write(0);        // msgVersion
        bo.write(0);        // 占位
        bo.write((id >>> 8) & 0xFF);
        bo.write(id & 0xFF);
        bo.write((type >>> 8) & 0xFF);
        bo.write(type & 0xFF);
        if (body.length > 0) bo.write(body, 0, body.length);
        return bo.toByteArray();
        } catch (Throwable e) { return new byte[0]; }
    }

    /** 短字符串：int16 长度 + UTF-8（0 结尾看客户端实现，这里按 Python 版保持一致） */
    private static void writeShortStr(ByteArrayOutputStream bo, String s) {
        byte[] b;
        try { b = s.getBytes("UTF-8"); } catch (Throwable e) { b = s.getBytes(); }
        bo.write((b.length >>> 8) & 0xFF);
        bo.write(b.length & 0xFF);
        bo.write(b, 0, b.length);
    }
}
