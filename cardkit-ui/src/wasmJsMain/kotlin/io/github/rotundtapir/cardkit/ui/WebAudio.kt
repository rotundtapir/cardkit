// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui

import kotlin.js.Promise
import org.khronos.webgl.ArrayBuffer

// Minimal Web Audio API surface for the sound engine — kotlinx-browser does not bind Web Audio.

internal external class AudioContext : JsAny {
    val destination: AudioDestinationNode
    val state: String
    fun decodeAudioData(audioData: ArrayBuffer): Promise<AudioBuffer>
    fun createBufferSource(): AudioBufferSourceNode
    fun createGain(): GainNode
    fun resume(): Promise<JsAny?>
}

internal external class AudioBuffer : JsAny

internal open external class AudioNode : JsAny {
    fun connect(destination: AudioNode): AudioNode
}

internal external class AudioDestinationNode : AudioNode

internal external class AudioBufferSourceNode : AudioNode {
    var buffer: AudioBuffer?
    fun start()
}

internal external class GainNode : AudioNode {
    val gain: AudioParam
}

internal external class AudioParam : JsAny {
    var value: Double
}
