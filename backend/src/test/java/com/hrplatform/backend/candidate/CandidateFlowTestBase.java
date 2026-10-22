package com.hrplatform.backend.candidate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrplatform.backend.IntegrationTestBase;
import com.hrplatform.backend.model.entity.Assessment;
import com.hrplatform.backend.model.entity.CandidateSession;
import com.hrplatform.backend.model.entity.Company;
import com.hrplatform.backend.model.entity.Question;
import com.hrplatform.backend.model.entity.QuestionOption;
import com.hrplatform.backend.model.entity.QuestionType;
import com.hrplatform.backend.model.entity.SessionStatus;
import com.hrplatform.backend.repository.AnswerRepository;
import com.hrplatform.backend.repository.AssessmentRepository;
import com.hrplatform.backend.repository.CandidateSessionRepository;
import com.hrplatform.backend.repository.CompanyRepository;
import com.hrplatform.backend.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared fixture for candidate-flow tests. Deliberately not @Transactional: every request
 * runs in its own real transaction, exactly as in production.
 *
 * Tenant A owns "main" (MCQ, MCQ, TRUE_FALSE, OPEN_TEXT) and "other" (one MCQ).
 * Tenant B owns "foreign" (one MCQ).
 */
abstract class CandidateFlowTestBase extends IntegrationTestBase {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected CompanyRepository companyRepository;
    @Autowired protected AssessmentRepository assessmentRepository;
    @Autowired protected QuestionRepository questionRepository;
    @Autowired protected CandidateSessionRepository sessionRepository;
    @Autowired protected AnswerRepository answerRepository;

    protected Assessment mainAssessment;
    protected Question q1, q2, q3, q4;
    protected Long q1Correct, q1Wrong, q2Correct, q2Wrong, q3True, q3False;

    protected Question otherQuestion;
    protected Long otherCorrect;

    protected Question foreignQuestion;
    protected Long foreignCorrect;

    @BeforeEach
    void setUpFixture() {
        jdbcTemplate.execute("TRUNCATE companies CASCADE");

        Company tenantA = companyRepository.save(Company.builder().name("Tenant A").email("a@example.com").build());
        Company tenantB = companyRepository.save(Company.builder().name("Tenant B").email("b@example.com").build());

        mainAssessment = assessment(tenantA, "Main");
        q1 = question(mainAssessment, QuestionType.MULTIPLE_CHOICE, 0, "correct", "wrong");
        q2 = question(mainAssessment, QuestionType.MULTIPLE_CHOICE, 1, "correct", "wrong");
        q3 = question(mainAssessment, QuestionType.TRUE_FALSE, 2, "correct", "wrong");
        q4 = question(mainAssessment, QuestionType.OPEN_TEXT, 3);
        q1Correct = optionId(q1, "correct");
        q1Wrong = optionId(q1, "wrong");
        q2Correct = optionId(q2, "correct");
        q2Wrong = optionId(q2, "wrong");
        q3True = optionId(q3, "correct");
        q3False = optionId(q3, "wrong");

        Assessment other = assessment(tenantA, "Other");
        otherQuestion = question(other, QuestionType.MULTIPLE_CHOICE, 0, "correct");
        otherCorrect = optionId(otherQuestion, "correct");

        Assessment foreign = assessment(tenantB, "Foreign");
        foreignQuestion = question(foreign, QuestionType.MULTIPLE_CHOICE, 0, "correct");
        foreignCorrect = optionId(foreignQuestion, "correct");
    }

    protected CandidateSession inProgressSession() {
        return session(SessionStatus.IN_PROGRESS, Instant.now());
    }

    protected CandidateSession session(SessionStatus status, Instant startedAt) {
        CandidateSession session = new CandidateSession();
        session.setAssessment(mainAssessment);
        session.setCandidateName("Candidate");
        session.setCandidateEmail("candidate@example.com");
        session.setInviteToken(UUID.randomUUID().toString());
        session.setStatus(status);
        session.setStartedAt(startedAt);
        return sessionRepository.save(session);
    }

    protected ResultActions submit(CandidateSession session, Map<?, ?>... answers) throws Exception {
        return mockMvc.perform(post("/api/candidate/session/{token}/submit", session.getInviteToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("answers", Arrays.asList(answers)))));
    }

    protected static Map<String, Object> choice(Question question, Long optionId) {
        Map<String, Object> answer = new HashMap<>();
        answer.put("questionId", question.getId());
        answer.put("selectedOptionId", optionId);
        return answer;
    }

    protected static Map<String, Object> text(Question question, String value) {
        Map<String, Object> answer = new HashMap<>();
        answer.put("questionId", question.getId());
        answer.put("openTextAnswer", value);
        return answer;
    }

    protected int answerCount(CandidateSession session) {
        return answerRepository.findAllBySessionId(session.getId()).size();
    }

    protected SessionStatus statusOf(CandidateSession session) {
        return sessionRepository.findByInviteToken(session.getInviteToken()).orElseThrow().getStatus();
    }

    private Assessment assessment(Company company, String title) {
        Assessment assessment = new Assessment();
        assessment.setCompany(company);
        assessment.setTitle(title);
        assessment.setDurationMinutes(30);
        return assessmentRepository.save(assessment);
    }

    private Question question(Assessment assessment, QuestionType type, int order, String... optionTexts) {
        Question question = new Question();
        question.setAssessment(assessment);
        question.setText(assessment.getTitle() + " question " + order);
        question.setType(type);
        question.setOrderIndex(order);
        for (String optionText : optionTexts) {
            QuestionOption option = new QuestionOption();
            option.setQuestion(question);
            option.setText(optionText);
            option.setIsCorrect(optionText.equals("correct"));
            question.getOptions().add(option);
        }
        return questionRepository.save(question);
    }

    private static Long optionId(Question question, String text) {
        List<QuestionOption> options = question.getOptions();
        return options.stream().filter(o -> o.getText().equals(text)).findFirst().orElseThrow().getId();
    }
}
