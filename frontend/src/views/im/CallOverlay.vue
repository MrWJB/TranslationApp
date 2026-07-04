<template>
  <Teleport to="body">
    <div v-if="incomingCall || activeCall" class="call-overlay">
      <div class="call-card">
        <template v-if="incomingCall">
          <div class="call-status">来电</div>
          <div class="call-name">{{ incomingCall.fromUserName || '未知用户' }}</div>
          <div class="call-type">{{ incomingCall.type === 'VIDEO' ? '视频通话' : '语音通话' }}</div>
          <div class="call-actions">
            <el-button type="success" circle size="large" :icon="Phone" @click="accept" />
            <el-button type="danger" circle size="large" :icon="Close" @click="reject" />
          </div>
        </template>
        <template v-else-if="activeCall">
          <div class="call-status">通话中</div>
          <div class="call-type">{{ activeCall.type === 'VIDEO' ? '视频通话' : '语音通话' }}</div>
          <div class="video-area">
            <video ref="localVideoRef" autoplay muted playsinline class="local-video" />
            <video ref="remoteVideoRef" autoplay playsinline class="remote-video" />
          </div>
          <div class="call-actions">
            <el-button circle :icon="Microphone" :type="isMuted ? 'danger' : 'default'" @click="toggleMute" />
            <el-button v-if="activeCall.type === 'VIDEO'" circle :icon="isVideoOff ? VideoPause : VideoCamera" @click="toggleVideo" />
            <el-button type="danger" circle size="large" :icon="PhoneFilled" @click="hangup" />
          </div>
        </template>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Close, Microphone, Phone, PhoneFilled, VideoCamera, VideoPause } from '@element-plus/icons-vue'
import { useImStore } from '@/stores/im'

const imStore = useImStore()
const localVideoRef = ref<HTMLVideoElement>()
const remoteVideoRef = ref<HTMLVideoElement>()
const isMuted = ref(false)
const isVideoOff = ref(false)

const incomingCall = computed(() => imStore.incomingCall)
const activeCall = computed(() => imStore.activeCall)

watch(activeCall, (call) => {
  if (!call) return
  const rtc = imStore.getWebrtcCall()
  if (!rtc) return
  // Streams attached via callbacks when WebRTC is wired
})

async function accept() {
  await imStore.acceptIncomingCall()
}

async function reject() {
  await imStore.rejectIncomingCall()
}

async function hangup() {
  await imStore.endActiveCall()
}

function toggleMute() {
  isMuted.value = !isMuted.value
  imStore.getWebrtcCall()?.toggleMute(isMuted.value)
}

function toggleVideo() {
  isVideoOff.value = !isVideoOff.value
  imStore.getWebrtcCall()?.toggleVideo(!isVideoOff.value)
}
</script>

<style scoped>
.call-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.75);
  display: flex;
  align-items: center;
  justify-content: center;
}

.call-card {
  background: var(--dialog-bg);
  border-radius: 16px;
  padding: 32px;
  min-width: 320px;
  text-align: center;
  color: var(--text-primary);
}

.call-status {
  font-size: 14px;
  color: var(--text-secondary);
}

.call-name {
  font-size: 22px;
  font-weight: 600;
  margin: 12px 0 4px;
}

.call-type {
  color: var(--text-secondary);
  margin-bottom: 24px;
}

.call-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 24px;
}

.video-area {
  position: relative;
  width: 100%;
  max-width: 480px;
  aspect-ratio: 16/9;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
  margin: 16px auto;
}

.remote-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.local-video {
  position: absolute;
  bottom: 8px;
  right: 8px;
  width: 120px;
  height: 90px;
  object-fit: cover;
  border-radius: 6px;
  border: 2px solid var(--border-color);
}
</style>
