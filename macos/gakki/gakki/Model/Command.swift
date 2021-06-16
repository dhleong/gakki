//
//  Command.swift
//  gakki
//
//  Created by Daniel Leong on 5/21/21.
//

import Foundation

struct Command: Codable {
    enum Kind: String, Codable, CodingKey {
        case addAccount = "add-account"
        case deleteAccount = "delete-account"
        case loadAccounts = "load-accounts"

        case setState = "set-state"
        case setNowPlaying = "set-now-playing"
    }

    enum State: String, Codable, CodingKey {
        case playing
        case paused
    }

    var type: Kind

    // .addAccount/.deleteAccount:
    var name: String?
    var value: String?

    // .setState:
    var state: State?
    var currentTime: Double?

    // .setNowPlaying:
    var title: String?
    var artist: String?
    var imageUrl: String?
    var duration: Int?
}

extension Command {
    static func parse(string: String) throws -> Command? {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = convertFromKebabCase()
        if let data = string.data(using: .utf8) {
            return try decoder.decode(Command.self, from: data)
        }
        return nil
    }
}
