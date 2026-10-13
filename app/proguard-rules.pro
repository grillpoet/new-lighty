# ==================== kotlinx.serialization ====================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.grillpoet.lighty.**$$serializer { *; }
-keepclassmembers class com.grillpoet.lighty.** {
    *** Companion;
}
-keepclasseswithmembers class com.grillpoet.lighty.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ==================== jaudiotagger ====================
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# ==================== OkHttp ====================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ==================== Kotlin 元数据 ====================
-keep class kotlin.Metadata { *; }



-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }

# ==================== Jsoup ====================
-dontwarn org.jsoup.**
-keep class org.jsoup.** { *; }

# ==================== 删日志 ====================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}