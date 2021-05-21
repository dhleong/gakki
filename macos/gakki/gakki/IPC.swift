//
//  IPC.swift
//  gakki
//
//  Created by Daniel Leong on 5/21/21.
//

import Foundation

struct IPC {
    static func log(_ message: String) {
        send(["type": "log", "message": message])
    }

    static func send(_ message: [String: Any?]) {
        if let data = try? JSONSerialization.data(withJSONObject: message),
            let text = String(data: data, encoding: .utf8) {
            print(text)
            fflush(stdout)
        }
    }
}
