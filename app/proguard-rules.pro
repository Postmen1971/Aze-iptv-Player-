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

# Umschalten: die App schaltet „dynamic scheduling“ von Media3 per Namen ab (Name je nach Media3-Version anders) –
# ohne diese Regel benennt R8 die Methode um bzw. entfernt sie, und der Aufruf geht ins Leere.
-keepclassmembers class androidx.media3.exoplayer.ExoPlayer$Builder {
    *** *DynamicScheduling*(...);
}
