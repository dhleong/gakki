//
//  AudioDevice.swift
//  gakki
//
//  Created by Daniel Leong on 3/10/22.
//

import CoreAudio
import Foundation

class AudioDevice {
    private var id: AudioDeviceID
    private var listening = false

    var nominalSampleRate: Float64? {
        guard let address = validAddress(selector: kAudioDevicePropertyNominalSampleRate) else {
            return nil
        }
        return getProperty(address: address, defaultValue: 0.0)
    }

    init(withID id: AudioDeviceID) {
        self.id = id
    }

    deinit {
        unlisten()
    }

    func listen() {
        if listening {
            unlisten()
        }

        let clientData = Unmanaged.passUnretained(self).toOpaque()
        var address = AudioObjectPropertyAddress(
            mSelector: kAudioObjectPropertySelectorWildcard,
            mScope: kAudioObjectPropertyScopeWildcard,
            mElement: kAudioObjectPropertyElementWildcard
        )

        if noErr == AudioObjectAddPropertyListener(id, &address, onAudioPropertyChanged, clientData) {
            listening = true
        }
    }

    func unlisten() {
        if !listening {
            return
        }

        let clientData = Unmanaged.passUnretained(self).toOpaque()
        var address = AudioObjectPropertyAddress(
            mSelector: kAudioObjectPropertySelectorWildcard,
            mScope: kAudioObjectPropertyScopeWildcard,
            mElement: kAudioObjectPropertyElementWildcard
        )

        if noErr == AudioObjectRemovePropertyListener(id, &address, onAudioPropertyChanged, clientData) {
            listening = false
        }
    }

    private func validAddress(selector: AudioObjectPropertySelector) -> AudioObjectPropertyAddress? {
        var address = AudioDevice.address(selector: selector)
        guard AudioObjectHasProperty(id, &address) else { return nil }
        return address
    }

    private func getProperty<T>(address: AudioObjectPropertyAddress, defaultValue: T) -> T? {
        var value = defaultValue
        return AudioDevice.getPropertyDataOrNil(id, address: address, andValue: &value)
    }
}

extension AudioDevice: CustomStringConvertible {
    public var description: String {
        return "AudioDevice(id: \(id))"
    }
}

extension AudioDevice {
    class func address(selector: AudioObjectPropertySelector) -> AudioObjectPropertyAddress {
        return AudioObjectPropertyAddress(mSelector: selector, mScope: kAudioObjectPropertyScopeGlobal, mElement: kAudioObjectPropertyElementMain)
    }

    class func getPropertyDataOrNil<T>(_ objectID: AudioObjectID, address: AudioObjectPropertyAddress, andValue: inout T) -> T? {
        if noErr == getPropertyData(objectID, address: address, andValue: &andValue) {
            print("GOT prop data")
            return andValue
        } else {
            print("Err getting property data")
            return nil
        }
    }

    private class func getPropertyData<T>(_ objectID: AudioObjectID, address: AudioObjectPropertyAddress, andValue: inout T) -> OSStatus {
        var refableAddress = address
        var size = UInt32(MemoryLayout<T>.size)
        let status = AudioObjectGetPropertyData(AudioObjectID(kAudioObjectSystemObject), &refableAddress, UInt32(0), nil, &size, &andValue)

        if status != noErr {
            NSLog("Err getting property data: \(String(describing: status))")
        }

        return status
    }
}

fileprivate func onAudioPropertyChanged(
    objectID: UInt32,
    numInAddresses: UInt32,
    inAddresses: UnsafePointer<AudioObjectPropertyAddress>,
    clientData: Optional<UnsafeMutableRawPointer>
) -> Int32 {
    let address = inAddresses.pointee
    let _self = Unmanaged<AudioDevice>.fromOpaque(clientData!).takeUnretainedValue()

    switch address.mSelector {
        case kAudioDevicePropertyNominalSampleRate:
            NSLog("\(_self): Nominal Sample Rate changed -> \(String(describing: _self.nominalSampleRate))!")

        case kAudioDevicePropertyAvailableNominalSampleRates:
            // TODO Fetching *available* rates is... tricky.
            NSLog("\(_self): Available Sample Rates changed -> \(String(describing: _self.nominalSampleRate))!")


        default:
            break // nop
    }

    return kAudioHardwareNoError
}
