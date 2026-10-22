package com.hrplatform.backend.service;

import com.hrplatform.backend.exception.BadRequestException;
import com.hrplatform.backend.exception.ResourceNotFoundException;
import com.hrplatform.backend.model.dto.candidate.*;
import com.hrplatform.backend.model.entity.*;
import com.hrplatform.backend.repository.AnswerRepository;
import com.hrplatform.backend.repository.CandidateSessionRepository;
import com.hrplatform.backend.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CandidateService {

    private final CandidateSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;

    public CandidateSessionView loadSession(String token) {
        CandidateSession session = findSessionByToken(token);
        Assessment assessment = session.getAssessment();

        long questionCount = questionRepository.countByAssessmentId(assessment.getId());

        // Question content is only exposed while the timer is running.
        List<CandidateQuestionView> questions = session.getStatus() == SessionStatus.IN_PROGRESS
                ? questionRepository.findAllByAssessmentIdOrderByOrderIndexAsc(assessment.getId())
                        .stream()
                        .map(this::toQuestionView)
                        .toList()
                : List.of();

        return new CandidateSessionView(
                session.getId(),
                assessment.getTitle(),
                assessment.getDurationMinutes(),
                session.getStatus(),
                session.getStartedAt(),
                questionCount,
                questions
        );
    }

    public CandidateSessionView startSession(String token) {
        CandidateSession session = lockSessionByToken(token);

        if (session.getStatus() != SessionStatus.PENDING) {
            throw new BadRequestException("Session is already " + session.getStatus());
        }
        // Only new starts are blocked: a session already in progress may still be submitted.
        if (!session.getAssessment().getIsActive()) {
            throw new BadRequestException("This assessment is no longer accepting responses");
        }

        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setStartedAt(Instant.now());
        sessionRepository.save(session);

        return loadSession(token);
    }

    public SubmitResponse submitAnswers(String token, SubmitRequest request) {
        CandidateSession session = lockSessionByToken(token);

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new BadRequestException("Session is not in progress");
        }

        Instant deadline = session.getStartedAt()
                .plus(Duration.ofMinutes(session.getAssessment().getDurationMinutes()))
                .plusSeconds(30);
        if (Instant.now().isAfter(deadline)) {
            throw new BadRequestException("Time has expired");
        }

        List<Question> questions = questionRepository
                .findAllByAssessmentIdOrderByOrderIndexAsc(session.getAssessment().getId());
        Map<Long, AnswerSubmission> submissions = validateSubmissions(request.answers(), questions);

        // Every question of the assessment gets exactly one answer row. Questions missing
        // from the request are stored as unanswered and count as wrong.
        int correct = 0;
        int scoreable = 0;
        List<Answer> answers = new ArrayList<>();

        for (Question question : questions) {
            AnswerSubmission submission = submissions.get(question.getId());

            Answer answer = new Answer();
            answer.setSession(session);
            answer.setQuestion(question);

            if (question.getType() == QuestionType.OPEN_TEXT) {
                answer.setOpenTextAnswer(submission != null ? submission.openTextAnswer() : null);
                answer.setIsCorrect(null);
            } else {
                scoreable++;
                QuestionOption option = submission != null
                        ? findOption(question, submission.selectedOptionId())
                        : null;
                answer.setSelectedOption(option);
                answer.setIsCorrect(option != null && option.getIsCorrect());
                if (answer.getIsCorrect()) correct++;
            }

            answers.add(answer);
        }

        answerRepository.saveAll(answers);

        BigDecimal score = scoreable > 0
                ? BigDecimal.valueOf(correct * 100.0 / scoreable).setScale(2, RoundingMode.HALF_UP)
                : null;

        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(Instant.now());
        session.setScore(score);
        sessionRepository.save(session);

        return new SubmitResponse(session.getId(), session.getStatus(), score, session.getCompletedAt());
    }

    /**
     * Rejects the whole submission (nothing is persisted) if any entry references a question
     * outside this session's assessment, repeats a question, or selects an option that does
     * not belong to its question.
     */
    private Map<Long, AnswerSubmission> validateSubmissions(List<AnswerSubmission> submissions,
                                                            List<Question> questions) {
        Map<Long, Question> questionsById = questions.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<Long, AnswerSubmission> byQuestionId = new HashMap<>();

        for (AnswerSubmission submission : submissions) {
            Question question = questionsById.get(submission.questionId());
            if (question == null) {
                throw new BadRequestException("Question does not belong to this assessment");
            }
            if (byQuestionId.putIfAbsent(question.getId(), submission) != null) {
                throw new BadRequestException("Duplicate answer for question " + question.getId());
            }
            if (submission.selectedOptionId() != null
                    && findOption(question, submission.selectedOptionId()) == null) {
                throw new BadRequestException("Option does not belong to this question");
            }
        }

        return byQuestionId;
    }

    private QuestionOption findOption(Question question, Long optionId) {
        if (optionId == null) return null;
        return question.getOptions().stream()
                .filter(o -> o.getId().equals(optionId))
                .findFirst()
                .orElse(null);
    }

    private CandidateSession findSessionByToken(String token) {
        return sessionRepository.findByInviteToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
    }

    // Row lock held until the transaction commits, so a concurrent start/submit for the same
    // token waits and then sees the updated status.
    private CandidateSession lockSessionByToken(String token) {
        return sessionRepository.findByInviteTokenForUpdate(token)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
    }

    private CandidateQuestionView toQuestionView(Question q) {
        List<CandidateOptionView> options = q.getOptions().stream()
                .map(o -> new CandidateOptionView(o.getId(), o.getText()))
                .toList();

        return new CandidateQuestionView(q.getId(), q.getText(), q.getType(), q.getOrderIndex(), options);
    }
}
