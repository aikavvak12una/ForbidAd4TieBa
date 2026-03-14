package com.tieba.hook;

import android.view.View;
import android.view.ViewGroup;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "TiebaHook";
    private static final String TARGET_PACKAGE = "com.baidu.tieba";
    private static final String MODULE_VERSION_NAME = "6";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + ": initialized. version=" + MODULE_VERSION_NAME);

        ClassLoader cl = lpparam.classLoader;

        // =============================================
        // 1. Strategy layer: fake VIP ad-free status
        // =============================================
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "getMemberCloseAdVipClose", 1);
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "isMemberCloseAdVipClose", true);
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "getMemberCloseAdIsOpen", 1);
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "isMemberCloseAdIsOpen", true);
        hookReturnConstant(cl, "com.baidu.tbadk.core.data.AccountData", "getMemberType", 2);
        hookReturnConstant(cl, "com.baidu.tbadk.data.CloseAdData", "G1", 1);
        hookReturnConstant(cl, "com.baidu.tbadk.data.CloseAdData", "J1", 1);
        hookReturnConstant(cl, "com.baidu.tieba.ad.under.utils.SplashForbidAdHelperKt", "a", true);
        hookReturnConstant(cl, "com.baidu.tieba.nd7", "i0", true);
        hookReturnConstant(cl, "com.baidu.tieba.nd7", "l", 0);

        // =============================================
        // 2. SDK switches & crash prevention
        // =============================================
        hookSwitchManager(cl);
        hookZga(cl);

        // =============================================
        // 3. Feed page (RecyclerView): hide ad views
        // =============================================
        hookFeedAdViews(cl);

        // =============================================
        // 4. Post page (TypeAdapter/ListView): replace ad views with empty views
        // =============================================
        hookPostAdViews(cl);
    }

    // =============================================
    // Feed page ad blocking (BdTypeRecyclerView)
    // =============================================

    /**
     * Block ads in the homepage feed (RecyclerView-based).
     * Uses constructor/lifecycle hooks to hide ad views and prevent measurement.
     */
    private void hookFeedAdViews(ClassLoader cl) {
        // Hide feed cards that contain download controls (App install ads)
        hookFeedAppAdCard(cl);

        // gfa: ad view factory used by feed adapters
        hookGfaAdFactory(cl);

        // FeedAdViewHolder: squash itemView immediately on creation
        hookFeedAdViewHolder(cl);

        // Hide known ad view classes via constructor/lifecycle hooks
        // Base classes cover all subclasses:
        //   AbsFeedAdxView → FeedAdxNativePbView, FeedAdxNativeCommentView, FeedAdxNativeFrsView, etc.
        //   AdCardBaseView → AdCardSinglePicView, AdCardSmallPicView, AdCardVideoView
        hookHideViewByClassName(cl, "com.baidu.tieba.funad.view.AbsFeedAdxView");
        hookHideViewByClassName(cl, "com.baidu.tieba.recapp.lego.view.AdCardBaseView");
        hookHideViewByClassName(cl, "com.baidu.tieba.funad.view.TbAdVideoView");
        hookHideViewByClassName(cl, "com.baidu.tieba.feed.ad.compact.DelegateFunAdView");
        hookHideViewByClassName(cl, "com.baidu.tieba.pb.pb.main.view.PbImageAlaRecommendView");
    }

    private void hookGfaAdFactory(ClassLoader cl) {
        // gfa.g() - ad view factory: return a 0-size empty view
        try {
            XposedHelpers.findAndHookMethod(
                "com.baidu.tieba.gfa", cl, "g",
                android.content.Context.class,
                "com.baidu.adp.BdUniqueId",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object result = param.getResult();
                        if (result instanceof View) {
                            squashView((View) result);
                        }
                    }
                }
            );
        } catch (Throwable t) {}

        // gfa.h() - ad data binding: skip entirely
        try {
            XposedBridge.hookAllMethods(
                XposedHelpers.findClass("com.baidu.tieba.gfa", cl), "h",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args != null && param.args.length > 0 && param.args[0] instanceof View) {
                            squashView((View) param.args[0]);
                        }
                        param.setResult(null);
                    }
                }
            );
        } catch (Throwable t) {}

        // gfa.i() - setVisibility wrapper: always force GONE
        try {
            XposedBridge.hookAllMethods(
                XposedHelpers.findClass("com.baidu.tieba.gfa", cl), "i",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args != null && param.args.length >= 2 && param.args[0] instanceof View) {
                            squashView((View) param.args[0]);
                            param.setResult(null);
                        }
                    }
                }
            );
        } catch (Throwable t) {}
    }

    private void hookFeedAdViewHolder(ClassLoader cl) {
        try {
            XposedBridge.hookAllConstructors(
                XposedHelpers.findClass("com.baidu.tieba.funad.adapter.FeedAdViewHolder", cl),
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            View itemView = (View) XposedHelpers.getObjectField(param.thisObject, "itemView");
                            if (itemView != null) squashView(itemView);
                        } catch (Throwable ignored) {}
                    }
                }
            );
        } catch (Throwable t) {}
    }

    // =============================================
    // Post page ad blocking (TypeAdapter/ListView)
    // =============================================

    /**
     * Block ads in the post page (PbLandscapeListView / TypeAdapter-based).
     * All ad view creation paths converge at TypeAdapter.getView().
     * If the returned view is an ad view, replace it with a 0-height empty view.
     */
    private void hookPostAdViews(final ClassLoader cl) {
        try {
            final Class<?> absFeedAdxViewClass = XposedHelpers.findClassIfExists("com.baidu.tieba.funad.view.AbsFeedAdxView", cl);
            final Class<?> adCardBaseViewClass = XposedHelpers.findClassIfExists("com.baidu.tieba.recapp.lego.view.AdCardBaseView", cl);
            final Class<?> tbAdVideoViewClass = XposedHelpers.findClassIfExists("com.baidu.tieba.funad.view.TbAdVideoView", cl);
            final Class<?> delegateFunAdViewClass = XposedHelpers.findClassIfExists("com.baidu.tieba.feed.ad.compact.DelegateFunAdView", cl);
            final Class<?> pbImageAlaRecommendViewClass = XposedHelpers.findClassIfExists("com.baidu.tieba.pb.pb.main.view.PbImageAlaRecommendView", cl);

            // Hook TypeAdapter.getView() — the SINGLE convergence point for ALL items
            XposedBridge.hookAllMethods(
                XposedHelpers.findClass("com.baidu.adp.widget.ListView.TypeAdapter", cl),
                "getView",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        View result = (View) param.getResult();
                        if (result == null) return;

                        if (isAdView(result, absFeedAdxViewClass, adCardBaseViewClass,
                                tbAdVideoViewClass, delegateFunAdViewClass, pbImageAlaRecommendViewClass)) {
                            param.setResult(createEmptyView(result.getContext()));
                        }
                    }
                }
            );

            // Hook hfd.getView() — the adapter presenter for lego ad cards
            // (AdCardSinglePicView, AdCardVideoView go through hfd → TypeAdapter)
            try {
                XposedBridge.hookAllMethods(
                    XposedHelpers.findClass("com.baidu.tieba.hfd", cl),
                    "getView",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            View result = (View) param.getResult();
                            if (result != null) {
                                param.setResult(createEmptyView(result.getContext()));
                            }
                        }
                    }
                );
            } catch (Throwable t) {}

            XposedBridge.log(TAG + ": Post page ad view interception installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to hook post page ads: " + t.getMessage());
        }
    }

    private static boolean isAdView(View view, Class<?>... adClasses) {
        for (Class<?> cls : adClasses) {
            if (cls != null && cls.isInstance(view)) return true;
        }
        return false;
    }

    private static View createEmptyView(android.content.Context context) {
        View emptyView = new View(context);
        emptyView.setLayoutParams(new android.widget.AbsListView.LayoutParams(0, 0));
        emptyView.setVisibility(View.GONE);
        return emptyView;
    }

    // =============================================
    // Utility hooks
    // =============================================

    private void hookReturnConstant(ClassLoader cl, String className, String methodName, final Object value) {
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(value);
                }
            });
        } catch (Throwable t) {}
    }

    private void hookSwitchManager(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.baidu.adp.lib.featureSwitch.SwitchManager", cl, "findType", String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        if (key != null && (key.equals("ad_baichuan_open")
                                || key.equals("bear_wxb_download")
                                || key.equals("pref_key_fun_ad_sdk_enable"))) {
                            param.setResult(0);
                        }
                    }
                }
            );
        } catch (Throwable t) {}
    }

    private void hookZga(ClassLoader cl) {
        try {
            XC_MethodHook safeStringHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.getResult() == null) {
                        param.setResult("");
                    }
                }
            };
            XposedHelpers.findAndHookMethod("com.baidu.tieba.zga", cl, "d", String.class, safeStringHook);
            XposedHelpers.findAndHookMethod("com.baidu.tieba.zga", cl, "f", String.class, safeStringHook);
        } catch (Throwable t) {}
    }

    private void hookFeedAppAdCard(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.baidu.tieba.recapp.lego.view.AdCardBaseView", cl, "x",
                "com.baidu.tieba.recapp.lego.model.AdCard",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object adCard = param.args[0];
                        if (adCard == null) return;
                        try {
                            Object advertAppInfo = XposedHelpers.callMethod(adCard, "getAdvertAppInfo");
                            if (advertAppInfo != null
                                    && (Boolean) XposedHelpers.callMethod(advertAppInfo, "isAppAdvert")) {
                                squashView((View) param.thisObject);
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            );
        } catch (Throwable t) {}
    }

    // =============================================
    // View-level ad hiding (for RecyclerView/Feed)
    // =============================================

    /**
     * Hook a view class to ensure it is always hidden and measured as 0-size.
     * Effective for RecyclerView-based lists (feed page).
     */
    private void hookHideViewByClassName(ClassLoader cl, String className) {
        try {
            final Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
            if (clazz == null) return;

            // On construction: squash immediately
            XposedBridge.hookAllConstructors(clazz, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View) squashView((View) param.thisObject);
                }
            });

            // On attach: squash again (view may have been recycled/re-laid-out)
            XposedBridge.hookAllMethods(clazz, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View) squashView((View) param.thisObject);
                }
            });

            // Prevent visibility changes
            XposedBridge.hookAllMethods(clazz, "setVisibility", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args != null && param.args.length >= 1 && param.args[0] instanceof Integer) {
                        param.args[0] = View.GONE;
                    }
                }
            });

            // Force 0-size measurement
            XposedBridge.hookAllMethods(clazz, "onMeasure", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        XposedHelpers.callMethod(param.thisObject, "setMeasuredDimension", 0, 0);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {}
    }

    /**
     * Set a view to GONE with 0 dimensions, padding, and margins.
     */
    private static void squashView(View view) {
        if (view == null) return;
        try {
            view.setVisibility(View.GONE);
            view.setMinimumHeight(0);
            view.setMinimumWidth(0);
            view.setPadding(0, 0, 0, 0);
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp != null) {
                lp.width = 0;
                lp.height = 0;
                if (lp instanceof ViewGroup.MarginLayoutParams) {
                    ((ViewGroup.MarginLayoutParams) lp).setMargins(0, 0, 0, 0);
                }
                view.setLayoutParams(lp);
            }
        } catch (Throwable ignored) {}
    }
}
