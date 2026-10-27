package com.baidu.searchbox.player;
import com.baidu.searchbox.player.layer.BaseKernelLayer;
import com.baidu.searchbox.player.model.BasicVideoSeries;
import com.baidu.searchbox.player.kernel.AbsVideoKernel;
import java.util.function.Supplier;

/** Overload fixture: signatures are restored, none of these bodies may execute. */
public class BDVideoPlayer {
    private BaseKernelLayer kernelLayer;
    public BDVideoPlayer() {}
    /** Mirrors the host constructor's unconditional access after virtual setup. */
    public BDVideoPlayer(Supplier<? extends AbsVideoKernel> factory) {
        setupKernelLayer(new BaseKernelLayer(factory.get()));
        kernelLayer.setAcceptVolumeChange(false);
    }
    public void setupKernelLayer(BaseKernelLayer layer) { attachKernelLayer(layer); }
    public void attachKernelLayer(BaseKernelLayer layer) { kernelLayer = layer; }
    public BaseKernelLayer getKernelLayer() { return kernelLayer; }
    public void prepare() { throw new AssertionError(); }
    public void start() { throw new AssertionError(); }
    public void resume() { throw new AssertionError(); }
    public void doPlay() { throw new AssertionError(); }
    public void play(String url) { throw new AssertionError(); }
    public void setVideoUrl(String url) { throw new AssertionError(); }
    public void setVideoUrl(String url, boolean prepare) { throw new AssertionError(); }
    public void setVideoSeries(BasicVideoSeries series) { throw new AssertionError(); }
    public void setVideoSeries(BasicVideoSeries series, boolean prepare) { throw new AssertionError(); }

    public static final class SkippedSetup extends BDVideoPlayer {
        public SkippedSetup(Supplier<? extends AbsVideoKernel> factory) { super(factory); }
        @Override public void setupKernelLayer(BaseKernelLayer layer) {}
    }
}
