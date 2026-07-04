import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  acceptFriendRequest,
  answerCall,
  createDirectConversation,
  createGroupConversation,
  endCall,
  getConversations,
  getDepartmentTree,
  getFriendRequests,
  getFriends,
  getMessages,
  initiateCall,
  markConversationRead,
  presignAttachment,
  completeAttachment,
  rejectCall,
  rejectFriendRequest,
  searchMessages,
  sendFriendRequest,
  sendMessageRest,
} from '@/api/im'
import {
  connectStomp,
  disconnectStomp,
  sendPresenceHeartbeat,
  sendReadReceipt,
  sendTyping,
} from '@/im/stompClient'
import { WebRtcCall } from '@/im/webrtc'
import type {
  CallRingingPayload,
  CallSession,
  Conversation,
  Department,
  Friend,
  FriendRequest,
  ImEvent,
  Message,
  MessageNewPayload,
  MessageReadPayload,
  PresencePayload,
  PresenceStatus,
  SendMessagePayload,
  TypingPayload,
} from '@/types/im'
import { useUserStore } from './user'
import { parseDate } from '@/utils/date'

export const useImStore = defineStore('im', () => {
  const conversations = ref<Conversation[]>([])
  const messagesMap = ref<Record<number, Message[]>>({})
  const friends = ref<Friend[]>([])
  const friendRequests = ref<FriendRequest[]>([])
  const departments = ref<Department[]>([])
  const presenceMap = ref<Record<number, PresenceStatus>>({})
  const activeConversationId = ref<number | null>(null)
  const wsConnected = ref(false)
  const typingUsers = ref<Record<number, Set<number>>>({})
  const searchResults = ref<Message[]>([])

  const activeCall = ref<CallSession | null>(null)
  const incomingCall = ref<(CallSession & { fromUserName?: string }) | null>(null)
  let webrtcCall: WebRtcCall | null = null
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null

  const unreadTotal = computed(() =>
    conversations.value.reduce((sum, c) => sum + (c.unreadCount || 0), 0)
  )

  const activeConversation = computed(() =>
    conversations.value.find((c) => c.id === activeConversationId.value) ?? null
  )

  const activeMessages = computed(() => {
    const id = activeConversationId.value
    return id ? messagesMap.value[id] ?? [] : []
  })

  function sortConversations() {
    conversations.value.sort((a, b) => {
      if (a.pinned && !b.pinned) return -1
      if (!a.pinned && b.pinned) return 1
      const ta = parseDate(a.lastMessageAt)?.getTime() ?? 0
      const tb = parseDate(b.lastMessageAt)?.getTime() ?? 0
      return tb - ta
    })
  }

  function patchDirectConversationPeer(conv: Conversation): Conversation {
    if (conv.type !== 'DIRECT') return conv
    if (conv.peerUser?.username || conv.peerUser?.realName || conv.title) return conv
    const userStore = useUserStore()
    const peerId =
      conv.peerUserId ??
      conv.members?.find((m) => m.userId !== userStore.userId)?.userId
    if (!peerId) return conv
    const friend = friends.value.find((f) => f.userId === peerId)
    if (!friend) return conv
    return {
      ...conv,
      peerUserId: peerId,
      peerUser: {
        id: peerId,
        username: friend.username,
        realName: friend.realName,
        avatar: friend.avatar,
      },
      title: friend.realName || friend.username,
      avatarUrl: conv.avatarUrl || friend.avatar,
    }
  }

  function normalizeMessage(raw: Message): Message {
    return {
      ...raw,
      id: Number(raw.id),
      conversationId: Number(raw.conversationId),
      senderId: Number(raw.senderId),
    }
  }

  function mergeMessages(existing: Message[], incoming: Message[]): Message[] {
    const byKey = new Map<string, Message>()
    for (const msg of [...existing, ...incoming]) {
      const key = msg.id ? `id:${msg.id}` : msg.clientMsgId ? `cid:${msg.clientMsgId}` : `tmp:${msg.createdAt}:${msg.content}`
      byKey.set(key, msg)
    }
    return [...byKey.values()].sort(
      (a, b) => (parseDate(a.createdAt)?.getTime() ?? 0) - (parseDate(b.createdAt)?.getTime() ?? 0)
    )
  }

  function handleImEvent(event: ImEvent) {
    switch (event.event) {
      case 'MESSAGE_NEW': {
        const payload = event.payload as MessageNewPayload | Message
        const raw =
          payload && typeof payload === 'object' && 'message' in payload && payload.message
            ? payload.message
            : (payload as Message)
        if (!raw?.conversationId) break
        const message = normalizeMessage(raw)
        appendMessage(message)
        updateConversationPreview(message.conversationId, message)
        break
      }
      case 'MESSAGE_ACK': {
        const payload = event.payload as { clientMsgId?: string; messageId?: number }
        if (!payload?.clientMsgId || !payload.messageId) break
        for (const [conversationId, list] of Object.entries(messagesMap.value)) {
          const idx = list.findIndex((m) => m.clientMsgId === payload.clientMsgId)
          if (idx < 0) continue
          const updated = [...list]
          updated[idx] = { ...updated[idx], id: Number(payload.messageId) }
          messagesMap.value[Number(conversationId)] = updated
          break
        }
        break
      }
      case 'MESSAGE_READ': {
        const payload = event.payload as MessageReadPayload
        markMessageReadLocal(payload.conversationId, payload.messageId, payload.readerId)
        break
      }
      case 'PRESENCE': {
        const payload = event.payload as PresencePayload
        presenceMap.value[payload.userId] = payload.status
        const friend = friends.value.find((f) => f.userId === payload.userId)
        if (friend) friend.presenceStatus = payload.status
        break
      }
      case 'CALL_RINGING': {
        const payload = event.payload as CallRingingPayload
        incomingCall.value = {
          ...payload.call,
          fromUserName: payload.fromUser?.realName || payload.fromUser?.username,
        }
        break
      }
      case 'CALL_ANSWERED':
      case 'CALL_END':
        if (activeCall.value) endCallLocal()
        incomingCall.value = null
        break
      case 'TYPING': {
        const payload = event.payload as TypingPayload
        if (!typingUsers.value[payload.conversationId]) {
          typingUsers.value[payload.conversationId] = new Set()
        }
        if (payload.typing) {
          typingUsers.value[payload.conversationId].add(payload.userId)
        } else {
          typingUsers.value[payload.conversationId].delete(payload.userId)
        }
        break
      }
      default:
        break
    }
  }

  function appendMessage(message: Message) {
    const conversationId = Number(message.conversationId)
    const normalized = normalizeMessage({ ...message, conversationId })
    const list = messagesMap.value[conversationId] ?? []
    if (
      list.some(
        (m) =>
          (normalized.id && m.id === normalized.id) ||
          (normalized.clientMsgId && m.clientMsgId === normalized.clientMsgId)
      )
    ) {
      return
    }
    messagesMap.value[conversationId] = [...list, normalized]
  }

  function updateConversationPreview(conversationId: number, message: Message) {
    const userStore = useUserStore()
    const conv = conversations.value.find((c) => c.id === conversationId)
    if (!conv) return
    conv.lastMessageId = message.id
    conv.lastMessageAt = message.createdAt
    conv.lastMessagePreview = message.type === 'TEXT' ? message.content : `[${message.type}]`
    if (message.senderId !== userStore.userId && activeConversationId.value !== conversationId) {
      conv.unreadCount = (conv.unreadCount || 0) + 1
    }
    sortConversations()
  }

  function markMessageReadLocal(conversationId: number, messageId: number, readerId: number) {
    const list = messagesMap.value[conversationId]
    if (!list) return
    const msg = list.find((m) => m.id === messageId)
    if (msg && readerId !== msg.senderId) {
      msg.readCount = (msg.readCount || 0) + 1
    }
  }

  async function loadConversations() {
    conversations.value = (await getConversations()).map(patchDirectConversationPeer)
    sortConversations()
  }

  async function loadFriends() {
    friends.value = await getFriends()
    for (const f of friends.value) {
      if (f.presenceStatus) presenceMap.value[f.userId] = f.presenceStatus
    }
  }

  async function loadFriendRequests() {
    friendRequests.value = await getFriendRequests()
  }

  async function loadDepartments() {
    departments.value = await getDepartmentTree()
  }

  async function loadMessages(conversationId: number, before?: number) {
    const messages = await getMessages(conversationId, before)
    const existing = messagesMap.value[conversationId] ?? []
    messagesMap.value[conversationId] = mergeMessages(existing, messages)
    return messages
  }

  async function openConversation(conversationId: number) {
    activeConversationId.value = conversationId
    if (!messagesMap.value[conversationId]?.length) {
      await loadMessages(conversationId)
    }
    const conv = conversations.value.find((c) => c.id === conversationId)
    if (conv && conv.unreadCount > 0) {
      const msgs = messagesMap.value[conversationId]
      const lastId = msgs?.[msgs.length - 1]?.id ?? conv.lastMessageId
      if (lastId) {
        await markConversationRead(conversationId, lastId)
        sendReadReceipt(conversationId, lastId)
        conv.unreadCount = 0
      }
    }
  }

  async function startDirectChat(targetUserId: number) {
    const conv = patchDirectConversationPeer(await createDirectConversation(targetUserId))
    const idx = conversations.value.findIndex((c) => c.id === conv.id)
    if (idx >= 0) conversations.value[idx] = conv
    else conversations.value.unshift(conv)
    sortConversations()
    await openConversation(conv.id)
    return conv
  }

  async function createGroup(name: string, memberIds: number[]) {
    const conv = await createGroupConversation({ name, memberIds })
    conversations.value.unshift(conv)
    sortConversations()
    await openConversation(conv.id)
    return conv
  }

  async function sendMessage(payload: Omit<SendMessagePayload, 'clientMsgId'> & { clientMsgId?: string }) {
    const clientMsgId = payload.clientMsgId ?? crypto.randomUUID()
    const full: SendMessagePayload = { ...payload, clientMsgId }
    const message = await sendMessageRest(full)
    appendMessage(message)
    updateConversationPreview(message.conversationId, message)
  }

  async function uploadFile(file: File): Promise<number> {
    const presign = await presignAttachment({
      fileName: file.name,
      mimeType: file.type || 'application/octet-stream',
      size: file.size,
    })
    await fetch(presign.uploadUrl, {
      method: 'PUT',
      headers: presign.headers ?? { 'Content-Type': file.type || 'application/octet-stream' },
      body: file,
    })
    const attachment = await completeAttachment(presign.attachmentId)
    return attachment.id
  }

  async function search(q: string, conversationId?: number) {
    searchResults.value = await searchMessages(q, conversationId)
    return searchResults.value
  }

  async function acceptRequest(id: number) {
    await acceptFriendRequest(id)
    await loadFriendRequests()
    await loadFriends()
  }

  async function rejectRequest(id: number) {
    await rejectFriendRequest(id)
    await loadFriendRequests()
  }

  async function addFriend(toUserId: number, message?: string) {
    await sendFriendRequest(toUserId, message)
    await loadFriendRequests()
  }

  function setTyping(conversationId: number, typing: boolean) {
    sendTyping(conversationId, typing)
  }

  function connect() {
    connectStomp(
      handleImEvent,
      () => {
        wsConnected.value = true
        if (heartbeatTimer) clearInterval(heartbeatTimer)
        heartbeatTimer = setInterval(sendPresenceHeartbeat, 30000)
      },
      () => {
        wsConnected.value = false
      }
    )
  }

  function disconnect() {
    if (heartbeatTimer) clearInterval(heartbeatTimer)
    heartbeatTimer = null
    disconnectStomp()
    wsConnected.value = false
    endCallLocal()
  }

  async function startCall(conversationId: number, type: 'AUDIO' | 'VIDEO') {
    const call = await initiateCall(conversationId, type)
    activeCall.value = call
    const userStore = useUserStore()
    webrtcCall = new WebRtcCall(call.id, true, {}, {})
    await webrtcCall.start(type === 'VIDEO')
    return call
  }

  async function acceptIncomingCall() {
    if (!incomingCall.value) return
    const call = await answerCall(incomingCall.value.id)
    activeCall.value = call
    incomingCall.value = null
    webrtcCall = new WebRtcCall(call.id, false, {}, {})
    await webrtcCall.start(call.type === 'VIDEO')
  }

  async function rejectIncomingCall() {
    if (!incomingCall.value) return
    await rejectCall(incomingCall.value.id)
    incomingCall.value = null
  }

  async function endActiveCall() {
    if (activeCall.value) await endCall(activeCall.value.id)
    endCallLocal()
  }

  function endCallLocal() {
    webrtcCall?.hangup()
    webrtcCall = null
    activeCall.value = null
    incomingCall.value = null
  }

  function getWebrtcCall() {
    return webrtcCall
  }

  async function init() {
    await Promise.all([loadConversations(), loadFriends(), loadFriendRequests(), loadDepartments()])
    connect()
  }

  return {
    conversations,
    messagesMap,
    friends,
    friendRequests,
    departments,
    presenceMap,
    activeConversationId,
    activeConversation,
    activeMessages,
    wsConnected,
    typingUsers,
    searchResults,
    unreadTotal,
    activeCall,
    incomingCall,
    loadConversations,
    loadFriends,
    loadFriendRequests,
    loadDepartments,
    loadMessages,
    openConversation,
    startDirectChat,
    createGroup,
    sendMessage,
    uploadFile,
    search,
    acceptRequest,
    rejectRequest,
    addFriend,
    setTyping,
    connect,
    disconnect,
    init,
    startCall,
    acceptIncomingCall,
    rejectIncomingCall,
    endActiveCall,
    getWebrtcCall,
  }
})
