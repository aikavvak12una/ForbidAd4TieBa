package com.baidu.searchbox.player.preboot.task;

public abstract class AbsPrebootTask {
    public final void work$preboot_release() { throw new AssertionError("Restore must not deliver work"); }
    public abstract void run();
}
