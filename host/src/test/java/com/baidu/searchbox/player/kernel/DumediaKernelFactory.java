package com.baidu.searchbox.player.kernel;
public class DumediaKernelFactory implements IKernelFactory {
    @Override public AbsVideoKernel create(String type) {
        throw new AssertionError("Real decoder factory must not run in empty-kernel test");
    }
}
