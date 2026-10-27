package net.atos.mev.calendarcalculator.setup.draw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DrawBatchServiceLimitTest {
    @Test
    void requiresPositiveCandidateCountWithoutAFunctionalMaximum() {
        assertEquals(1_001, DrawBatchService.requireCandidates(1_001));
        assertEquals(Integer.MAX_VALUE, DrawBatchService.requireCandidates(Integer.MAX_VALUE));
        assertEquals("candidates is required and must be a positive integer",
                assertThrows(IllegalArgumentException.class, () -> DrawBatchService.requireCandidates(null)).getMessage());
        assertEquals("candidates is required and must be a positive integer",
                assertThrows(IllegalArgumentException.class, () -> DrawBatchService.requireCandidates(0)).getMessage());
        assertEquals("candidates is required and must be a positive integer",
                assertThrows(IllegalArgumentException.class, () -> DrawBatchService.requireCandidates(-1)).getMessage());
    }
}
