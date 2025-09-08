# Keep Gowit SDK public API
-keep public class com.gowit.sdk.GowitSdk { *; }
-keep public class com.gowit.sdk.model.** { *; }
-keep public interface com.gowit.sdk.** { *; }

# Keep callback interfaces
-keep interface com.gowit.sdk.callback.** { *; }

# Keep data classes and their fields
-keepclassmembers class com.gowit.sdk.model.** {
    *;
}
