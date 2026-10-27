package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

class CompetitionAdminControllerTest {
    private final JdbcClient jdbc = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
    private final SetupCatalogRepository catalog = mock(SetupCatalogRepository.class);
    private final CompetitionAdminController controller = new CompetitionAdminController(jdbc, catalog);

    @Test
    void validatesSupportedMasterFields() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> controller.create("FRMF", new CompetitionAdminController.CreateCompetition("../bad", "D1", "Test"))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> controller.update("D1", new CompetitionAdminController.UpdateCompetition("", "Old"))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> controller.update("D1", new CompetitionAdminController.UpdateCompetition("Name", null))).getStatusCode());
        verifyNoInteractions(catalog);
    }

    @Test
    void updateUsesOriginalNameForConcurrentModificationCheck() {
        when(jdbc.sql("SELECT id FROM competition WHERE id = :id").param("id", "D1").query(String.class).optional())
                .thenReturn(Optional.of("D1"));
        when(jdbc.sql("UPDATE competition SET name = :name WHERE id = :id AND name = :original")
                .param("name", "Edited").param("id", "D1").param("original", "Original").update()).thenReturn(1);
        SetupDtos.CompetitionSummary result = new SetupDtos.CompetitionSummary("D1", "D1", "Edited", "MEN", 1, "FRMF", "ACTIVE", 1, 16, 3);
        when(catalog.competition("D1")).thenReturn(result);
        assertSame(result, controller.update("D1", new CompetitionAdminController.UpdateCompetition(" Edited ", "Original")));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> controller.update("D1", new CompetitionAdminController.UpdateCompetition("Other", "Stale"))).getStatusCode());
    }

    @Test
    void creationPersistsOnlyCompetitionWithoutCreatingSeasonOrDraw() {
        when(jdbc.sql("SELECT code FROM federation WHERE code = :code").param("code", "FRMF").query(String.class).optional())
                .thenReturn(Optional.of("FRMF"));
        SetupDtos.CompetitionSummary result = new SetupDtos.CompetitionSummary("CUP", "CUP", "Cup", "MEN", 1, "FRMF", "ACTIVE", 0, 0, 3);
        when(catalog.competition("CUP")).thenReturn(result);
        assertSame(result, controller.create("FRMF", new CompetitionAdminController.CreateCompetition("CUP", "CUP", "Cup")));
        verify(jdbc.sql("""
                INSERT INTO competition (id, code, name, storage_dir, output_suffix, sort_order, federation_code)
                VALUES (:id, :code, :name, :storage, '', 0, :federation)
                """).param("id", "CUP").param("code", "CUP").param("name", "Cup")
                .param("storage", "CUP").param("federation", "FRMF")).update();
    }
}
