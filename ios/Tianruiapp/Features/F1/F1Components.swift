import SwiftUI

enum F1MapURLBuilder {
    private static let fallbackAmapStaticKey = "028f30294d904b08d1a7e1150a3d7c74"

    static func staticMapURL(latitude: Double, longitude: Double) -> URL? {
        guard latitude != 0, longitude != 0 else { return nil }

        let apiKey: String
        if let value = Bundle.main.object(forInfoDictionaryKey: "AMapStaticMapAPIKey") as? String,
           !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            apiKey = value
        } else {
            apiKey = fallbackAmapStaticKey
        }

        var components = URLComponents()
        components.scheme = "https"
        components.host = "restapi.amap.com"
        components.path = "/v3/staticmap"
        components.queryItems = [
            URLQueryItem(name: "key", value: apiKey),
            URLQueryItem(name: "location", value: "\(longitude),\(latitude)"),
            URLQueryItem(name: "zoom", value: "16"),
            URLQueryItem(name: "size", value: "500*300"),
            URLQueryItem(name: "markers", value: "mid,0xFF0000,A:\(longitude),\(latitude)"),
            URLQueryItem(name: "scale", value: "2"),
        ]
        return components.url
    }
}

enum F1MapAddressResolver {
    static func displayAddress(address: String, latitude: Double, longitude: Double) -> String {
        let trimmed = address.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty {
            return trimmed
        }
        return (latitude != 0 && longitude != 0) ? "获取地址中" : "位置信息不可用"
    }
}

enum F1MapSectionMode: Equatable {
    case single
    case split

    static func forLayout(_ layout: F1ViewModel.LayoutMode) -> F1MapSectionMode {
        layout == .f2 ? .split : .single
    }
}

enum F1MapAddressVisibilityPolicy {
    static func showsAddress(for layout: F1ViewModel.LayoutMode) -> Bool {
        layout != .f2
    }
}

enum F1ControlChannel: Equatable {
    case ble
    case cellular4G
    case none
}

enum F1ControlChannelPolicy {
    static func select(isF2: Bool, bleReady: Bool, networkOnline: Bool) -> F1ControlChannel {
        if bleReady { return .ble }
        if isF2 && networkOnline { return .cellular4G }
        return .none
    }
}

struct F1HeaderView: View {
    let deviceName: String
    let showVehicleSelector: Bool
    let totalMileage: String
    let bleIconName: String
    let bleIconBlinking: Bool
    let bleSystemConnected: Bool
    let show4GIcon: Bool
    let signalStrengthLevel: Int
    let onScanTap: () -> Void
    let onBluetoothTap: () -> Void
    let onBlePairedTap: () -> Void
    let onVehicleTap: () -> Void

    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 6) {
                if showVehicleSelector {
                    Button(action: onVehicleTap) {
                        HStack(spacing: 4) {
                            Text(deviceName)
                                .font(.system(size: 21, weight: .heavy))
                                .foregroundStyle(AppColor.titleBlack)
                            Image(systemName: "chevron.down")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundStyle(Color(red: 0.26, green: 0.33, blue: 0.45))
                        }
                    }
                    .buttonStyle(.plain)
                }

                Text("历史总里程：  \(totalMileage)")
                    .font(.system(size: 15, weight: .regular))
                    .foregroundStyle(Color(red: 0.18, green: 0.23, blue: 0.33))
            }

            Spacer(minLength: 8)

            HStack(spacing: 18) {
                Button(action: onScanTap) {
                    Image("f2_scan")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 28, height: 28)
                }
                .buttonStyle(.plain)

                Button(action: onBluetoothTap) {
                    F1BlinkingIcon(imageName: bleIconName, isBlinking: bleIconBlinking)
                }
                .buttonStyle(.plain)

                if bleSystemConnected {
                    Button(action: onBlePairedTap) {
                        Image("ble_paired")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 40, height: 40)
                    }
                    .buttonStyle(.plain)
                }

                if show4GIcon {
                    F1SignalBadgeView(level: signalStrengthLevel)
                        .frame(width: 40, height: 40)
                }
            }
            .foregroundStyle(AppColor.titleBlack)
            .padding(.top, 10)
        }
    }
}

struct F1SignalBadgeView: View {
    let level: Int

    var body: some View {
        ZStack {
            Circle()
                .fill(Color.white.opacity(0.34))
                .frame(width: 38, height: 38)

            HStack(alignment: .bottom, spacing: 1.2) {
                ForEach(0..<5, id: \.self) { index in
                    RoundedRectangle(cornerRadius: 0.8, style: .continuous)
                        .fill(index < activeBarCount ? Color(red: 0.35, green: 0.40, blue: 0.50) : Color(red: 0.73, green: 0.77, blue: 0.83))
                        .frame(width: 2.6, height: CGFloat(5 + index * 2))
                }
            }
        }
    }

    private var activeBarCount: Int {
        max(0, min(5, level))
    }
}

private struct F1BlinkingIcon: View {
    let imageName: String
    let isBlinking: Bool
    @State private var isDimmed = false

    var body: some View {
        Image(imageName)
            .resizable()
            .scaledToFit()
            .frame(width: 28, height: 28)
            .opacity(isBlinking && isDimmed ? 0.35 : 1)
            .onAppear {
                updateBlinkingState()
            }
            .onChange(of: isBlinking) { _ in
                updateBlinkingState()
            }
    }

    private func updateBlinkingState() {
        guard isBlinking else {
            isDimmed = false
            return
        }

        withAnimation(.easeInOut(duration: 0.52).repeatForever(autoreverses: true)) {
            isDimmed = true
        }
    }
}

struct F1HeroView: View {
    let voltage: String
    let batteryPercent: String

    var body: some View {
        ZStack {
            Image("f2_bike")
                .resizable()
                .scaledToFit()
                .frame(maxWidth: .infinity)
                .frame(height: 252)
                .padding(.top, 12)

            HStack(alignment: .top) {
                ZStack {
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [Color(red: 0.17, green: 0.19, blue: 0.46), Color(red: 0.02, green: 0.05, blue: 0.16)],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )

                    VStack {
                        Spacer(minLength: 0)
                        Text(batteryPercent)
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(.bottom, 14)
                    }
                }
                .frame(width: 33, height: 99)
                .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                .offset(y: -38)

                Spacer()

                VStack(alignment: .leading, spacing: 8) {
                    Text("挪车")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color(red: 0.00, green: 0.84, blue: 0.50))

                    Text("电压:  \(voltage)")
                        .font(.system(size: 12, weight: .regular))
                        .foregroundStyle(Color(red: 0.84, green: 0.88, blue: 1.00))
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [Color(red: 0.18, green: 0.20, blue: 0.47), Color(red: 0.12, green: 0.14, blue: 0.32)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                )
                .frame(width: 128, height: 84, alignment: .leading)
                .offset(y: -82)
            }
            .padding(.top, 0)
        }
        .frame(height: 276)
    }
}

struct F1MainControlCardView: View {
    @ObservedObject var viewModel: F1ViewModel

    var body: some View {
        VStack(spacing: 16) {
            HStack(alignment: .top, spacing: 10) {
                F1SquareControl(
                    title: viewModel.autoSenseTitle,
                    imageName: viewModel.isAutoSenseEnabled ? "f2_ble_open" : "f2_ble_close",
                    systemImageName: nil,
                    isActive: viewModel.isAutoSenseEnabled,
                    isEnabled: viewModel.hasAnyChannel,
                    action: viewModel.toggleAutoSense
                )

                F1SliderControl(
                    isEnabled: viewModel.hasAnyChannel,
                    isLocked: viewModel.isLocked,
                    onLockChanged: viewModel.setLock
                )

                F1SquareControl(
                    title: viewModel.muteTitle,
                    imageName: viewModel.isMuteEnabled ? "f2_mute" : nil,
                    systemImageName: viewModel.isMuteEnabled ? nil : "speaker.wave.2.fill",
                    isActive: viewModel.isMuteEnabled,
                    isEnabled: viewModel.hasAnyChannel,
                    action: viewModel.toggleMute
                )
            }

            Divider()

            HStack(spacing: 24) {
                F1ActionItem(imageName: "f2_find_bike", title: "寻车功能", action: viewModel.findBike)
                F1ActionItem(imageName: "f2_set", title: "设置", action: viewModel.onSettingsTap)
                F1ActionItem(imageName: "f2_user", title: "用车人", action: viewModel.onShareTap)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 20)
        .background(
            RoundedRectangle(cornerRadius: 34, style: .continuous)
                .fill(.white)
                .shadow(color: AppShadow.card.color, radius: AppShadow.card.radius, x: AppShadow.card.x, y: AppShadow.card.y)
        )
    }
}

struct F1TripMetricsCardView: View {
    let timeText: String?
    let mileageText: String
    let durationText: String
    let topSpeedText: String
    let averageSpeedText: String

    var body: some View {
        RoundedRectangle(cornerRadius: 20, style: .continuous)
            .fill(.white)
            .frame(height: (timeText?.isEmpty == false) ? 136 : 118)
            .overlay {
                VStack(spacing: 8) {
                    if let timeText, !timeText.isEmpty {
                        Text(timeText)
                            .font(.system(size: 14, weight: .regular))
                            .foregroundStyle(Color(red: 0.44, green: 0.49, blue: 0.56))
                    }
                    HStack(spacing: 0) {
                        metricItem(value: mileageText, title: "里程")
                        metricItem(value: durationText, title: "耗时")
                        metricItem(value: topSpeedText, title: "极速")
                        metricItem(value: averageSpeedText, title: "匀速")
                    }
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 16)
            }
    }

    private func metricItem(value: String, title: String) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.system(size: 22, weight: .regular))
                .foregroundStyle(Color(red: 0.08, green: 0.11, blue: 0.18))
                .lineLimit(1)
                .minimumScaleFactor(0.75)
            Text(title)
                .font(.system(size: 13, weight: .regular))
                .foregroundStyle(Color(red: 0.44, green: 0.49, blue: 0.56))
        }
        .frame(maxWidth: .infinity)
    }
}

private struct F1SquareControl: View {
    let title: String
    let imageName: String?
    let systemImageName: String?
    let isActive: Bool
    let isEnabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 7) {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: isActive
                                ? [Color(red: 0.31, green: 0.24, blue: 0.53), Color(red: 0.09, green: 0.12, blue: 0.21)]
                                : [Color(red: 0.18, green: 0.20, blue: 0.47), Color(red: 0.02, green: 0.05, blue: 0.16)],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .frame(width: 58, height: 58)
                    .overlay {
                        if let imageName {
                            Image(imageName)
                                .renderingMode(.template)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 24, height: 24)
                                .foregroundStyle(.white)
                        } else if let systemImageName {
                            Image(systemName: systemImageName)
                                .font(.system(size: 21, weight: .semibold))
                                .foregroundStyle(.white)
                        }
                    }
                    .animation(.easeInOut(duration: 0.2), value: isActive)

                Text(title)
                    .font(.system(size: 10, weight: .regular))
                    .foregroundStyle(AppColor.secondaryText)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                    .frame(width: 58)
            }
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .opacity(isEnabled ? 1 : 0.55)
        .frame(width: 58)
    }
}

private struct F1SliderControl: View {
    let isEnabled: Bool
    let isLocked: Bool
    let onLockChanged: (Bool) -> Void

    var body: some View {
        F1LockSliderTrack(isEnabled: isEnabled, isLocked: isLocked, onLockChanged: onLockChanged)
        .disabled(!isEnabled)
        .opacity(isEnabled ? 1 : 0.55)
    }
}

private struct F1LockSliderTrack: View {
    let isEnabled: Bool
    let isLocked: Bool
    let onLockChanged: (Bool) -> Void

    private let trackWidth: CGFloat = 175
    private let trackHeight: CGFloat = 65
    private let thumbSize: CGFloat = 44
    private let horizontalPadding: CGFloat = 6

    @State private var dragTranslation: CGFloat = 0
    @State private var visualLocked: Bool = true

    private var maxOffset: CGFloat {
        max(trackWidth - thumbSize - horizontalPadding * 2, 0)
    }

    private var currentLockedState: Bool {
        visualLocked
    }

    private var baseOffset: CGFloat {
        currentLockedState ? 0 : maxOffset
    }

    private var currentOffset: CGFloat {
        min(max(baseOffset + dragTranslation, 0), maxOffset)
    }

    private var hintAlpha: Double {
        guard maxOffset > 0 else { return 1 }
        let progress = min(max(currentOffset / maxOffset, 0), 1)
        if currentLockedState {
            return max(0, min(1, 1 - progress * 2))
        }
        return max(0, min(1, progress * 2 - 1))
    }

    private var backgroundGradient: LinearGradient {
        let colors: [Color]
        if !isEnabled {
            colors = [Color(red: 0.54, green: 0.56, blue: 0.60), Color(red: 0.36, green: 0.38, blue: 0.42)]
        } else if currentLockedState {
            colors = [Color(red: 0.19, green: 0.21, blue: 0.37), Color(red: 0.04, green: 0.06, blue: 0.10)]
        } else {
            colors = [Color(red: 0.82, green: 0.04, blue: 0.94), Color(red: 0.47, green: 0.10, blue: 0.84)]
        }
        return LinearGradient(colors: colors, startPoint: .leading, endPoint: .trailing)
    }

    var body: some View {
        let gesture = DragGesture(minimumDistance: 2)
            .onChanged { value in
                guard isEnabled else { return }
                dragTranslation = value.translation.width
            }
            .onEnded { value in
                guard isEnabled else { return }
                let finalOffset = min(max(baseOffset + value.translation.width, 0), maxOffset)
                let progress = maxOffset > 0 ? finalOffset / maxOffset : 0
                if currentLockedState {
                    if progress >= 0.55 {
                        visualLocked = false
                        withAnimation(.spring(response: 0.28, dampingFraction: 0.78)) {
                            dragTranslation = 0
                        }
                        onLockChanged(false)
                        return
                    }
                } else if progress <= 0.45 {
                    visualLocked = true
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.78)) {
                        dragTranslation = 0
                    }
                    onLockChanged(true)
                    return
                }
                withAnimation(.spring(response: 0.28, dampingFraction: 0.78)) {
                    dragTranslation = 0
                }
            }

        ZStack {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(backgroundGradient)

            Circle()
                .fill(isEnabled ? .white : Color(red: 0.74, green: 0.75, blue: 0.78))
                .frame(width: thumbSize, height: thumbSize)
                .overlay {
                    Image(systemName: currentLockedState ? "lock.open.fill" : "lock.fill")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(currentLockedState ? Color(red: 0.21, green: 0.14, blue: 0.52) : Color(red: 0.05, green: 0.08, blue: 0.16))
                }
                .offset(x: -maxOffset / 2 + currentOffset)
                .animation(.spring(response: 0.28, dampingFraction: 0.78), value: currentLockedState)

            if isEnabled {
                Text(currentLockedState ? "滑动解锁 ›" : "‹ 滑动锁车")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.white.opacity(0.85))
                    .lineLimit(1)
                    .minimumScaleFactor(0.9)
                    .frame(maxWidth: .infinity, alignment: currentLockedState ? .trailing : .leading)
                    .padding(.leading, thumbSize + horizontalPadding + 8)
                    .padding(.trailing, thumbSize + horizontalPadding + 8)
                    .opacity(hintAlpha)
            }
        }
        .frame(width: trackWidth, height: trackHeight)
        .contentShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .gesture(gesture)
        .onAppear {
            visualLocked = isLocked
        }
        .onChange(of: isLocked) { newValue in
            visualLocked = newValue
        }
    }
}

private struct F1ActionItem: View {
    let imageName: String
    let title: String
    var action: (() -> Void)? = nil

    var body: some View {
        Button(action: { action?() }) {
            VStack(spacing: 9) {
                Image(imageName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 28, height: 28)

                Text(title)
                    .font(.system(size: 11, weight: .regular))
                    .foregroundStyle(AppColor.secondaryText)
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
    }
}

struct F1MapCardView: View {
    let latitude: Double
    let longitude: Double
    let address: String
    var onMapTap: (() -> Void)? = nil

    private var hasValidLocation: Bool {
        latitude != 0 && longitude != 0
    }

    private var staticMapURL: URL? {
        F1MapURLBuilder.staticMapURL(latitude: latitude, longitude: longitude)
    }

    private var resolvedAddress: String {
        F1MapAddressResolver.displayAddress(address: address, latitude: latitude, longitude: longitude)
    }

    var body: some View {
        RoundedRectangle(cornerRadius: 30, style: .continuous)
            .fill(.white)
            .frame(height: 318)
            .overlay {
                VStack(spacing: 8) {
                    F1MapSnapshotView(latitude: latitude, longitude: longitude, cornerRadius: 20)
                    .frame(width: UIScreen.main.bounds.width * 0.85,height: 220)
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .contentShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .onTapGesture {
                        onMapTap?()
                    }

                    HStack(spacing: 6) {
                        Text("位置:")
                            .font(.system(size: 14, weight: .regular))
                            .foregroundStyle(Color(red: 0.54, green: 0.58, blue: 0.66))
                            .frame(width: 30, alignment: .leading)
                        Text(resolvedAddress)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(Color(red: 0.07, green: 0.10, blue: 0.16))
                            .lineLimit(2)
                            .layoutPriority(1)
                        
                        
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 2)
                }
                .padding(12)
            }
    }
}

struct F1SplitMapCardsView: View {
    let onCurrentTap: () -> Void
    let onTrackTap: () -> Void

    var body: some View {
        GeometryReader { proxy in
            let cardWidth = max((proxy.size.width - 12) / 2, 0)
            HStack(spacing: 12) {
                splitCard(title: "当前位置", onTap: onCurrentTap)
                    .frame(width: cardWidth)
                splitCard(title: "历史轨迹", onTap: onTrackTap)
                    .frame(width: cardWidth)
            }
        }
        .frame(height: 176)
    }

    private func splitCard(title: String, onTap: @escaping () -> Void) -> some View {
        RoundedRectangle(cornerRadius: 20, style: .continuous)
            .fill(.white)
            .frame(height: 176)
            .overlay {
                VStack(spacing: 6) {
                    Image("f2_map_current")
                        .resizable()
                        .scaledToFill()
                        .frame(height: 106)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    Text(title)
                        .font(.system(size: 12, weight: .regular))
                        .foregroundStyle(Color(red: 0.54, green: 0.58, blue: 0.66))
                }
                .padding(8)
            }
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .onTapGesture(perform: onTap)
    }
}

private struct F1MapSnapshotView: View {
    let latitude: Double
    let longitude: Double
    let cornerRadius: CGFloat

    private var staticMapURL: URL? {
        F1MapURLBuilder.staticMapURL(latitude: latitude, longitude: longitude)
    }

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(AppColor.mapPlaceholder)

            if let url = staticMapURL {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .empty:
                        Text("加载地图中...")
                            .font(.system(size: 14, weight: .regular))
                            .foregroundStyle(Color(red: 0.43, green: 0.50, blue: 0.59))
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                    case .failure:
                        Text("地图加载失败")
                            .font(.system(size: 14, weight: .regular))
                            .foregroundStyle(Color(red: 0.43, green: 0.50, blue: 0.59))
                    @unknown default:
                        EmptyView()
                    }
                }
            } else {
                Text("暂无位置信息")
                    .font(.system(size: 14, weight: .regular))
                    .foregroundStyle(Color(red: 0.43, green: 0.50, blue: 0.59))
            }
        }
    }
}

struct F1BottomTabView: View {
    let isHomeSelected: Bool
    let onHomeTap: () -> Void
    let onMineTap: () -> Void

    var body: some View {
        HStack {
            Spacer()
            Button(action: onHomeTap) {
                VStack(spacing: 4) {
                    Image(isHomeSelected ? "nav_home_active" : "nav_home")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 28, height: 28)
                    Text("首页")
                        .font(.system(size: 14, weight: isHomeSelected ? .semibold : .regular))
                        .foregroundStyle(isHomeSelected ? AppColor.titleBlack : Color(red: 0.74, green: 0.77, blue: 0.82))
                }
            }
            .buttonStyle(.plain)

            Spacer()

            Button(action: onMineTap) {
                VStack(spacing: 4) {
                    Image(isHomeSelected ? "nav_mine" : "nav_mine_active")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 28, height: 28)
                    Text("我的")
                        .font(.system(size: 14, weight: isHomeSelected ? .regular : .semibold))
                        .foregroundStyle(isHomeSelected ? Color(red: 0.74, green: 0.77, blue: 0.82) : AppColor.titleBlack)
                }
            }
            .buttonStyle(.plain)

            Spacer()
        }
        .padding(.top, 10)
        .padding(.bottom, 6)
        .frame(maxWidth: .infinity)
        .background(.white)
    }
}
