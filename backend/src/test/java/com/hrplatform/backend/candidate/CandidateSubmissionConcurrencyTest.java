package com.hrplatform.backend.candidate;

import com.hrplatform.backend.model.entity.CandidateSession;
import com.hrplatform.backend.model.entity.SessionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Fires the same request from several real threads at once. The class is intentionally not
 * @Transactional: each request runs in its own transaction on its own pooled connection, so
 * the row lock on candidate_sessions is what actually serialises them.
 */
class CandidateSubmissionConcurrencyTest extends CandidateFlowTestBase {

    private static final int THREADS = 8;

    private ExecutorService executor;

    @BeforeEach
    void startExecutor() {
        executor = Executors.newFixedThreadPool(THREADS);
    }

    @AfterEach
    void stopExecutor() throws InterruptedException {
        // Wait for every in-flight request to finish, otherwise the next test's TRUNCATE
        // races with transactions that are still open.
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    }

    @RepeatedTest(5)
    void concurrentSubmitsAreAcceptedExactlyOnce() throws Exception {
        CandidateSession session = inProgressSession();

        List<Integer> statuses = runConcurrently(() -> submit(session,
                choice(q1, q1Correct), choice(q2, q2Correct), choice(q3, q3True), text(q4, "answer"))
                .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 400).hasSize(THREADS - 1);
        assertThat(answerCount(session)).isEqualTo(4);
        assertThat(statusOf(session)).isEqualTo(SessionStatus.COMPLETED);
    }

    @RepeatedTest(5)
    void concurrentStartsAreAcceptedExactlyOnce() throws Exception {
        CandidateSession session = session(SessionStatus.PENDING, null);

        // A fresh request builder per thread: builders are not thread-safe.
        List<Integer> statuses = runConcurrently(() ->
                mockMvc.perform(post("/api/candidate/session/{token}/start", session.getInviteToken()))
                        .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 400).hasSize(THREADS - 1);
        assertThat(statusOf(session)).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @Test
    void databaseRejectsSecondAnswerRowForSameQuestion() {
        CandidateSession session = inProgressSession();
        String insert = "INSERT INTO answers (session_id, question_id, is_correct) VALUES (?, ?, true)";

        jdbcTemplate.update(insert, session.getId(), q1.getId());

        assertThatThrownBy(() -> jdbcTemplate.update(insert, session.getId(), q1.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<Integer> runConcurrently(Callable<Integer> request) throws Exception {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                return request.call();
            }));
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(30, TimeUnit.SECONDS));
        }
        return statuses;
    }
}
