#if SWIFT_PACKAGE
import Flutter

@objc(TwitterLoginPlugin)
public class TwitterLoginPlugin: NSObject, FlutterPlugin {
    public static func register(with registrar: FlutterPluginRegistrar) {
        SwiftTwitterLoginPlugin.register(with: registrar)
    }
}
#endif
