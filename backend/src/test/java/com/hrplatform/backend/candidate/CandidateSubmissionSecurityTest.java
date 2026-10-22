package com.hrplatform.backend.candidate;

import com.hrplatform.backend.model.entity.Answer;
import com.hrplatform.backend.model.entity.CandidateSession;
import com.hrplatform.backend.model.entity.SessionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CandidateSubmissionSecurityTest extends CandidateFlowTestBase {

    @Test
    void optionFromAnotherQuestionInSameAssessmentIsRejected() throws Exception {
        CandidateSession session = inProgressSession();

        submit(session, choice(q1, q2Correct), choice(q2, q2Wrong), choice(q3, q3False), text(q4, "x"))
                .andExpect(status().isBadRequest());

        assertNothingPersisted(session);
    }

    @Test
    void optionFromAnotherTenantIsRejected() throws Exception {
        CandidateSession session = inProgressSession();

        submit(session, choice(q1, foreignCorrect), choice(q2, q2Wrong), choice(q3, q3False), text(q4, "x"))
                .andExpect(status().isBadRequest());

        assertNothingPersisted(session);
    }

    @Test
    void questionFromAnotherAssessmentIsRejected() throws Exception {
        CandidateSession session = inProgressSession();

        submit(session, choice(otherQuestion, otherCorrect), choice(q1, q1Wrong))
                .andExpect(status().isBadRequest());

        assertNothingPersisted(session);
    }

    @Test
    void duplicateAnswerForSameQuestionIsRejected() throws Exception {
        CandidateSession session = inProgressSession();

        submit(session, choice(q1, q1Correct), choice(q1, q1Correct), choice(q1, q1Correct), choice(q2, q2Wrong))
                .andExpect(status().isBadRequest());

        assertNothingPersisted(session);
    }

    @Test
    void omittedQuestionsCountAsWrong() throws Exception {
        CandidateSession session = inProgressSession();

        // Only the one question the candidate knows: must score 1/3, not 1/1.
        submit(session, choice(q1, q1Correct))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(33.33));

        List<Answer> answers = answerRepository.findAllBySessionId(session.getId());
        assertThat(answers).hasSize(4);
        assertThat(answers).filteredOn(a -> a.getQuestion().getId().equals(q2.getId()))
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.getSelectedOption()).isNull();
                    assertThat(a.getIsCorrect()).isFalse();
                });
        assertThat(answers).filteredOn(a -> a.getQuestion().getId().equals(q4.getId()))
                .singleElement()
                .satisfies(a -> assertThat(a.getIsCorrect()).isNull());
    }

    @Test
    void secondSubmitIsRejected() throws Exception {
        CandidateSession session = inProgressSession();

        submit(session, choice(q1, q1Wrong), choice(q2, q2Wrong), choice(q3, q3False), text(q4, "first"))
                .andExpect(status().isOk());
        submit(session, choice(q1, q1Correct), choice(q2, q2Correct), choice(q3, q3True), text(q4, "second"))
                .andExpect(status().isBadRequest());

        assertThat(answerCount(session)).isEqualTo(4);
        assertThat(sessionRepository.findByInviteToken(session.getInviteToken()).orElseThrow().getScore())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void validSubmissionIsScoredAgainstScoreableQuestions() throws Exception {
        CandidateSession session = inProgressSession();

        submit(session, choice(q1, q1Correct), choice(q2, q2Wrong), choice(q3, q3True), text(q4, "free text"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.score").value(66.67));

        List<Answer> answers = answerRepository.findAllBySessionId(session.getId());
        assertThat(answers).hasSize(4);
        assertThat(answers).filteredOn(a -> a.getQuestion().getId().equals(q4.getId()))
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.getOpenTextAnswer()).isEqualTo("free text");
                    assertThat(a.getIsCorrect()).isNull();
                });
    }

    private void assertNothingPersisted(CandidateSession session) {
        assertThat(answerCount(session)).isZero();
        assertThat(statusOf(session)).isEqualTo(SessionStatus.IN_PROGRESS);
    }
}
