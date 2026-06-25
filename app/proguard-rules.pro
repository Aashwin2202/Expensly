# Google Sign-In
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.fintrackai.data.remote.** { *; }
-keep class com.fintrackai.domain.model.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes EnclosingMethod

# unicode-emoji-json Gson model
-keep class com.fintrackai.domain.category.UnicodeEmojiRecord { *; }

# Room
-keep class * extends androidx.room.RoomDatabase

# WorkManager + Hilt workers
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keep class androidx.hilt.** { *; }
