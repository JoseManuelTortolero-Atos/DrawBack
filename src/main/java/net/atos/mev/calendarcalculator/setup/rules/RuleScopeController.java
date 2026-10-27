package net.atos.mev.calendarcalculator.setup.rules;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Asignación de restricciones por ámbito y reglas efectivas por liga-temporada. */
@RestController
@RequestMapping("/api/setup")
public class RuleScopeController {

	private final RuleScopeService service;

	public RuleScopeController(RuleScopeService service) {
		this.service = service;
	}

	@GetMapping("/federations/{fed}/rule-assignments")
	public List<RuleScopeDtos.Assignment> list(@PathVariable String fed, @RequestParam(required = false) String competitionId,
			@RequestParam(required = false) String season, @RequestParam(required = false) String scopeType) {
		return service.list(fed, competitionId, season, scopeType);
	}

	@PostMapping("/federations/{fed}/rule-assignments")
	public ResponseEntity<RuleScopeDtos.Assignment> create(@PathVariable String fed, @RequestBody RuleScopeDtos.AssignmentRequest body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.create(fed, body));
	}

	@PutMapping("/rule-assignments/{id}")
	public RuleScopeDtos.Assignment update(@PathVariable String id, @RequestBody RuleScopeDtos.AssignmentRequest body) {
		return service.update(id, body);
	}

	@DeleteMapping("/rule-assignments/{id}")
	public Map<String, Integer> delete(@PathVariable String id) {
		return Map.of("deletedRows", service.delete(id));
	}

	@GetMapping("/competitions/{cid}/seasons/{season}/effective-rules")
	public RuleScopeDtos.EffectiveRules effective(@PathVariable String cid, @PathVariable String season) {
		return service.effective(cid, season);
	}
}
