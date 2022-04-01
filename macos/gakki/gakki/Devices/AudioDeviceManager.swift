//
//  AudioDeviceManager.swift
//  gakki
//
//  Created by Daniel Leong on 3/10/22.
//

import CoreAudio
import Foundation

class AudioDeviceManager {
    private var listening = false

    var defaultOutputDevice: AudioDevice? = nil

    func listen() {
        if (listening) {
            unlisten()
        }

        let systemObjectID = AudioObjectID(kAudioObjectSystemObject)
        let clientData = Unmanaged.passUnretained(self).toOpaque()
        var address = AudioObjectPropertyAddress(
            mSelector: kAudioObjectPropertySelectorWildcard,
            mScope: kAudioObjectPropertyScopeWildcard,
            mElement: kAudioObjectPropertyElementWildcard
        )
        if noErr == AudioObjectAddPropertyListener(systemObjectID, &address, onAudioPropertyChanged, clientData) {
            listening = true
        }

        if defaultOutputDevice == nil {
            // Grab and listen to the current default, if any
            onDefaultDeviceChanged()
        }
    }

    func unlisten() {
        if !listening {
            return
        }

        let systemObjectID = AudioObjectID(kAudioObjectSystemObject)
        let clientData = Unmanaged.passUnretained(self).toOpaque()
        var address = AudioObjectPropertyAddress(
            mSelector: kAudioObjectPropertySelectorWildcard,
            mScope: kAudioObjectPropertyScopeWildcard,
            mElement: kAudioObjectPropertyElementWildcard
        )

        if noErr == AudioObjectRemovePropertyListener(systemObjectID, &address, onAudioPropertyChanged, clientData) {
            listening = false
        }
    }

    fileprivate func onDefaultDeviceChanged() {
        let newDefault = getDefaultOutputDevice()
        let isFirst = self.defaultOutputDevice == nil
        self.defaultOutputDevice = newDefault

        if !isFirst {
            IPC.send(["type": "default-device-changed"])
        }

        if let device = newDefault {
            device.listen()
        }
    }

    private func getDefaultOutputDevice() -> AudioDevice? {
        let address = AudioDevice.address(selector: kAudioHardwarePropertyDefaultOutputDevice)
        var deviceID = AudioDeviceID()

        guard let id = AudioDevice.getPropertyDataOrNil(AudioObjectID(kAudioObjectSystemObject), address: address, andValue: &deviceID) else {
            return nil
        }

        return AudioDevice(withID: id)
    }
}

fileprivate func onAudioPropertyChanged(
    objectID: UInt32,
    numInAddresses: UInt32,
    inAddresses: UnsafePointer<AudioObjectPropertyAddress>,
    clientData: Optional<UnsafeMutableRawPointer>
) -> Int32 {
    let address = inAddresses.pointee
    let _self = Unmanaged<AudioDeviceManager>.fromOpaque(clientData!).takeUnretainedValue()

    switch address.mSelector {
        case kAudioHardwarePropertyDefaultOutputDevice:
            _self.onDefaultDeviceChanged()

        case kAudioHardwarePropertyDefaultSystemOutputDevice:
            break // nop?
        default:
            break // nop
    }

    return kAudioHardwareNoError
}
