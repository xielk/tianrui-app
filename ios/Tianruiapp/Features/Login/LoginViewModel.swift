import Foundation

@MainActor
final class LoginViewModel: ObservableObject {
    @Published var phone: String = ""
    @Published var code: String = ""
    @Published var isAgree: Bool = true
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var countdown: Int = 0
    @Published var isLoggedIn: Bool = false

    private var countdownTask: Task<Void, Never>?

    func sendCode() {
        guard countdown == 0 else { return }
        guard phone.count >= 11 else {
            errorMessage = "请先输入正确手机号"
            return
        }

        Task {
            isLoading = true
            defer { isLoading = false }

            do {
                try await AuthAPIClient.shared.sendVerificationCode(phone: phone)
                startCountdown()
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func loginWithSms() {
        guard !isLoading else { return }
        guard isAgree else {
            errorMessage = "请先阅读并同意协议"
            return
        }
        guard phone.count >= 11, code.count >= 4 else {
            errorMessage = "请输入手机号和验证码"
            return
        }

        Task {
            isLoading = true
            defer { isLoading = false }

            do {
                let response = try await AuthAPIClient.shared.loginWithSms(phone: phone, code: code)
                UserDefaults.standard.set(response.token, forKey: "auth_token")
                UserDefaults.standard.set(response.uuid, forKey: "auth_uuid")
                Task {
                    await PushDeviceReporter.reportCachedTokenIfPossible()
                }
                isLoggedIn = true
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    private func startCountdown() {
        countdownTask?.cancel()
        countdown = 60

        countdownTask = Task { [weak self] in
            while let self, self.countdown > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                if Task.isCancelled { return }
                await MainActor.run {
                    self.countdown -= 1
                }
            }
        }
    }
}
