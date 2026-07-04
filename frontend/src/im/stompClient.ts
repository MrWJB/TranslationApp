import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { ImEvent, SendMessagePayload } from '@/types/im'

export type StompMessageHandler = (event: ImEvent) => void

let client: Client | null = null
let reconnectAttempts = 0
const MAX_RECONNECT_DELAY = 30000

const subscriptions: StompSubscription[] = []

function getWsUrl(): string {
  const token = localStorage.getItem('token')
  const base = `${window.location.origin}/ws/im`
  return token ? `${base}?token=${encodeURIComponent(token)}` : base
}

function clearSubscriptions() {
  subscriptions.forEach((sub) => {
    try {
      sub.unsubscribe()
    } catch {
      /* ignore */
    }
  })
  subscriptions.length = 0
}

function parseEvent(message: IMessage): ImEvent | null {
  try {
    return JSON.parse(message.body) as ImEvent
  } catch {
    return null
  }
}

export function connectStomp(onEvent: StompMessageHandler, onConnect?: () => void, onDisconnect?: () => void): Client {
  if (client?.connected) {
    return client
  }
  if (client?.active) {
    client.deactivate()
    client = null
  }

  client = new Client({
    webSocketFactory: () => new SockJS(getWsUrl()) as WebSocket,
    reconnectDelay: 0,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      reconnectAttempts = 0
      clearSubscriptions()

      const queues = ['/user/queue/messages', '/user/queue/presence', '/user/queue/calls']
      for (const dest of queues) {
        const sub = client!.subscribe(dest, (msg) => {
          const event = parseEvent(msg)
          if (event) onEvent(event)
        })
        subscriptions.push(sub)
      }

      onConnect?.()
    },
    onDisconnect: () => {
      onDisconnect?.()
      scheduleReconnect(onEvent, onConnect, onDisconnect)
    },
    onStompError: () => {
      onDisconnect?.()
      scheduleReconnect(onEvent, onConnect, onDisconnect)
    },
    onWebSocketClose: () => {
      onDisconnect?.()
      scheduleReconnect(onEvent, onConnect, onDisconnect)
    },
  })

  client.activate()
  return client
}

function scheduleReconnect(
  onEvent: StompMessageHandler,
  onConnect?: () => void,
  onDisconnect?: () => void
) {
  if (!client) return
  reconnectAttempts += 1
  const delay = Math.min(1000 * 2 ** reconnectAttempts, MAX_RECONNECT_DELAY)
  setTimeout(() => {
    if (client && !client.active) {
      client = null
      connectStomp(onEvent, onConnect, onDisconnect)
    }
  }, delay)
}

export function disconnectStomp() {
  reconnectAttempts = 999
  clearSubscriptions()
  if (client) {
    client.deactivate()
    client = null
  }
}

export function isStompConnected(): boolean {
  return !!client?.connected
}

export function subscribeConversation(conversationId: number, onEvent: StompMessageHandler): StompSubscription | null {
  if (!client?.connected) return null
  const sub = client.subscribe(`/topic/conversation.${conversationId}`, (msg) => {
    const event = parseEvent(msg)
    if (event) onEvent(event)
  })
  subscriptions.push(sub)
  return sub
}

export function sendStompMessage(destination: string, body: unknown) {
  if (!client?.connected) return
  client.publish({ destination, body: JSON.stringify(body) })
}

export function sendChatMessage(payload: SendMessagePayload) {
  sendStompMessage('/app/im/send', payload)
}

export function sendReadReceipt(conversationId: number, messageId: number) {
  sendStompMessage('/app/im/read', { conversationId, messageId })
}

export function sendTyping(conversationId: number, typing: boolean) {
  sendStompMessage('/app/im/typing', { conversationId, typing })
}

export function sendPresenceHeartbeat() {
  sendStompMessage('/app/im/presence/heartbeat', {})
}

export function sendCallOffer(callId: number, sdp: RTCSessionDescriptionInit) {
  sendStompMessage('/app/im/call/offer', { callId, sdp })
}

export function sendCallAnswer(callId: number, sdp: RTCSessionDescriptionInit) {
  sendStompMessage('/app/im/call/answer', { callId, sdp })
}

export function sendCallIce(callId: number, candidate: RTCIceCandidateInit) {
  sendStompMessage('/app/im/call/ice', { callId, candidate })
}
