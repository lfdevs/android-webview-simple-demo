# android-webview-simple-demo

A Simple Demo of Android Webview

## Tips

1. For Linux arm64 users, You need to use build-tools built **NATIVELY** for Linux arm64. Please uncomment the following section in
   the `gradle.properties` file and change the `aapt2` path to your own.

   ```properties
   #android.aapt2FromMavenOverride=/home/lf/Android/Sdk/build-tools/35.0.1/aapt2
   ```

2. If you need to change the default URL of the Webview, please modify the `webview_url` string in
   `app/src/main/res/values/strings.xml`.

   ```xml
   <string name="webview_url">https://ys.mihoyo.com/cloud/m/</string>
   ```
