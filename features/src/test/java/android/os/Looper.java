package android.os;

/** Available only inside an explicitly opened JVM handler test scope. */
public final class Looper {
    private static final Looper MAIN = new Looper();
    public static Looper getMainLooper() {
        Handler.requireTest();
        return MAIN;
    }
}
