//
//  AppDelegate.swift
//  gakki
//
//  Created by Daniel Leong on 5/18/21.
//

import Cocoa
import MediaPlayer
import SwiftUI
import AppKit

@main
class AppDelegate: NSObject, NSApplicationDelegate {
    private let commandHandler = CommandHandler()

    var popover: NSPopover!
    var statusBarItem: NSStatusItem!

    func applicationDidFinishLaunching(_ aNotification: Notification) {
        if CommandLine.arguments.contains("--show-ui") {
            let contentView = ContentView()

            let popover = NSPopover()
            popover.behavior = .transient
            popover.contentViewController = NSHostingController(rootView: contentView)
            self.popover = popover

            self.statusBarItem = NSStatusBar.system.statusItem(withLength: CGFloat(NSStatusItem.variableLength))

            if let button = self.statusBarItem.button {
                button.image = NSImage(named: "Icon")
                button.action = #selector(togglePopover(_:))
            }
        }

        attachHandlers()
        MPNowPlayingInfoCenter.default().playbackState = .playing
        MPNowPlayingInfoCenter.default().playbackState = .paused

        IPC.send(["type": "ready"])

        DispatchQueue.global(qos: .userInitiated).async { [self] in
            commandHandler.processStdin()
        }
    }

    func applicationWillTerminate(_ aNotification: Notification) {
        // Insert code here to tear down your application
    }

    @objc func togglePopover(_ sender: AnyObject?) {
        if let button = self.statusBarItem.button {
            if self.popover.isShown {
                self.popover.performClose(sender)
            } else {
                self.popover.show(relativeTo: button.bounds, of: button, preferredEdge: NSRectEdge.minY)
            }
        }
    }

    private func attachHandlers() {
        let commandCenter = MPRemoteCommandCenter.shared()
        let playPause = commandCenter.togglePlayPauseCommand
        playPause.isEnabled = true
        playPause.addTarget(handler: { _ in
            IPC.send(["type": "media", "event": "toggle"])
            return .success
        })

        let play = commandCenter.playCommand
        play.isEnabled = true
        play.addTarget(handler: { _ in
            IPC.send(["type": "media", "event": "play"])
            return .success
        })

        let pause = commandCenter.pauseCommand
        pause.isEnabled = true
        pause.addTarget(handler: { _ in
            IPC.send(["type": "media", "event": "pause"])
            return .success
        })

        let previous = commandCenter.previousTrackCommand
        previous.isEnabled = true
        previous.addTarget(handler: { _ in
            IPC.send(["type": "media", "event": "previous-track"])
            return .success
        })

        let nextTrack = commandCenter.nextTrackCommand
        nextTrack.isEnabled = true
        nextTrack.addTarget(handler: { _ in
            IPC.send(["type": "media", "event": "next-track"])
            return .success
        })
    }
}
