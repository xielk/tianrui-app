import Foundation

struct F1MockData {
    let deviceName: String
    let totalMileage: String
    let voltage: String
    let batteryPercent: String

    static let sample = F1MockData(
        deviceName: "GC0018002▼",
        totalMileage: "49KM",
        voltage: "0.45",
        batteryPercent: "0%"
    )
}
