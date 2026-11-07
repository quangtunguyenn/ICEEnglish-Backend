package com.ice.iceenglish.modules.submissions.service;

import com.ice.iceenglish.modules.exams.dto.ExamSnapshot;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.enums.PartType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PartOrderTest {
    @Test
    void shuffleKeepsFixedAndGroupSlotsAndAllowsDifferentOrders() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), fixed = UUID.randomUUID();
        UUID c = UUID.randomUUID(), d = UUID.randomUUID(), common1 = UUID.randomUUID(), common2 = UUID.randomUUID();
        List<ExamSnapshot.PartSnap> parts = List.of(
                part(a, 1, false, "A"), part(c, 2, false, "B"), part(fixed, 3, true, "A"),
                part(b, 4, false, "A"), part(d, 5, false, "B"),
                part(common1, 6, false, null), part(common2, 7, false, null));
        ExamSnapshot exam = new ExamSnapshot(UUID.randomUUID(), "Exam", ExamType.MOCK,
                ExamStatus.PUBLISHED, 50, true, parts, List.of());
        Random swap = new Random() {
            @Override public int nextInt(int bound) { return 0; }
        };
        Random identity = new Random() {
            @Override public int nextInt(int bound) { return bound - 1; }
        };

        assertEquals(List.of(b, d, fixed, a, c, common2, common1), PartOrder.shuffled(exam, swap));
        assertEquals(List.of(a, c, fixed, b, d, common1, common2), PartOrder.shuffled(exam, identity));
        assertEquals(a, exam.parts().get(0).id());
    }

    @Test
    void fallsBackOnlyForAbsentOrderAndRejectsCorruptOrders() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), other = UUID.randomUUID();
        assertEquals(List.of(a, b), PartOrder.resolve(null, List.of(a, b)));
        assertEquals(List.of(b, a), PartOrder.resolve(List.of(b, a), List.of(a, b)));
        assertThrows(IllegalStateException.class, () -> PartOrder.resolve(List.of(a), List.of(a, b)));
        assertThrows(IllegalStateException.class, () -> PartOrder.resolve(List.of(a, a), List.of(a, b)));
        assertThrows(IllegalStateException.class, () -> PartOrder.resolve(List.of(a, other), List.of(a, b)));
    }

    private ExamSnapshot.PartSnap part(UUID id, int order, boolean fixed, String group) {
        return new ExamSnapshot.PartSnap(id, "Part", PartType.GRAMMAR, null, null, order, fixed, group);
    }
}
