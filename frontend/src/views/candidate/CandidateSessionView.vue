<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSession, startSession, submitAnswers } from '@/api/candidate'
import CenteredState from '@/components/ui/CenteredState.vue'
import CandidateIntroScreen from '@/components/candidate/CandidateIntroScreen.vue'
import CandidateQuizScreen from '@/components/candidate/CandidateQuizScreen.vue'

const route = useRoute()
const router = useRouter()

const token = route.params.token
const session = ref(null)
const answers = ref({})
const error = ref(null)
const loading = ref(false)
const currentIdx = ref(0)

const totalCount = computed(() => session.value?.questionCount ?? 0)

onMounted(async () => {
  try {
    const res = await getSession(token)
    session.value = res.data
    // Questions are only returned once the session is in progress (e.g. after a reload).
    if (session.value.status === 'IN_PROGRESS') initAnswers()
  } catch {
    error.value = 'Session not found or the link has expired.'
  }
})

function initAnswers() {
  session.value.questions.forEach((q) => {
    answers.value[q.id] = { selectedOptionId: null, openTextAnswer: '' }
  })
}

async function handleStart() {
  loading.value = true
  try {
    const res = await startSession(token)
    session.value = res.data
    initAnswers()
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to start session'
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  loading.value = true
  error.value = null
  try {
    const payload = session.value.questions.map((q) => ({
      questionId: q.id,
      selectedOptionId: answers.value[q.id]?.selectedOptionId || null,
      openTextAnswer: answers.value[q.id]?.openTextAnswer || null,
    }))
    const res = await submitAnswers(token, { answers: payload })
    router.push({
      name: 'candidate-complete',
      params: { token },
      state: {
        result: res.data,
        questions: session.value.questions,
        answers: answers.value,
      },
    })
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to submit'
  } finally {
    loading.value = false
  }
}

function selectOption(questionId, optId) {
  answers.value[questionId].selectedOptionId = optId
}

function updateAnswer(questionId, text) {
  answers.value[questionId].openTextAnswer = text
}
</script>

<template>
  <div class="candidate">

    <CenteredState v-if="error && !session" variant="error" icon="⚠" title="Something went wrong" :message="error" />
    <CenteredState v-else-if="!session" variant="loading" title="LOADING…" />

    <template v-else>
      <CandidateIntroScreen
        v-if="session.status === 'PENDING'"
        :session="session"
        :total-count="totalCount"
        :loading="loading"
        :error="error"
        @start="handleStart"
      />

      <CandidateQuizScreen
        v-else-if="session.status === 'IN_PROGRESS'"
        :session="session"
        :answers="answers"
        :current-idx="currentIdx"
        :loading="loading"
        :error="error"
        @update:current-idx="currentIdx = $event"
        @submit="handleSubmit"
        @select-option="selectOption"
        @update-answer="updateAnswer"
      />

      <CenteredState
        v-else-if="session.status === 'COMPLETED'"
        title="Already submitted"
        message="This assessment has already been completed."
      />
    </template>
  </div>
</template>
