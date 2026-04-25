import SwiftUI

enum AppShadow {
    static let card = Shadow(color: Color.black.opacity(0.06), radius: 8, x: 0, y: 2)
}

struct Shadow {
    let color: Color
    let radius: CGFloat
    let x: CGFloat
    let y: CGFloat
}
