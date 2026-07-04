import { api } from './index'
import type {
  Attachment,
  CallSession,
  Conversation,
  CreateGroupRequest,
  Department,
  Friend,
  FriendRequest,
  ImUserSummary,
  Message,
  PresignRequest,
  PresignResponse,
  UpdateConversationRequest,
} from '@/types/im'

const IM = '/im'

// Contacts / Organization
export const getDepartmentTree = (): Promise<Department[]> =>
  api.get(`${IM}/contacts/departments/tree`)

export const getDepartmentUsers = (departmentId: number): Promise<ImUserSummary[]> =>
  api.get(`${IM}/contacts/departments/${departmentId}/users`)

export const searchUsers = (q: string): Promise<ImUserSummary[]> =>
  api.get(`${IM}/contacts/users/search`, { params: { q } })

export const getFriends = (): Promise<Friend[]> =>
  api.get(`${IM}/contacts/friends`)

export const sendFriendRequest = (toUserId: number, message?: string): Promise<FriendRequest> =>
  api.post(`${IM}/contacts/friend-requests`, { toUserId, message })

export const getFriendRequests = (): Promise<FriendRequest[]> =>
  api.get(`${IM}/contacts/friend-requests`)

export const acceptFriendRequest = (id: number): Promise<void> =>
  api.put(`${IM}/contacts/friend-requests/${id}/accept`)

export const rejectFriendRequest = (id: number): Promise<void> =>
  api.put(`${IM}/contacts/friend-requests/${id}/reject`)

export const deleteFriend = (userId: number): Promise<void> =>
  api.delete(`${IM}/contacts/friends/${userId}`)

export const blockFriend = (userId: number): Promise<void> =>
  api.put(`${IM}/contacts/friends/${userId}/block`)

// Departments (admin)
export const createDepartment = (data: Partial<Department>): Promise<Department> =>
  api.post(`${IM}/contacts/departments`, data)

export const updateDepartment = (id: number, data: Partial<Department>): Promise<Department> =>
  api.put(`${IM}/contacts/departments/${id}`, data)

export const deleteDepartment = (id: number): Promise<void> =>
  api.delete(`${IM}/contacts/departments/${id}`)

export const assignUserDepartment = (userId: number, departmentId: number, isPrimary = true): Promise<void> =>
  api.post(`${IM}/contacts/departments/${departmentId}/users`, { userId, isPrimary })

// Conversations
export const getConversations = (): Promise<Conversation[]> =>
  api.get(`${IM}/conversations`)

export const createDirectConversation = (targetUserId: number): Promise<Conversation> =>
  api.post(`${IM}/conversations/direct`, { targetUserId })

export const createGroupConversation = (data: CreateGroupRequest): Promise<Conversation> =>
  api.post(`${IM}/conversations/group`, data)

export const getConversation = (id: number): Promise<Conversation> =>
  api.get(`${IM}/conversations/${id}`)

export const updateConversation = (id: number, data: UpdateConversationRequest): Promise<Conversation> =>
  api.put(`${IM}/conversations/${id}`, data)

export const inviteMembers = (conversationId: number, memberIds: number[]): Promise<Conversation> =>
  api.post(`${IM}/conversations/${conversationId}/members`, { memberIds })

export const removeMember = (conversationId: number, userId: number): Promise<void> =>
  api.delete(`${IM}/conversations/${conversationId}/members/${userId}`)

export const markConversationRead = (conversationId: number, lastMessageId: number): Promise<void> =>
  api.put(`${IM}/conversations/${conversationId}/read`, { lastMessageId })

// Messages
export const getMessages = (
  conversationId: number,
  before?: number,
  limit = 30
): Promise<Message[]> =>
  api.get(`${IM}/messages/conversations/${conversationId}`, { params: { before, limit } })

export const searchMessages = (q: string, conversationId?: number): Promise<Message[]> =>
  api.get(`${IM}/messages/search`, { params: { q, conversationId } })

export const sendMessageRest = (data: {
  conversationId: number
  type: string
  content: string
  attachmentId?: number
  replyToId?: number
  clientMsgId: string
}): Promise<Message> =>
  api.post(`${IM}/messages`, data)

// Attachments
export const presignAttachment = (data: PresignRequest): Promise<PresignResponse> =>
  api.post(`${IM}/attachments/presign`, data)

export const completeAttachment = (attachmentId: number): Promise<Attachment> =>
  api.post(`${IM}/attachments/complete`, { attachmentId })

export const getAttachmentDownloadUrl = (id: number): Promise<{ downloadUrl: string }> =>
  api.get(`${IM}/attachments/${id}/download-url`)

// Calls
export const initiateCall = (conversationId: number, type: 'AUDIO' | 'VIDEO'): Promise<CallSession> =>
  api.post(`${IM}/calls`, { conversationId, type })

export const answerCall = (callId: number): Promise<CallSession> =>
  api.put(`${IM}/calls/${callId}/answer`)

export const rejectCall = (callId: number): Promise<CallSession> =>
  api.put(`${IM}/calls/${callId}/reject`)

export const endCall = (callId: number): Promise<CallSession> =>
  api.put(`${IM}/calls/${callId}/end`)
