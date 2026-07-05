# Borg Pharmacy - ProGuard / R8 rules
# Note: minification is currently DISABLED in build.gradle.kts for maximum
# runtime safety. These rules are provided so R8 can be safely enabled later
# without stripping Compose, Room, Supabase or Ktor reflection-based code.

# ---- Kotlin / general ----
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod
-dontwarn javax.annotation.**

# ---- Kotlin Coroutines ----
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ---- Jetpack Compose ----
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ---- Kotlinx Serialization (used by Supabase/Ktor) ----
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep,includedescriptorclasses class com.borg.pharmacy.**$$serializer { *; }
-keepclassmembers class com.borg.pharmacy.** {
    *** Companion;
}

# ---- Ktor ----
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# ---- Supabase ----
-dontwarn io.github.jan.supabase.**
-keep class io.github.jan.supabase.** { *; }

# ---- Keep app data models (serialization) ----
-keep class com.borg.pharmacy.data.local.entity.** { *; }
