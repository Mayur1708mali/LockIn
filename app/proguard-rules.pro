# ProGuard and R8 rules for LockIn Android Client
# Why: Optimizes and shrinks the application package size while preventing runtime crashes
# due to obfuscation of reflection/serialization endpoints.

# --- Retrofit & OkHttp Network Rules ---
-keepattributes Signature, InnerClasses, AnnotationDefault, EnclosingMethod, *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**
-keep class retrofit2.** { *; }
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}

# --- Gson / Serialization DTOs ---
# Protects the network data models to ensure Gson serialization and parsing does not break.
-keep class com.lockin.app.core.data.remote.dto.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- Dagger Hilt Rules ---
-keep public class * extends android.app.Service
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends androidx.lifecycle.ViewModel
-keep class com.lockin.app.di.** { *; }
-keep class * implements hilt.internal.GeneratedComponent

# --- Room Database ---
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.RoomDatabase
-keep class com.lockin.app.core.data.local.dao.** { *; }
-keep class com.lockin.app.core.data.local.entity.** { *; }
-keep class * extends androidx.room.RoomOpenHelper

# --- RootBeer Root Detection ---
-keep class com.scottyab.rootbeer.RootBeer {
    public boolean isRooted();
    public boolean isRootedWithoutBusyBoxCheck();
}
-keep class com.scottyab.rootbeer.util.QLog { *; }
-dontwarn com.scottyab.rootbeer.**

# --- Razorpay Checkout SDK ---
# Prevents JavascriptInterface methods and internal checkout mechanisms from breaking.
-keep class com.razorpay.** { *; }
-dontwarn com.razorpay.**
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.razorpay.CheckoutActivity { *; }
