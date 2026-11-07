package com.ice.iceenglish.modules.submissions.service;

import com.ice.iceenglish.modules.exams.dto.ExamSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** Ordering for one attempt; the original snapshot is never modified. */
public final class PartOrder {
    private PartOrder() {}

    public static List<UUID> shuffled(ExamSnapshot exam) {
        return shuffled(exam, ThreadLocalRandom.current());
    }

    static List<UUID> shuffled(ExamSnapshot exam, Random random) {
        List<ExamSnapshot.PartSnap> parts = exam.parts();
        List<UUID> result = new ArrayList<>(parts.stream().map(ExamSnapshot.PartSnap::id).toList());
        Map<String, List<Integer>> slots = new HashMap<>();
        for (int i = 0; i < parts.size(); i++) {
            ExamSnapshot.PartSnap part = parts.get(i);
            if (!part.fixedPosition()) {
                slots.computeIfAbsent(part.shuffleGroup(), key -> new ArrayList<>()).add(i);
            }
        }
        for (List<Integer> positions : slots.values()) {
            List<UUID> ids = positions.stream().map(result::get).collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(ids, random);
            for (int i = 0; i < positions.size(); i++) {
                result.set(positions.get(i), ids.get(i));
            }
        }
        return result;
    }

    public static List<UUID> resolve(List<UUID> stored, List<UUID> original) {
        if (stored == null) {
            return List.copyOf(original);
        }
        if (stored.size() != original.size() || new HashSet<>(stored).size() != stored.size()
                || !new HashSet<>(stored).equals(new HashSet<>(original))) {
            throw new IllegalStateException("Thứ tự part đã lưu không khớp với đề thi.");
        }
        return List.copyOf(stored);
    }

    public static Map<UUID, Integer> indexes(List<UUID> order) {
        Map<UUID, Integer> result = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            result.put(order.get(i), i + 1);
        }
        return result;
    }
}
