import SwiftUI
import WebKit

enum LoginAgreementLink: String, Identifiable {
    case service
    case privacy

    var id: String { rawValue }

    var title: String {
        switch self {
        case .service:
            return "用户协议"
        case .privacy:
            return "隐私政策"
        }
    }

    var urlString: String {
        switch self {
        case .service:
            return "https://cdn.tr.sheyutech.com/service.html"
        case .privacy:
            return "https://cdn.tr.sheyutech.com/privacy.html"
        }
    }

    var url: URL? { URL(string: urlString) }
}

struct LoginLogoBlock: View {
    var body: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .stroke(AppColor.primaryBlue, lineWidth: 3)
                    .frame(width: 124, height: 124)

                Image("app_logo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 50, height: 50)
            }

            Text("天瑞智行")
                .font(AppTypography.loginTitle)
                .foregroundStyle(.black)
        }
    }
}

struct SmsLoginForm: View {
    @Binding var phone: String
    @Binding var code: String
    let countdown: Int
    let isLoading: Bool
    let onSendCode: () -> Void
    let onSubmit: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            TextField("请输入手机号", text: $phone)
                .keyboardType(.numberPad)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .foregroundStyle(.black)
                .padding(.horizontal, 14)
                .frame(height: 48)
                .background(.white)
                .overlay {
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .stroke(Color(red: 0.85, green: 0.88, blue: 0.93), lineWidth: 1)
                }

            HStack(spacing: 10) {
                TextField("请输入验证码", text: $code)
                    .keyboardType(.numberPad)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .foregroundStyle(.black)
                    .padding(.horizontal, 14)
                    .frame(height: 48)
                    .background(.white)
                    .overlay {
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .stroke(Color(red: 0.85, green: 0.88, blue: 0.93), lineWidth: 1)
                    }

                Button(action: onSendCode) {
                    Text(countdown > 0 ? "\(countdown)秒" : "发送验证码")
                        .font(.system(size: 14, weight: .medium))
                        .frame(width: 106, height: 48)
                }
                .disabled(isLoading || countdown > 0)
                .buttonStyle(SecondaryLoginButtonStyle())
            }

            Button(action: onSubmit) {
                Text(isLoading ? "登录中..." : "登录")
                    .frame(maxWidth: .infinity)
                    .frame(height: LoginLayout.primaryButtonHeight)
            }
            .disabled(isLoading)
            .buttonStyle(PrimaryLoginButtonStyle())

        }
    }
}

struct PrimaryLoginButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(AppTypography.buttonText)
            .foregroundStyle(.white)
            .background(AppColor.primaryBlue)
            .clipShape(RoundedRectangle(cornerRadius: LoginLayout.buttonCornerRadius, style: .continuous))
    }
}

struct SecondaryLoginButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(AppTypography.buttonText)
            .foregroundStyle(AppColor.primaryBlue)
            .background(Color.clear)
            .overlay {
                RoundedRectangle(cornerRadius: LoginLayout.buttonCornerRadius, style: .continuous)
                    .stroke(AppColor.primaryBlue, lineWidth: 1.5)
            }
    }
}

struct LoginAgreementRow: View {
    let isAgree: Bool
    let onToggle: () -> Void
    let onOpenService: () -> Void
    let onOpenPrivacy: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: isAgree ? "checkmark.square.fill" : "square")
                .font(.system(size: 26, weight: .semibold))
                .foregroundStyle(AppColor.softPurple)
                .onTapGesture(perform: onToggle)

            Text("我已阅读并同意")
                .font(AppTypography.agreementText)
                .foregroundStyle(Color(red: 0.30, green: 0.31, blue: 0.35))

            Text("《用户协议》")
                .font(AppTypography.agreementText)
                .foregroundStyle(AppColor.primaryBlue)
                .underline()
                .onTapGesture(perform: onOpenService)

            Text("和")
                .font(AppTypography.agreementText)
                .foregroundStyle(Color(red: 0.30, green: 0.31, blue: 0.35))

            Text("《隐私政策》")
                .font(AppTypography.agreementText)
                .foregroundStyle(AppColor.primaryBlue)
                .underline()
                .onTapGesture(perform: onOpenPrivacy)
        }
        .lineLimit(1)
        .minimumScaleFactor(0.8)
    }
}

struct LoginAgreementWebView: UIViewRepresentable {
    let url: URL

    func makeUIView(context: Context) -> WKWebView {
        let view = WKWebView(frame: .zero)
        view.allowsBackForwardNavigationGestures = true
        return view
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        if uiView.url != url {
            uiView.load(URLRequest(url: url))
        }
    }
}
