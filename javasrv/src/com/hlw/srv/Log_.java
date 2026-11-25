package com.hlw.srv;

import android.util.Log;

/** 统一日志出口，方便 logcat 过滤 HLWSRV。 */
public final class Log_ {
    public static final String TAG = "HLWSRV";
    private Log_() {}
    public static void i(String s) { try { Log.i(TAG, s); } catch (Throwable ignored) {} }
    public static void e(String s, Throwable t) { try { Log.e(TAG, s + " :: " + t); } catch (Throwable ignored) {} }
}
