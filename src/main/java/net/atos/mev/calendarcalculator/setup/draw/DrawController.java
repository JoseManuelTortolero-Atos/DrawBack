package net.atos.mev.calendarcalculator.setup.draw;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;

import jakarta.servlet.http.HttpServletResponse;
import net.atos.mev.calendarcalculator.setup.SetupDtos;

/** Camino 1: sorteo de emparejamientos por lotes de candidatos. */
@RestController
@RequestMapping("/api/setup")
public class DrawController {

	private final DrawBatchService draws;

	public DrawController(DrawBatchService draws) {
		this.draws = draws;
	}

	@PostMapping("/competitions/{cid}/seasons/{season}/draw/validate")
	public List<SetupDtos.ValidationIssue> validate(@PathVariable String cid, @PathVariable String season,
			@RequestParam(defaultValue = "true") boolean includeDependents) {
		return draws.validate(cid, season, includeDependents);
	}

	@GetMapping("/competitions/{cid}/seasons/{season}/draw-batches")
	public List<DrawDtos.DrawBatch> list(@PathVariable String cid, @PathVariable String season) {
		return draws.list(cid, season);
	}

	@PostMapping("/competitions/{cid}/seasons/{season}/draw-batches")
	public ResponseEntity<DrawDtos.DrawBatch> start(@PathVariable String cid, @PathVariable String season,
			@RequestBody(required = false) DrawDtos.StartDrawRequest body) {
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(draws.start(cid, season, body));
	}

	@GetMapping("/draw-batches/{id}")
	public DrawDtos.DrawBatch get(@PathVariable String id) {
		return draws.get(id);
	}

	@PostMapping("/draw-batches/{id}/cancel")
	public DrawDtos.DrawBatch cancel(@PathVariable String id) {
		return draws.cancel(id);
	}

	@DeleteMapping("/draw-batches/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		draws.delete(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/draw-batches/{id}/candidates")
	public DrawDtos.CandidatePage candidates(@PathVariable String id, @RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "50") int limit) {
		return draws.candidates(id, offset, limit);
	}

	@GetMapping("/draw-batches/{id}/candidates/{no}")
	public DrawDtos.Candidate candidate(@PathVariable String id, @PathVariable int no) {
		return draws.candidate(id, no);
	}

	/** Aplica el candidato elegido por el cliente como emparejamientos de la temporada (inicia la competición). */
	@PostMapping("/draw-batches/{id}/candidates/{no}/choose")
	public DrawDtos.DrawBatch choose(@PathVariable String id, @PathVariable int no) {
		return draws.choose(id, no);
	}

	@DeleteMapping("/draw-batches/{id}/discarded")
	public Map<String, Integer> deleteDiscarded(@PathVariable String id) {
		return Map.of("deletedRows", draws.deleteDiscarded(id));
	}

	@GetMapping("/draw-batches/{id}/zip")
	public WebAsyncTask<Void> zip(@PathVariable String id, HttpServletResponse response) {
		String name = draws.zipName(id);
		response.setContentType("application/zip");
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString());
		// Miles de plantillas con imagenes superan el timeout MVC habitual.
		return new WebAsyncTask<>(Duration.ofMinutes(30).toMillis(), () -> {
			draws.writeZip(id, response.getOutputStream());
			return null;
		});
	}
}
