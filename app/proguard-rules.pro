# Keep the libxposed entry point referenced from META-INF/xposed/java_init.list.
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep class com.forbidad4tieba.hook.MainHook { *; }
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
