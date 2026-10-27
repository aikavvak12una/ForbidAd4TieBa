package com.baidu.tbadk.abtest;

public final class UbsABTestHelper {
    public static boolean cookieRepeatedOpt() { throw new AssertionError("Restore must not invoke the target"); }
    public static boolean isPbCommentFunAdABTest() { throw new AssertionError("Restore must not invoke the target"); }
    public static boolean isPbPageBannerFunAdSdkTest() { throw new AssertionError("Restore must not invoke the target"); }
    public boolean instanceFlag() { return true; }
    private static boolean privateFlag() { return true; }
    public static boolean parameterFlag(String value) { return true; }
    public static int integerFlag() { return 1; }
}
