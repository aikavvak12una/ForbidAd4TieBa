package com.baidu.tbadk.data;

import java.util.List;

/** Only the DTO relationships required by cloud admission restoration, not a host scanner fixture. */
public final class DialogStrategiesData {
    public static int constructed;
    public String dialogName;
    public String dialogUrl;
    public List<StrategiesConfigData> dialogStrategy;

    public DialogStrategiesData() {
        constructed++;
        throw new AssertionError("Restoration must not construct the host data");
    }

    public static final class StrategiesConfigData {
        public String category;
        public String readCategory() { return category; }
    }
}
