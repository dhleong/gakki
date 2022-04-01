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
    var audioDeviceManager: AudioDeviceManager!

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

        self.audioDeviceManager = AudioDeviceManager()
        self.audioDeviceManager.listen()
        attachMediaCommandHandlers()
        MPNowPlayingInfoCenter.default().playbackState = .playing
        MPNowPlayingInfoCenter.default().playbackState = .paused

        IPC.send(["type": "ready"])

        DispatchQueue.global(qos: .userInitiated).async { [self] in
            commandHandler.processStdin()
        }
    }

    func applicationWillTerminate(_ aNotification: Notification) {
        self.audioDeviceManager.unlisten()
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

    private func attachMediaCommandHandlers() {
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

        let seek = commandCenter.changePlaybackPositionCommand
        seek.isEnabled = true
        seek.addTarget { event in
            if let ev = event as? MPChangePlaybackPositionCommandEvent {
                MPNowPlayingInfoCenter.default().setCurrentTime(ev.positionTime)
                IPC.send([
                    "type": "media",
                    "event": "seek",
                    "time": ev.positionTime,
                ])
            }

            return .success
        }

        // NOTE: This code is kept around for reference, but is disabled for
        // now. If we enable these commands, we seem to lose the next/prev
        // track buttons in the UI, and there are also some issues around
        // updating the current timestamp when they fire... so it's easiest
        // to just not use them, and rely on the absolute seek command.
        // commandCenter.skipForwardCommand.handleSeekEvent(seekDirection: 1.0)
        // commandCenter.skipBackwardCommand.handleSeekEvent(seekDirection: -1.0)
    }
}

extension MPSkipIntervalCommand {
    func handleSeekEvent(seekDirection: Double) {
        isEnabled = true
        addTarget(handler: { event in
            if let ev = event as? MPSkipIntervalCommandEvent {
                IPC.send([
                    "type": "media",
                    "event": "seek",
                    "relative": seekDirection * ev.interval,
                ])
            }
            return .success
        })
    }
}
