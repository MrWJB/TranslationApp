export type ConversationType = 'DIRECT' | 'GROUP'
export type MessageType = 'TEXT' | 'IMAGE' | 'FILE' | 'LINK' | 'SYSTEM' | 'CALL'
export type FriendshipStatus = 'PENDING' | 'ACCEPTED' | 'BLOCKED'
export type PresenceStatus = 'ONLINE' | 'OFFLINE' | 'AWAY'
export type CallType = 'AUDIO' | 'VIDEO'
export type CallStatus = 'RINGING' | 'ACTIVE' | 'ENDED' | 'MISSED'
export type MemberRole = 'OWNER' | 'ADMIN' | 'MEMBER'

export type ImEventType =
  | 'MESSAGE_NEW'
  | 'MESSAGE_ACK'
  | 'MESSAGE_READ'
  | 'PRESENCE'
  | 'CALL_RINGING'
  | 'CALL_ANSWERED'
  | 'CALL_END'
  | 'TYPING'
  | 'ACK'

export interface ImUserSummary {
  id: number
  username: string
  realName?: string
  avatar?: string
  phone?: string
  presenceStatus?: PresenceStatus
}

export interface Department {
  id: number
  name: string
  parentId?: number | null
  sortOrder: number
  leaderUserId?: number | null
  memberCount?: number
  children?: Department[]
}

export interface Friend {
  userId: number
  username: string
  realName?: string
  avatar?: string
  remark?: string
  status: FriendshipStatus
  presenceStatus?: PresenceStatus
}

export interface FriendRequest {
  id: number
  fromUserId: number
  toUserId: number
  fromUser?: ImUserSummary
  toUser?: ImUserSummary
  message?: string
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED'
  createdAt: string
}

export interface ConversationMember {
  userId: number
  username?: string
  realName?: string
  avatar?: string
  role: MemberRole
  unreadCount: number
  pinned?: boolean
  joinedAt?: string
}

export interface Conversation {
  id: number
  type: ConversationType
  title?: string
  avatarUrl?: string
  ownerId?: number
  announcement?: string
  lastMessageId?: number
  lastMessageAt?: string
  lastMessagePreview?: string
  unreadCount: number
  pinned?: boolean
  members?: ConversationMember[]
  peerUserId?: number
  peerUser?: ImUserSummary
}

export interface Attachment {
  id: number
  uploaderId: number
  fileName: string
  mimeType: string
  sizeBytes: number
  downloadUrl?: string
  createdAt?: string
}

export interface Message {
  id: number
  conversationId: number
  senderId: number
  senderName?: string
  senderAvatar?: string
  type: MessageType
  content: string
  attachmentId?: number
  attachment?: Attachment
  replyToId?: number
  replyTo?: Message
  clientMsgId?: string
  readByMe?: boolean
  readCount?: number
  createdAt: string
}

export interface CallSession {
  id: number
  conversationId: number
  initiatorId: number
  type: CallType
  status: CallStatus
  startedAt?: string
  endedAt?: string
}

export interface ImEvent<T = unknown> {
  event: ImEventType
  payload: T
  timestamp: string
}

export interface MessageNewPayload {
  message: Message
  conversationId: number
}

export interface MessageReadPayload {
  conversationId: number
  messageId: number
  readerId: number
}

export interface PresencePayload {
  userId: number
  status: PresenceStatus
  lastSeen?: string
}

export interface CallRingingPayload {
  call: CallSession
  fromUser?: ImUserSummary
}

export interface TypingPayload {
  conversationId: number
  userId: number
  typing: boolean
}

export interface SendMessagePayload {
  conversationId: number
  type: MessageType
  content: string
  attachmentId?: number
  replyToId?: number
  clientMsgId: string
}

export interface PresignRequest {
  fileName: string
  mimeType: string
  size: number
}

export interface PresignResponse {
  attachmentId: number
  uploadUrl: string
  headers?: Record<string, string>
}

export interface CreateGroupRequest {
  name: string
  memberIds: number[]
}

export interface UpdateConversationRequest {
  title?: string
  announcement?: string
  avatarUrl?: string
}
