# Optimierung (R8) für den Release-Build – macht die Oberfläche spürbar flüssiger.

# VLC: Die native Bibliothek ruft Java-Klassen per Name auf
-keep class org.videolan.** { *; }
-dontwarn org.videolan.**

# Brücken zwischen WebView (SkorText, YouTube) und App
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# Fehlermeldungen lesbar halten
-keepattributes SourceFile,LineNumberTable
