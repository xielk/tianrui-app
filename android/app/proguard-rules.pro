# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# AMap 2D Map ProGuard Rules
-keep class com.amap.api.maps2d.** { *; }
-keep class com.amap.api.mapcore2d.** { *; }
-keep class com.autonavi.amap.mapcore2d.** { *; }

# Ignore missing location SDK classes (since we only use map2d)
-dontwarn com.amap.api.location.**
-dontwarn com.amap.api.mapcore2d.**
-dontwarn com.autonavi.amap.mapcore2d.**

# Jiguang (TPNS) ProGuard Rules
-dontwarn com.jg.EType
-dontwarn com.jg.JgClassChecked
-dontwarn com.jg.JgMethodChecked
