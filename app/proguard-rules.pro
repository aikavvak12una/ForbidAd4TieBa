# Keep all module hook classes.
# Xposed callback/reflection paths are outside normal static call graph for R8.
-keep class com.forbidad4tieba.hook.** { *; }

# Xposed API is compileOnly — prevent R8 from removing references to it
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# Suppress warnings for deprecated Android widget
-dontwarn android.widget.Switch
