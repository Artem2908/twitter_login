# Android regression tests

The Robolectric tests exercise Custom Tabs restoration, missing login parameters,
browser cancellation callbacks, and channel cleanup on Android API 33. They also
verify that repeated service connections and activity restarts or recreation do
not launch another browser, while a new login after cancellation still works.

Connect this checkout to a Flutter host application through a path dependency and
run `flutter pub get`. From the host application's `android` directory, run:

```sh
./gradlew --init-script /absolute/path/to/twitter_login/android/test-init.gradle :twitter_login:testDebugUnitTest
```

Use JDK 17 or newer with an Android Gradle Plugin 8 host. The init script raises
only the test invocation's plugin compile SDK to 34 and minimum SDK to 24 so the
test APK can include current Flutter embedding dependencies. It does not change
the plugin's normal build configuration or the host application's settings.

The tests do not contact Twitter or drive Chrome's back gesture. Before releasing,
also check these scenarios on a physical device with gesture navigation enabled:

1. Open Twitter login and complete a slow Back swipe. The browser should close
   without opening another login tab. Repeat after starting a new login.
2. Start a Back swipe, then cancel the gesture. Login should remain open and usable.
3. Close login with the browser's close button and retry.
4. Rotate the device or put the app in the background while login is open, return,
   then close it. No additional login tab should appear.
5. Complete a successful login.
6. Return to the app after its background process has been killed; it should not
   crash when the browser activity is restored.
