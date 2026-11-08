package com.ice.iceenglish.modules.submissions.service;

import com.ice.iceenglish.modules.exams.dto.ExamSnapshot;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.enums.PartType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartOrderTest {

    @Test
    void shuffleKeepsFixedPartsAndShufflesWithinGroup() {
        UUID fixed = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ExamSnapshot snapshot = new ExamSnapshot(UUID.randomUUID(), "Exam", ExamType.MOCK,
                ExamStatus.PUBLISHED, 50, true, List.of(
                new ExamSnapshot.PartSnap(fixed, "Fixed", PartType.GRAMMAR, null, null, 1, true, null),
                new ExamSnapshot.PartSnap(first, "First", PartType.GRAMMAR, null, null, 2, false, "g"),
                new ExamSnapshot.PartSnap(second, "Second", PartType.GRAMMAR, null, null, 3, false, "g")),
                List.of());

        List<UUID> order = PartOrder.shuffled(snapshot, new java.util.Random() {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        });

        assertEquals(List.of(fixed, second, first), order);
    }
}