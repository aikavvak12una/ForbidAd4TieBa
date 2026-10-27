package com.baidu.tbadk.switchs;

/** Host signature fixture; never packaged into the module. */
public final class CsjInitSwitchInject {
    public static boolean isOpen() { throw new AssertionError("Restore must not invoke the target"); }
}
