import type { Conversation, Friend } from '@/types/im'

export function getConversationDisplayTitle(
  conv: Conversation,
  friends: Friend[] = []
): string {
  if (conv.type === 'GROUP' && conv.title) return conv.title
  if (conv.type === 'DIRECT') {
    if (conv.peerUser) {
      return conv.peerUser.realName || conv.peerUser.username
    }
    if (conv.peerUserId) {
      const friend = friends.find((f) => f.userId === conv.peerUserId)
      if (friend) return friend.realName || friend.username
    }
    const peerMember = conv.members?.find((m) => m.userId === conv.peerUserId)
    if (peerMember) return peerMember.realName || peerMember.username || ''
    if (conv.title) return conv.title
    return `会话 ${conv.id}`
  }
  return conv.title || `会话 ${conv.id}`
}

export function getConversationAvatarUrl(conv: Conversation, friends: Friend[] = []): string | undefined {
  if (conv.avatarUrl) return conv.avatarUrl
  if (conv.peerUser?.avatar) return conv.peerUser.avatar
  if (conv.peerUserId) {
    return friends.find((f) => f.userId === conv.peerUserId)?.avatar
  }
  return undefined
}
