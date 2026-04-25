import SwiftUI

struct RootScreen: View {
    @AppStorage("auth_token") private var authToken: String = ""
    @AppStorage("auth_uuid") private var authUUID: String = ""
    @StateObject private var f1ViewModel: F1ViewModel

    init() {
        let manager = IOSBleManager()
        let repository = BleRepositoryImpl(manager: manager)
        _f1ViewModel = StateObject(wrappedValue: F1ViewModel(repository: repository))
    }

    static func hasSession(token: String, uuid: String) -> Bool {
        !token.isEmpty || !uuid.isEmpty
    }

    var body: some View {
        if !Self.hasSession(token: authToken, uuid: authUUID) {
            LoginScreen {
                authToken = UserDefaults.standard.string(forKey: "auth_token") ?? ""
                authUUID = UserDefaults.standard.string(forKey: "auth_uuid") ?? ""
            }
        } else {
            F1HomeScreen(viewModel: f1ViewModel) {
                UserDefaults.standard.set("", forKey: "auth_token")
                UserDefaults.standard.set("", forKey: "auth_uuid")
                authToken = ""
                authUUID = ""
            }
            .task {
                await PushDeviceReporter.reportCachedTokenIfPossible()
            }
        }
    }
}

#Preview {
    RootScreen()
}
