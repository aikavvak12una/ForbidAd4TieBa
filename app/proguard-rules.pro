# Keep the entire MainHook class and all its members (methods, fields)
# R8 cannot trace Xposed reflection calls, so we must keep everything
-keep class com.forbidad4tieba.hook.MainHook { *; }

# Keep all inner/nested classes (Companion, SquashState, anonymous XC_MethodHook subclasses)
-keep class com.forbidad4tieba.hook.MainHook$* { *; }

# Xposed API is compileOnly — prevent R8 from removing references to it
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# Suppress warnings for deprecated Android widget
-dontwarn android.widget.Switch