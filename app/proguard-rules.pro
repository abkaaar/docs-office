# Docs Office release / staging R8 rules

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Tink
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Play Billing
-keep class com.android.billingclient.** { *; }

# PdfBox-Android
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.commons.**
-dontwarn org.bouncycastle.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Hilt / Dagger
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Enums used in Room / DataStore
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
