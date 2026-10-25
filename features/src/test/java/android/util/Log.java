package android.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/** JVM log sink for exercising real hook failure paths without an Android process. */
public final class Log {
    public static final int DEBUG = 3, INFO = 4, WARN = 5, ERROR = 6;
    public static final List<String> entries = new ArrayList<>();

    public static int println(int priority, String tag, String text) {
        entries.add(priority + "/" + tag + ": " + text);
        return 0;
    }
    public static int d(String tag, String text) { return println(DEBUG, tag, text); }
    public static int i(String tag, String text) { return println(INFO, tag, text); }
    public static int w(String tag, String text) { return println(WARN, tag, text); }
    public static int e(String tag, String text) { return println(ERROR, tag, text); }
    public static int e(String tag, String text, Throwable error) {
        return e(tag, text + "\n" + getStackTraceString(error));
    }
    public static String getStackTraceString(Throwable error) {
        StringWriter out = new StringWriter();
        error.printStackTrace(new PrintWriter(out));
        return out.toString();
    }
}
