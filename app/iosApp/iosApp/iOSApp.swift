import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        IosPlatformModuleKt.startKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
