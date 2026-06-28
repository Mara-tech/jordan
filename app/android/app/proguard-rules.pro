# Gson reflection — keep field signatures and annotations
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }

# DTOs from jordan-core deserialized via Gson
-keep class com.mara.jordan.core.dto.** { *; }

# App model classes serialized/deserialized via Gson
-keep class com.mara.jordan.app.model.** { *; }

# Room — keep entity and DAO implementations generated at compile time
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Lombok — annotation-processor-generated getters/setters/builders are plain Java methods;
# keep them so Room, Gson, and Volley can reach them at runtime.
-keep @lombok.Builder class * { *; }
-keepclassmembers @lombok.Getter class * { get*(); }
-keepclassmembers @lombok.Setter class * { set*(...); }
-keepclassmembers @lombok.Data class * { *; }
