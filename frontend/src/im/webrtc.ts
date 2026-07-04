import { sendCallAnswer, sendCallIce, sendCallOffer } from './stompClient'

export interface WebRtcConfig {
  stunUrls?: string[]
  turnUrls?: string[]
  turnUsername?: string
  turnCredential?: string
}

export interface WebRtcCallbacks {
  onLocalStream?: (stream: MediaStream) => void
  onRemoteStream?: (stream: MediaStream) => void
  onConnectionStateChange?: (state: RTCPeerConnectionState) => void
  onError?: (error: Error) => void
}

const DEFAULT_STUN = ['stun:stun.l.google.com:19302']

export class WebRtcCall {
  private pc: RTCPeerConnection | null = null
  private localStream: MediaStream | null = null
  private callId: number
  private isInitiator: boolean
  private config: WebRtcConfig
  private callbacks: WebRtcCallbacks

  constructor(callId: number, isInitiator: boolean, config: WebRtcConfig = {}, callbacks: WebRtcCallbacks = {}) {
    this.callId = callId
    this.isInitiator = isInitiator
    this.config = config
    this.callbacks = callbacks
  }

  private buildIceServers(): RTCIceServer[] {
    const servers: RTCIceServer[] = (this.config.stunUrls || DEFAULT_STUN).map((url) => ({ urls: url }))
    if (this.config.turnUrls?.length) {
      servers.push({
        urls: this.config.turnUrls,
        username: this.config.turnUsername,
        credential: this.config.turnCredential,
      })
    }
    return servers
  }

  private createPeerConnection() {
    this.pc = new RTCPeerConnection({ iceServers: this.buildIceServers() })

    this.pc.onicecandidate = (event) => {
      if (event.candidate) {
        sendCallIce(this.callId, event.candidate.toJSON())
      }
    }

    this.pc.ontrack = (event) => {
      const [stream] = event.streams
      if (stream) this.callbacks.onRemoteStream?.(stream)
    }

    this.pc.onconnectionstatechange = () => {
      if (this.pc) this.callbacks.onConnectionStateChange?.(this.pc.connectionState)
    }
  }

  async start(video: boolean): Promise<void> {
    try {
      this.createPeerConnection()
      this.localStream = await navigator.mediaDevices.getUserMedia({ audio: true, video })
      this.localStream.getTracks().forEach((track) => this.pc!.addTrack(track, this.localStream!))
      this.callbacks.onLocalStream?.(this.localStream)

      if (this.isInitiator) {
        const offer = await this.pc!.createOffer()
        await this.pc!.setLocalDescription(offer)
        sendCallOffer(this.callId, offer)
      }
    } catch (err) {
      this.callbacks.onError?.(err instanceof Error ? err : new Error(String(err)))
      throw err
    }
  }

  async handleRemoteOffer(sdp: RTCSessionDescriptionInit) {
    if (!this.pc) this.createPeerConnection()
    if (!this.localStream) {
      const video = sdp.sdp?.includes('m=video') ?? false
      this.localStream = await navigator.mediaDevices.getUserMedia({ audio: true, video })
      this.localStream.getTracks().forEach((track) => this.pc!.addTrack(track, this.localStream!))
      this.callbacks.onLocalStream?.(this.localStream)
    }
    await this.pc!.setRemoteDescription(new RTCSessionDescription(sdp))
    const answer = await this.pc!.createAnswer()
    await this.pc!.setLocalDescription(answer)
    sendCallAnswer(this.callId, answer)
  }

  async handleRemoteAnswer(sdp: RTCSessionDescriptionInit) {
    if (!this.pc) return
    await this.pc.setRemoteDescription(new RTCSessionDescription(sdp))
  }

  async handleRemoteIce(candidate: RTCIceCandidateInit) {
    if (!this.pc) return
    await this.pc.addIceCandidate(new RTCIceCandidate(candidate))
  }

  toggleMute(muted: boolean) {
    this.localStream?.getAudioTracks().forEach((t) => { t.enabled = !muted })
  }

  toggleVideo(enabled: boolean) {
    this.localStream?.getVideoTracks().forEach((t) => { t.enabled = enabled })
  }

  hangup() {
    this.localStream?.getTracks().forEach((t) => t.stop())
    this.localStream = null
    this.pc?.close()
    this.pc = null
  }
}
