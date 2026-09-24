# The page calls these methods by name through the JavaScript bridge.
-keepclassmembers class com.focusfriend.app.FocusBridge {
    @android.webkit.JavascriptInterface <methods>;
}
