package com.baidu.searchbox.live.nps;

import com.baidu.searchbox.live.host2live.video.IVideoInsertLiveService.LoadResultCallback;

public class LiveYYPluginManager {
    public LiveYYPluginManager() { throw new AssertionError("restore must not construct a manager"); }
    private boolean renamedReadiness() { throw new AssertionError("restore must not prepare an engine"); }
    public void preHandleLiveNpsWithoutLoad() {}
    public void preHandleYYLiveSubPluginWithoutLoad() {}
    public boolean isSupportInsertVideo() { return true; }
    public void preHandleYYLivePluginWithoutLoadV2(LoadResultCallback callback) {}
}
