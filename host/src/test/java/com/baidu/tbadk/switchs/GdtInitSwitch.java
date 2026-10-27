package com.baidu.tbadk.switchs;

public final class GdtInitSwitch {
    public static boolean isOn() { throw new AssertionError("Restore must not invoke the target"); }
}
