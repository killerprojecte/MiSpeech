// Top-level build file where you can add configuration options common to all sub-projects/modules.
// 两个 AGP 插件都必须在这里声明版本（apply false），否则在同一个 build 里
// 同时使用 com.android.library 与 com.android.application 会因 classpath 版本未知而报错。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}
