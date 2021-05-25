//
//  Auth.swift
//  gakki
//
//  Created by Daniel Leong on 5/23/21.
//

import Security
import Foundation

struct AuthCommands {
    private let serviceName = "net.dhleong.gakki"
    private let lock = NSLock()

    var allKeys: [String] {
        let q = query(with: [
            Keys.returnData: false,
            Keys.returnAttributes: true,
            Keys.matchLimit: kSecMatchLimitAll,
        ])

        var result: AnyObject?

        let resultCode = withUnsafeMutablePointer(to: &result) {
            SecItemCopyMatching(q as CFDictionary, UnsafeMutablePointer($0))
        }

        if resultCode != noErr {
            return []
        }

        guard let listOfDictionaries = result as? [[String: Any]] else {
            return []
        }

        return listOfDictionaries.compactMap {
            $0[Keys.account] as? String
        }
    }

    func delete(account: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }

        // Delete any existing key before saving it
        return deleteNoLock(forKey: account)
    }

    func getAuth() -> [String: String] {
        var result: [String: String] = [:]
        for key in allKeys {
            result[key] = getAccountAuth(account: key)
        }
        return result
    }

    func getAccountAuth(account: String) -> String? {
        if let data = readData(forKey: account),
        let string = String(data: data, encoding: .utf8) {
            return string
        }
        return nil
    }

    func setAccountAuth(account: String, value: String) -> Bool {
        return setData(forKey: account, data: value.data(using: .utf8)!)
    }

    private func readData(forKey key: String) -> Data? {
        lock.lock()
        defer { lock.unlock() }

        let q = query(forKey: key, [
            Keys.matchLimit: kSecMatchLimitOne,
            Keys.returnData: true,
        ])

        var result: AnyObject?

        let resultCode = withUnsafeMutablePointer(to: &result) {
            SecItemCopyMatching(q as CFDictionary, UnsafeMutablePointer($0))
        }

        if resultCode == noErr {
            return result as? Data
        }

        return nil
    }

    private func setData(forKey key: String, data: Data) -> Bool {
        lock.lock()
        defer { lock.unlock() }

        // Delete any existing key before saving it
        deleteNoLock(forKey: key)

        let q = query(forKey: key, [
            Keys.valueData: data,
            Keys.accessible: kSecAttrAccessibleWhenUnlocked,
        ])

        let resultCode = SecItemAdd(q as CFDictionary, nil)
        return resultCode == noErr
    }

    @discardableResult
    private func deleteNoLock(forKey key: String) -> Bool {
        let q = query(forKey: key)
        let resultCode = SecItemDelete(q as CFDictionary)
        return resultCode == noErr
    }

    private func query(forKey key: String, _ extras: [String: Any] = [:]) -> [String: Any] {
        var q = query(with: extras)
        q[Keys.account] = key
        return q
    }

    private func query(with extras: [String: Any] = [:]) -> [String: Any] {
        var query = baseQuery()
        query.merge(extras, uniquingKeysWith: { _, new in new })
        return query
    }

    private func baseQuery() -> [String: Any] {
        return [
            Keys.secClass: kSecClassGenericPassword,
            Keys.service: serviceName,
        ]
    }

    struct Keys {
        static let secClass = kSecClass as String
        static let account = kSecAttrAccount as String
        static let accessible = kSecAttrAccessible as String
        static let matchLimit = kSecMatchLimit as String
        static let returnData = kSecReturnData as String
        static let returnAttributes = kSecReturnAttributes as String
        static let service = kSecAttrService as String
        static let valueData = kSecValueData as String
    }
}
