# PureShot / libxposed API 102
-dontwarn io.github.libxposed.**

# The Java entry class is referenced by META-INF/xposed/java_init.list.
# R8 may rename it, but the resource file is rewritten to match.
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# Kept stable for policy JSON / reflection used by later phases.
-keep class io.kazutoiris.pure.shot.hook.Feature { *; }
-keep class io.kazutoiris.pure.shot.hook.Override { *; }
-keep class io.kazutoiris.pure.shot.hook.AppPolicy { *; }
-keep class io.kazutoiris.pure.shot.hook.PerAppConfig { *; }
-keep class io.kazutoiris.pure.shot.PrefKeys { *; }
