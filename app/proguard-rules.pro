# ==============================================================================
# DragWeb ProGuard & R8 Configuration
# ==============================================================================

# ------------------------------------------------------------------------------
# General & Attributes Preservation
# ------------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes Exceptions

# Enable aggressive code optimizations in R8 full mode
-allowaccessmodification
-repackageclasses 'glab.dragweb.obf'

# ------------------------------------------------------------------------------
# Android Components & Custom Views
# ------------------------------------------------------------------------------
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.fragment.app.Fragment

-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep Custom UI and ColorPicker views
-keep class glab.dragweb.ui.** { *; }
-keep class glab.dragweb.colorpicker.** { *; }

# ------------------------------------------------------------------------------
# GSON & Data Models (Preserve all fields for JSON serialization/deserialization)
# ------------------------------------------------------------------------------
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep all models, logic beans, and data entities serialized with GSON
-keep class glab.dragweb.models.** { *; }
-keep class glab.dragweb.logic.BlockBean { *; }
-keep class glab.dragweb.logic.BlockDef { *; }
-keep class glab.dragweb.logic.CategoryDef { *; }
-keep class glab.dragweb.logic.LogicBlock { *; }
-keep class glab.dragweb.logic.ManageBlocksWidgets$** { *; }
-keep class glab.dragweb.data.DesignDataManager$** { *; }
-keep class glab.dragweb.data.ProjectDataManager$** { *; }
-keep class glab.dragweb.data.GoogleFontsManager$** { *; }
-keep class glab.dragweb.fragments.EventsFragment$** { *; }
-keep class glab.dragweb.engine.HtmlCssImporter$** { *; }

# Keep serialized fields
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ------------------------------------------------------------------------------
# WebView & Javascript Interface
# ------------------------------------------------------------------------------
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class glab.dragweb.activities.TextEditorActivity$* {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class glab.dragweb.activities.PreviewActivity$* {
    @android.webkit.JavascriptInterface <methods>;
}

# ------------------------------------------------------------------------------
# Enums, Parcelable & Serializable
# ------------------------------------------------------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ------------------------------------------------------------------------------
# Strip Debug Logging in Release Builds
# ------------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Suppress harmless warnings
-dontwarn sun.misc.**
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
