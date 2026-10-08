import SwiftUI
import FirebaseCore
import ComposeApp

@main
struct iOSApp: App {
    init() {
        // GoogleService-Info.plist comes from Doppler (scripts/doppler-sync-local.sh); without it the
        // app runs and shows the "Firebase missing" screen instead of crashing.
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Universal Links (https://owlsplit.web.app/j/<code>) and any URL the app is opened with.
                .onOpenURL { url in
                    MainViewControllerKt.handleIncomingLink(url: url.absoluteString)
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL {
                        MainViewControllerKt.handleIncomingLink(url: url.absoluteString)
                    }
                }
        }
    }
}
