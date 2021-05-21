//
//  gakkiTests.swift
//  gakkiTests
//
//  Created by Daniel Leong on 5/18/21.
//

import XCTest
@testable import gakki

class gakkiTests: XCTestCase {

    override func setUpWithError() throws {
        // Put setup code here. This method is called before the invocation of each test method in the class.
    }

    override func tearDownWithError() throws {
        // Put teardown code here. This method is called after the invocation of each test method in the class.
    }

    func testCommandParsing() throws {
        let command = try Command.parse(
            string: """
            {
                "type": "set-state",
                "state": "playing"
            }
            """)

        XCTAssert(command != nil)
        XCTAssert(command!.type == .setState)
    }

    func testPerformanceExample() throws {
        // This is an example of a performance test case.
        self.measure {
            // Put the code you want to measure the time of here.
        }
    }

}
