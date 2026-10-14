import SwiftUI
import FirebaseAppCheck
import FirebaseCore
import GoogleSignIn
import ComposeApp

@main
struct iOSApp: App {
    init() {
        // GoogleService-Info.plist comes from Doppler (scripts/doppler-sync-local.sh); without it the
        // app runs and shows the "Firebase missing" screen instead of crashing.
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            // App Check (docs/FIREBASE.md#app-check), before configure so every request has a token.
            // Not enforced yet. Debug builds print their debug token in the Xcode console; release
            // builds use DeviceCheck (App Attest needs the Apple Team ID and its entitlement: pending).
            #if DEBUG
            AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
            #else
            AppCheck.setAppCheckProviderFactory(DeviceCheckProviderFactory())
            #endif
            FirebaseApp.configure()
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Universal Links (https://owlsplit.web.app/j/<code>) and any URL the app is opened with.
                .onOpenURL { url in
                    // Google sign-in returns through its URL scheme; anything else may be an invite.
                    if !GIDSignIn.sharedInstance.handle(url) {
                        MainViewControllerKt.handleIncomingLink(url: url.absoluteString)
                    }
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL {
                        MainViewControllerKt.handleIncomingLink(url: url.absoluteString)
                    }
                }
        }
    }
}
