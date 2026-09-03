//
//  GrantDemoApp.swift
//  GrantDemo
//
//  Created by viet.nguyen on 16/1/26.
//

import SwiftUI
import GrantDemoShared

@main
struct GrantDemoApp: App {
    init() {
        DemoLogging.shared.enableGrantLogging()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
