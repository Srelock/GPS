# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.motorider.**$$serializer { *; }
-keepclassmembers class com.motorider.** {
    *** Companion;
}
-keepclasseswithmembers class com.motorider.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room entities
-keep class com.motorider.data.entity.** { *; }

# Keep Retrofit models
-keep class com.motorider.data.remote.** { *; }

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
