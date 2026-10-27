package net.atos.mev.calendarcalculator.setup;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class FederationController {

    private final SetupCatalogRepository catalog;

    public FederationController(SetupCatalogRepository catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/federations")
    public List<SetupDtos.Federation> federations(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return catalog.listFederations(includeInactive);
    }

    @GetMapping("/federations/{code}")
    public SetupDtos.Federation federation(@PathVariable String code) {
        return catalog.requireFederation(code);
    }

    @PostMapping("/federations/{code}")
    public SetupDtos.Federation create(@PathVariable String code, @RequestBody SetupDtos.FederationUpsertRequest body) {
        return catalog.createFederation(code, body);
    }

    @PutMapping("/federations/{code}")
    public SetupDtos.Federation update(@PathVariable String code, @RequestBody SetupDtos.FederationUpsertRequest body) {
        return catalog.updateFederation(code, body);
    }

    @PatchMapping("/federations/{code}/status")
    public SetupDtos.Federation status(@PathVariable String code, @RequestBody SetupDtos.FederationStatusRequest body) {
        if (body.status() == null || !List.of("ACTIVE", "INACTIVE").contains(body.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ACTIVE or INACTIVE");
        }
        return catalog.updateFederationStatus(code, body.status());
    }

    @GetMapping("/federations/{code}/competitions")
    public List<SetupDtos.CompetitionSummary> competitions(@PathVariable String code) {
        return catalog.competitions(code);
    }

    @GetMapping("/competitions/{id}")
    public SetupDtos.CompetitionSummary competition(@PathVariable String id) {
        return catalog.competition(id);
    }

    @GetMapping("/setup/federations/{code}/rule-types")
    public List<SetupDtos.RuleTypeDef> ruleTypes(@PathVariable String code) {
        return catalog.ruleTypes(code);
    }

    @PutMapping("/setup/federations/{code}/rule-types/{type}")
    public SetupDtos.RuleTypeDef ruleTypeStatus(@PathVariable String code, @PathVariable String type,
            @RequestBody SetupDtos.RuleTypeStatusRequest body) {
        return catalog.updateRuleTypeStatus(code, type, body.enabled());
    }
}