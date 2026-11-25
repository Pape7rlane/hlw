package com.chaquo.python;
public class Python {
    public interface Platform {}
    public static void start(Platform p) {}
    public static boolean isStarted() { return false; }
    public static Python getInstance() { return null; }
    public PyObject getModule(String name) { return null; }
}
