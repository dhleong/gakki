//
//  convertFromKebabCase.swift
//  gakki
//
//  Created by Daniel Leong on 5/21/21.
//

import Foundation

struct AnyKey: CodingKey {
    var stringValue: String
    var intValue: Int?

    init?(stringValue: String) {
        self.stringValue = stringValue
        self.intValue = nil
    }

    init?(intValue: Int) {
        self.stringValue = String(intValue)
        self.intValue = intValue
    }
}

func convertFromKebabCase() -> JSONDecoder.KeyDecodingStrategy {
    return .custom { keys in
        let parts = keys.last!.stringValue.split(separator: "-")
        var key = parts[0]
        for part in parts[1...] {
            key.append(contentsOf: part.capitalized)
        }

        let kind = AnyKey(stringValue: String(key))
        if let kind = kind {
            return kind
        }

        print("Unable to parse \(keys) into ")
        return kind!
    }
}
