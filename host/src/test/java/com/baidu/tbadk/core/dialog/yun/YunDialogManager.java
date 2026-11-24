package com.baidu.tbadk.core.dialog.yun;

import android.content.Context;
import com.baidu.tbadk.data.DialogStrategiesData;
import org.json.JSONObject;

/** Renamed targets model member ownership only; current-APK DexKit verification is separate. */
public final class YunDialogManager {
    private Decision choose(Caller caller, DialogStrategiesData data, JSONObject extra) {
        return new Decision(false, "fixture", "unused");
    }

    public static final class Caller {
        public Context context;
        public Clock time;
        public JSONObject extra;

        public Caller(Context context, Clock time, JSONObject extra) {
            this.context = context;
            this.time = time;
            this.extra = extra;
        }

        public Clock moment() { return time; }
    }

    public static final class Clock {
        public String label;
        public Clock(String label, boolean page) { this.label = label; }
        public String readLabel() { return label; }
    }

    public static final class Decision {
        private final boolean accepted;
        public Decision(boolean accepted, String error, String detail) { this.accepted = accepted; }
        public Decision(String error, boolean accepted, String detail) { this.accepted = accepted; }
        public boolean allowed() { return accepted; }
    }
}
