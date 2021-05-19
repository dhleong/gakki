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
    private let stringToPlaybackState: [String: MPNowPlayingPlaybackState] = [
        "playing": .playing,
        "paused": .paused,
    ]

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

        send(["type": "ready"])

        DispatchQueue.global(qos: .userInitiated).async { [self] in
            processStdin()
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

    private func processStdin() {
        while true {
            guard let line = readLine(), let data = line.data(using: .utf8) else {
                return
            }

            do {
                if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any?] {
                    DispatchQueue.main.async { [self] in
                        dispatchMessage(json)
                    }
                }
            } catch {
                send(["type": "error", "error": error])
            }
        }
    }

    private func dispatchMessage(_ message: [String: Any?]) {
        switch message["type"] as? String {
        case "set-now-playing":
            var info: [String: Any] = [
                MPMediaItemPropertyTitle: message["title"] as? String ?? "",
                MPMediaItemPropertyArtist: message["artist"] as? String ?? "",
            ]

            if let rawImageUrl = message["image-url"] as? String,
            let imageUrl = URL(string: rawImageUrl) {
                withFetch(of: imageUrl) { [self] data in
                    if let data = data,
                    let image = NSImage(data: data as Data) {
                        let size = image.size as CGSize
                        let artwork = MPMediaItemArtwork(boundsSize: size) { _ in
                            return image
                        }
                        info[MPMediaItemPropertyArtwork] = artwork
                    }

                    MPNowPlayingInfoCenter.default().nowPlayingInfo = info
                }
            } else {
                MPNowPlayingInfoCenter.default().nowPlayingInfo = info
            }
            break;

        case "set-state":
            if let rawState = message["state"] as? String,
            let state = stringToPlaybackState[rawState] {
                MPNowPlayingInfoCenter.default().playbackState = state
            }
            break;

        default:
            log("Unexpected message: \(message["type"])")
            break;
        }
    }

    private func attachHandlers() {
        let commandCenter = MPRemoteCommandCenter.shared()
        let playPause = commandCenter.togglePlayPauseCommand
        playPause.isEnabled = true
        playPause.addTarget(handler: { [self] event in
            send(["type": "media", "event": "toggle"])
            return .success
        })

        let play = commandCenter.playCommand
        play.isEnabled = true
        play.addTarget(handler: { [self] event in
            send(["type": "media", "event": "play"])
            return .success
        })

        let pause = commandCenter.pauseCommand
        pause.isEnabled = true
        pause.addTarget(handler: { [self] event in
            send(["type": "media", "event": "pause"])
            return .success
        })

        let previous = commandCenter.previousTrackCommand
        previous.isEnabled = true
        previous.addTarget(handler: { [self] event in
            send(["type": "media", "event": "previous-track"])
            return .success
        })

        let nextTrack = commandCenter.nextTrackCommand
        nextTrack.isEnabled = true
        nextTrack.addTarget(handler: { [self] event in
            send(["type": "media", "event": "next-track"])
            return .success
        })
    }

    private func log(_ message: String) {
        send(["type": "log", "message": message])
    }

    private func withFetch(of url: URL, handler: @escaping (Data?) -> Void) {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForResource = 3 // timeout, in seconds

        let session = URLSession(configuration: config)
        session.dataTask(with: url) { data, response, error in
            if let error = error {
                handler(nil)
            } else {
                handler(data)
            }
        }.resume()
    }

    private func send(_ message: [String: Any?]) {
        if let data = try? JSONSerialization.data(withJSONObject: message),
            let text = String(data: data, encoding: .utf8) {
            print(text)
            fflush(stdout)
        }
    }
}

