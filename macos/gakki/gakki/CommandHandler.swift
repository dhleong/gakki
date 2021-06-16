//
//  CommandHandler.swift
//  gakki
//
//  Created by Daniel Leong on 5/21/21.
//

import MediaPlayer
import SwiftUI
import AppKit

private let commandToPlaybackState: [Command.State: MPNowPlayingPlaybackState] = [
    .playing: .playing,
    .paused: .paused,
]

struct CommandHandler {
    let auth = AuthCommands()

    func processStdin() {
        while true {
            guard let line = readLine() else {
                return
            }

            do {
                if let command = try Command.parse(string: line) {
                    DispatchQueue.main.async { [self] in
                        dispatch(command: command)
                    }
                } else {
                    IPC.log("Unexpected message: \(line)")
                }
            } catch {
                IPC.send(["type": "error", "error": String(describing: error)])
            }
        }
    }

    func dispatch(command: Command) {
        switch command.type {
        case .addAccount:
            guard let account = command.name,
            let value = command.value else {
                return
            }
            if !auth.setAccountAuth(account: account, value: value) {
                IPC.log("Unable to persist account: \(account)")
            }

        case .deleteAccount:
            guard let account = command.name else {
                return
            }
            if !auth.delete(account: account) {
                IPC.log("Unable to delete account: \(account)")
            }

        case .loadAccounts:
            IPC.send([
                "type": "auth-result",
                "auth": auth.getAuth(),
            ])

        case .setNowPlaying:
            setNowPlaying(with: command)

        case .setState:
            setState(with: command)
        }
    }

    private func setNowPlaying(with command: Command) {
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: command.title ?? "",
            MPMediaItemPropertyArtist: command.artist ?? "",
        ]

        if let duration = command.duration, duration > 0 {
            info[MPMediaItemPropertyPlaybackDuration] = duration
        }

        if let rawImageUrl = command.imageUrl,
        let imageUrl = URL(string: rawImageUrl) {

            withFetch(of: imageUrl) { data in
                guard let data = data else {
                    IPC.log("Failed to fetch data for \(imageUrl)")
                    return
                }

                if let image = NSImage(data: data as Data) {
                    let size = image.size as CGSize
                    let artwork = MPMediaItemArtwork(boundsSize: size) { _ in
                        return image
                    }
                    info[MPMediaItemPropertyArtwork] = artwork
                } else {
                    IPC.log("Failed to decode image data for \(imageUrl)")
                }

                MPNowPlayingInfoCenter.default().nowPlayingInfo = info
            }
        } else {
            IPC.log("No image provided for \(String(describing: command))")
            MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        }
    }

    private func setState(with command: Command) {
        if let inputState = command.state,
        let state = commandToPlaybackState[inputState] {
            MPNowPlayingInfoCenter.default().playbackState = state

            if let time = command.currentTime {
                MPNowPlayingInfoCenter.default().setCurrentTime(time)
            }
        }
    }

    private func withFetch(of url: URL, handler: @escaping (Data?) -> Void) {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForResource = 3 // timeout, in seconds

        let session = URLSession(configuration: config)
        session.dataTask(with: url) { data, _, error in
            if error != nil {
                IPC.log("Failed to fetch \(url): \(String(describing: error))")
                handler(nil)
            } else {
                handler(data)
            }
        }.resume()
    }
}
