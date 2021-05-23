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
        let query: [String: Any] = [
            Keys.secClass: kSecClassGenericPassword,
            Keys.service: serviceName,
            Keys.returnData: false,
            Keys.returnAttributes: true,
            Keys.matchLimit: kSecMatchLimitAll,
        ]

        var result: AnyObject?

        let lastResultCode = withUnsafeMutablePointer(to: &result) {
            SecItemCopyMatching(query as CFDictionary, UnsafeMutablePointer($0))
        }

        if lastResultCode == noErr {
            return (result as? [[String: Any]])?.compactMap {
                $0[Keys.account] as? String
            } ?? []
        }

        return []
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

        let query: [String: Any] = [
            Keys.secClass: kSecClassGenericPassword,
            Keys.service: serviceName,
            Keys.account: prefixed(key),
            Keys.matchLimit: kSecMatchLimitOne,
            Keys.returnData: true,
        ]

        // query = addAccessGroupWhenPresent(query)
        // query = addSynchronizableIfRequired(query, addingItems: false)
        // lastQueryParameters = query

        var result: AnyObject?

        let lastResultCode = withUnsafeMutablePointer(to: &result) {
            SecItemCopyMatching(query as CFDictionary, UnsafeMutablePointer($0))
        }

        if lastResultCode == noErr {
            return result as? Data
        }

        return nil
    }

    private func setData(forKey key: String, data: Data) -> Bool {
        lock.lock()
        defer { lock.unlock() }

        deleteNoLock(forKey: key) // Delete any existing key before saving it

        let query: [String: Any] = [
            Keys.secClass: kSecClassGenericPassword,
            Keys.service: serviceName,
            Keys.account: prefixed(key),
            Keys.valueData: data,
            Keys.accessible: kSecAttrAccessibleWhenUnlocked,
        ]

        // query = addAccessGroupWhenPresent(query)
        // query = addSynchronizableIfRequired(query, addingItems: true)
        // lastQueryParameters = query

        let lastResultCode = SecItemAdd(query as CFDictionary, nil)

        return lastResultCode == noErr
    }

    @discardableResult
    private func deleteNoLock(forKey key: String) -> Bool {
        let query: [String: Any] = [
            Keys.secClass: kSecClassGenericPassword,
            Keys.service: serviceName,
            Keys.account: prefixed(key),
        ]

        // query = addAccessGroupWhenPresent(query)
        // query = addSynchronizableIfRequired(query, addingItems: false)
        // lastQueryParameters = query

        let lastResultCode = SecItemDelete(query as CFDictionary)

        return lastResultCode == noErr
    }

    func prefixed(_ key: String) -> String {
        // return "io.github.dhleong.gakki.\(key)"
        return key
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
