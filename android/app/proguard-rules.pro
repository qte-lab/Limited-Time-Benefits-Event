-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

-keepclassmembers class * {
    @androidx.compose.runtime.Composable public *;
}

-keepclassmembers class * {
    @androidx.compose.ui.tooling.preview.Preview public *;
}

-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keepattributes Signature
-keepattributes *Annotation*

-keep class coil.** { *; }
-dontwarn coil.**

-keep class top.yukonga.miuix.** { *; }
-dontwarn top.yukonga.miuix.**

-keep class androidx.navigation3.** { *; }
-dontwarn androidx.navigation3.**

# Navigation 3 persists the back stack by writing the fully qualified class name of every NavKey and
# restores it with Class.forName(name).kotlin.serializer(). The key classes and their generated
# kotlinx.serialization serializers must therefore survive shrinking and obfuscation.
-keep class * implements androidx.navigation3.runtime.NavKey { *; }
-keep,includedescriptorclasses class com.chronie.gift.ui.navigation.**$$serializer { *; }
-keepclassmembers class com.chronie.gift.ui.navigation.** {
    *** Companion;
    public static ** INSTANCE;
}
-keepclasseswithmembers class com.chronie.gift.ui.navigation.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * extends java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}