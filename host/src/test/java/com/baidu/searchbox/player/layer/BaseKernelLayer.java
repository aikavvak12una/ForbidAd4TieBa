package com.baidu.searchbox.player.layer;
import com.baidu.searchbox.player.kernel.AbsVideoKernel;
public class BaseKernelLayer {
    private final AbsVideoKernel kernel;
    private boolean acceptVolumeChange = true;
    public BaseKernelLayer(AbsVideoKernel kernel) { this.kernel = java.util.Objects.requireNonNull(kernel); }
    public AbsVideoKernel getKernel() { return kernel; }
    public void setAcceptVolumeChange(boolean value) { acceptVolumeChange = value; }
    public boolean isAcceptVolumeChange() { return acceptVolumeChange; }
}
