// swift-tools-version:5.5
import PackageDescription

let package = Package(
    name: "Grant",
    platforms: [
        .iOS(.v13)
    ],
    products: [
        .library(
            name: "Grant",
            targets: ["Grant"]
        ),
    ],
    targets: [
        .binaryTarget(
            name: "Grant",
            url: "https://github.com/brewkits/Grant/releases/download/1.4.0/Grant.xcframework.zip",
            checksum: "PLACEHOLDER_CHECKSUM_WILL_BE_REPLACED_BY_CI"
        )
    ]
)