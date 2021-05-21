//
//  Command.swift
//  gakki
//
//  Created by Daniel Leong on 5/21/21.
//

import Foundation

struct Command: Codable {
    enum Kind: String, Codable, CodingKey {
        case setState = "set-state"
        case setNowPlaying = "set-now-playing"
    }

    enum State: String, Codable, CodingKey {
        case playing
        case paused
    }

    var type: Kind

    // .setState:
    var state: State?

    // .setNowPlaying:
    var title: String?
    var artist: String?
    var imageUrl: String?
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
