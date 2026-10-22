package com.hrplatform.backend.candidate;

import com.hrplatform.backend.model.entity.CandidateSession;
import com.hrplatform.backend.model.entity.SessionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CandidateQuestionExposureTest extends CandidateFlowTestBase {

    @Test
    void pendingSessionExposesCountButNotQuestions() throws Exception {
        CandidateSession session = session(SessionStatus.PENDING, null);

        mockMvc.perform(get("/api/candidate/session/{token}", session.getInviteToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.questionCount").value(4))
                .andExpect(jsonPath("$.questions").isEmpty());
    }

    @Test
    void startReturnsQuestionsWithoutCorrectnessFlags() throws Exception {
        CandidateSession session = session(SessionStatus.PENDING, null);

        mockMvc.perform(post("/api/candidate/session/{token}/start", session.getInviteToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.questionCount").value(4))
                .andExpect(jsonPath("$.questions.length()").value(4))
                .andExpect(jsonPath("$.questions[0].options[0].id").exists())
                .andExpect(jsonPath("$.questions[0].options[0].isCorrect").doesNotExist());
    }

    @Test
    void completedSessionDoesNotExposeQuestions() throws Exception {
        CandidateSession session = session(SessionStatus.IN_PROGRESS, Instant.now());
        submit(session, choice(q1, q1Correct)).andExpect(status().isOk());

        mockMvc.perform(get("/api/candidate/session/{token}", session.getInviteToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.questions").isEmpty());
    }
}
