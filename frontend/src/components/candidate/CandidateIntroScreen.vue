<script setup>
import BrandLogo from '@/components/layout/BrandLogo.vue'
import ThemeToggle from '@/components/layout/ThemeToggle.vue'

defineProps({
  session: { type: Object, required: true },
  totalCount: { type: Number, required: true },
  loading: { type: Boolean, default: false },
  error: { type: String, default: null },
})
const emit = defineEmits(['start'])
</script>

<template>
  <div class="cand-top">
    <div class="cand-top__brand">
      <BrandLogo to="/" />
    </div>
    <div class="cand-top__meta">
      <span>ASSESSMENT INVITE</span>
      <ThemeToggle />
    </div>
  </div>

  <div class="cand-main">
    <div class="intro-body">
      <span class="eyebrow">Ready when you are</span>
      <h1 class="display intro-title">{{ session.assessmentTitle }}</h1>
      <p class="intro-desc">
        You have <strong class="intro-strong">{{ session.durationMinutes }} minutes</strong> to answer
        <strong class="intro-strong">{{ totalCount }} question{{ totalCount !== 1 ? 's' : '' }}</strong>.
        The timer starts when you press the button below.
      </p>
      <div class="intro-tags">
        <span class="tag">⏱ {{ session.durationMinutes }} min</span>
        <span class="tag">{{ totalCount }} questions</span>
        <span class="tag">Auto-save</span>
      </div>
      <button
        class="btn btn--accent btn--lg intro-start"
        :disabled="loading"
        @click="emit('start')"
      >
        <span v-if="loading" class="spinner" />
        ▶ Begin assessment
      </button>
      <p v-if="error" class="error-text">{{ error }}</p>
      <p class="mono intro-note">YOUR ANSWERS ARE AUTO-SAVED</p>
    </div>
  </div>
</template>

<style scoped>
.intro-body {
  max-width: 560px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 22px;
  align-items: center;
}
.intro-title { font-size: 52px; line-height: 1.04; letter-spacing: -0.02em; margin: 0; }
.intro-desc { color: var(--muted); font-size: 15px; max-width: 44ch; }
.intro-strong { color: var(--ink); }
.intro-tags { display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; }
.intro-start { margin-top: 20px; padding: 14px 28px; }
.intro-note { font-size: 10.5px; color: var(--faint); letter-spacing: 0.1em; }
</style>
