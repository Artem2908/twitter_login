# Android regression tests

The Robolectric tests exercise Custom Tabs restoration, missing login parameters,
browser cancellation callbacks, and channel cleanup on Android API 33.

Connect this checkout to a Flutter host application through a path dependency and
run `flutter pub get`. From the host application's `android` directory, run:

```sh
./gradlew --init-script /absolute/path/to/twitter_login/android/test-init.gradle :twitter_login:testDebugUnitTest
```

Use JDK 17 or newer with an Android Gradle Plugin 8 host. The init script raises
only the test invocation's plugin compile SDK to 34 and minimum SDK to 24 so the
test APK can include current Flutter embedding dependencies. It does not change
the plugin's normal build configuration or the host application's settings.

The tests do not contact Twitter. Before releasing, also check a successful login,
closing the browser with Back and the close button, retrying after cancellation,
and returning to the app after its background process has been killed.
