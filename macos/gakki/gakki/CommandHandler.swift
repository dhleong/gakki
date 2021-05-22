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
                IPC.send(["type": "error", "error": error])
            }
        }
    }

    func dispatch(command: Command) {
        switch command.type {
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

        if let rawImageUrl = command.imageUrl,
        let imageUrl = URL(string: rawImageUrl) {
            withFetch(of: imageUrl) { data in
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
    }

    private func setState(with command: Command) {
        if let inputState = command.state,
        let state = commandToPlaybackState[inputState] {
            MPNowPlayingInfoCenter.default().playbackState = state
        }
    }

    private func withFetch(of url: URL, handler: @escaping (Data?) -> Void) {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForResource = 3 // timeout, in seconds

        let session = URLSession(configuration: config)
        session.dataTask(with: url) { data, _, error in
            if error != nil {
                handler(nil)
            } else {
                handler(data)
            }
        }.resume()
    }
}
