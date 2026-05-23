# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.aistock.analysis.**$$serializer { *; }
-keepclassmembers class com.aistock.analysis.** {
    *** Companion;
}
-keepclasseswithmembers class com.aistock.analysis.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Okio
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Play Billing
-keep class com.android.vending.billing.** { *; }

# Credential Manager / GoogleId
-keep class com.google.android.libraries.identity.googleid.** { *; }
