# Keep the libxposed entry point referenced from META-INF/xposed/java_init.list.
-keep class com.forbidad4tieba.hook.MainHook { *; }
-keep class * extends io.github.libxposed.api.XposedModule { *; }
