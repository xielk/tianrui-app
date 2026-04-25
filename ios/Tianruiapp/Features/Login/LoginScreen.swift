import SwiftUI

enum LoginLayout {
    static let horizontalPadding: CGFloat = 30
    static let primaryButtonHeight: CGFloat = 50
    static let buttonCornerRadius: CGFloat = 8
}

struct LoginScreen: View {
    @StateObject private var viewModel = LoginViewModel()
    @State private var selectedAgreementLink: LoginAgreementLink?
    let onLoginSuccess: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer().frame(height: 120)

            LoginLogoBlock()

            Spacer().frame(height: 60)

            SmsLoginForm(
                phone: $viewModel.phone,
                code: $viewModel.code,
                countdown: viewModel.countdown,
                isLoading: viewModel.isLoading,
                onSendCode: { viewModel.sendCode() },
                onSubmit: { viewModel.loginWithSms() }
            )

            Spacer().frame(height: 28)

            LoginAgreementRow(
                isAgree: viewModel.isAgree,
                onToggle: { viewModel.isAgree.toggle() },
                onOpenService: { selectedAgreementLink = .service },
                onOpenPrivacy: { selectedAgreementLink = .privacy }
            )

            Spacer(minLength: 0)
        }
        .padding(.horizontal, LoginLayout.horizontalPadding)
        .background(Color.white.ignoresSafeArea())
        .onChange(of: viewModel.isLoggedIn) { isLoggedIn in
            if isLoggedIn {
                onLoginSuccess()
            }
        }
        .fullScreenCover(item: $selectedAgreementLink) { link in
            NavigationStack {
                if let url = link.url {
                    LoginAgreementWebView(url: url)
                        .ignoresSafeArea(.all)
                        .navigationTitle(link.title)
                        .navigationBarTitleDisplayMode(.inline)
                        .toolbar {
                            ToolbarItem(placement: .topBarTrailing) {
                                Button("关闭") {
                                    selectedAgreementLink = nil
                                }
                            }
                        }
                }
            }
        }
        .alert("提示", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { isPresented in
                if !isPresented {
                    viewModel.errorMessage = nil
                }
            }
        )) {
            Button("确定", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
    }
}

#Preview {
    LoginScreen(onLoginSuccess: {})
}
