package com.hrplatform.backend.candidate;

import com.hrplatform.backend.model.entity.CandidateSession;
import com.hrplatform.backend.model.entity.SessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CandidateSessionRulesTest extends CandidateFlowTestBase {

    @Test
    void startIsRejectedWhenAssessmentIsInactive() throws Exception {
        deactivateMainAssessment();
        CandidateSession session = session(SessionStatus.PENDING, null);

        mockMvc.perform(post("/api/candidate/session/{token}/start", session.getInviteToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This assessment is no longer accepting responses"));

        assertThat(statusOf(session)).isEqualTo(SessionStatus.PENDING);
    }

    @Test
    void sessionInProgressCanStillBeSubmittedAfterDeactivation() throws Exception {
        CandidateSession session = session(SessionStatus.IN_PROGRESS, Instant.now());
        deactivateMainAssessment();

        submit(session, choice(q1, q1Correct), choice(q2, q2Correct), choice(q3, q3True), text(q4, "x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(100.0));
    }

    @Test
    void assessmentWithoutDurationCannotBeStored() {
        Long companyId = mainAssessment.getCompanyId() != null
                ? mainAssessment.getCompanyId()
                : mainAssessment.getCompany().getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO assessments (company_id, title, duration_minutes) VALUES (?, 'Untimed', NULL)",
                companyId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void deactivateMainAssessment() {
        mainAssessment.setIsActive(false);
        mainAssessment = assessmentRepository.save(mainAssessment);
    }
}
