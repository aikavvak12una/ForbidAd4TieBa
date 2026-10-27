package com.baidu.tbadk.abtest;

/** Test-only host shape: symbol restoration must not execute any host method. */
public final class UbsABTestHelper {
    public static boolean cookieRepeatedOpt() { throw new AssertionError("restore executed host code"); }
    public static boolean isPbCommentFunAdABTest() { throw new AssertionError("restore executed host code"); }
    public static boolean isPbPageBannerFunAdSdkTest() { throw new AssertionError("restore executed host code"); }
}
