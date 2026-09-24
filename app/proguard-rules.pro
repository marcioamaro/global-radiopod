# ProGuard/R8 rules for Global RadioPod

# Preserve line numbers and source files for readable Logcat crash traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

# Android Auto / Media3 Session & Player
-keep class androidx.media3.session.** { *; }
-keep interface androidx.media3.session.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.decoder.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media.app.** { *; }
-keep class android.support.v4.media.** { *; }
-keep class androidx.core.app.NotificationCompat** { *; }

# Android Auto / Car App Library
-keep class androidx.car.app.** { *; }
-keep interface androidx.car.app.** { *; }

# Data Models, Room & Moshi Reflection
-keep class com.marcioamaro.mediapod.data.model.** { *; }
-keep class com.marcioamaro.mediapod.data.db.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# Moshi JSON serialization
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
    @com.squareup.moshi.JsonClass *;
}

# Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# Keep service and receiver declarations
-keep class com.marcioamaro.mediapod.service.RadioMediaService { *; }

# ViewModels and Lifecycle reflection/factory compatibility
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
    <init>(android.app.Application);
}
-keep class com.marcioamaro.mediapod.ui.onboarding.** { *; }

