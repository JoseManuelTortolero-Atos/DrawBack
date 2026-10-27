package net.atos.mev.calendarcalculator.setup.draw;

import java.time.Instant;
import java.util.List;

/** Contrato REST del sorteo por lotes (camino 1: emparejamientos). */
public final class DrawDtos {

	private DrawDtos() {
	}

	/**
	 * @param candidates      número obligatorio de calendarios candidatos a generar (entero positivo)
	 * @param timeoutMinutes  tiempo máximo del lote (1..240, por defecto 30)
	 * @param includeDependents incluir dependientes y ligas enlazadas por pares P1 de la federacion, por defecto true
	 * @param forceRepetitions patrón de repeticiones de localía (avanzado; nRondas valores 0/1), opcional
	 */
	public record StartDrawRequest(Integer candidates, Integer timeoutMinutes, Boolean includeDependents, List<Integer> forceRepetitions) {
	}

	public record DrawLeague(String competitionId, String code, long competitionSeasonId) {
	}

	public record DrawBatch(String id, String competitionId, String season, String status, int requested, int generated, int tries,
			String message, Integer chosenNo, Instant createdAt, Instant finishedAt, Instant chosenAt, List<DrawLeague> leagues) {
	}

	public record DrawMatch(int round, String home, String away) {
	}

	public record CandidateLeague(String competitionId, String code, List<String> positionTeams, List<DrawMatch> matches) {
	}

	public record Candidate(int no, boolean chosen, List<CandidateLeague> leagues) {
	}

	/** Resumen para listar: jornada 1 de la liga principal. */
	public record CandidateSummary(int no, boolean chosen, String firstRound) {
	}

	public record CandidatePage(int total, int offset, int limit, List<CandidateSummary> items) {
	}
}
