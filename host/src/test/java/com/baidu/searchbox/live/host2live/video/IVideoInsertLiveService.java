package com.baidu.searchbox.live.host2live.video;

public interface IVideoInsertLiveService {
    interface LoadResultCallback {
        void onResult(boolean ready);
        void onEvent(boolean ready, int code);
    }
}
