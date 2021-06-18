//
//  MPNowPlayingInfoCenter+Extensions.swift
//  gakki
//
//  Created by Daniel Leong on 6/16/21.
//

import MediaPlayer

extension MPNowPlayingInfoCenter {
    func updateNowPlayingInfo(_ newValues: [String: Any]) {
        self.nowPlayingInfo?.merge(newValues, uniquingKeysWith: { _, new in new })
    }

    func setCurrentTime(_ currentTime: Double) {
        updateNowPlayingInfo([
            MPNowPlayingInfoPropertyElapsedPlaybackTime: currentTime,
        ])
    }
}
