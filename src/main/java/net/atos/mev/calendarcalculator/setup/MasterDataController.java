package net.atos.mev.calendarcalculator.setup;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class MasterDataController {
    public record MasterTeam(String code, String name, String city, int registrations) { }
    public record TeamRequest(String name, String city, String originalName, String originalCity) { }
    public record CreateSeason(LocalDate seasonStart, LocalDate seasonEnd, LocalDate firstRoundDate, LocalDate startSecondLeg) { }

    private final MasterDataService service;

    public MasterDataController(MasterDataService service) {
        this.service = service;
    }

    @GetMapping("/teams")
    public List<MasterTeam> teams() {
        return service.teams();
    }

    @GetMapping("/teams/{code}")
    public MasterTeam team(@PathVariable String code) {
        return service.team(code);
    }

    @PostMapping("/teams/{code}")
    @ResponseStatus(HttpStatus.CREATED)
    public MasterTeam createTeam(@PathVariable String code, @RequestBody TeamRequest body) {
        return service.createTeam(code, body);
    }

    @PutMapping("/teams/{code}")
    public MasterTeam updateTeam(@PathVariable String code, @RequestBody TeamRequest body) {
        return service.updateTeam(code, body);
    }

    @DeleteMapping("/teams/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTeam(@PathVariable String code) {
        service.deleteTeam(code);
    }

    @DeleteMapping("/federations/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFederation(@PathVariable String code) {
        service.deleteFederation(code);
    }

    @DeleteMapping("/competitions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCompetition(@PathVariable String id) {
        service.deleteCompetition(id);
    }

    @GetMapping("/competitions/{id}/seasons")
    public List<SetupDtos.CompetitionSeasonRef> seasons(@PathVariable String id) {
        return service.seasons(id);
    }

    @PostMapping("/setup/competitions/{cid}/seasons/{season}")
    @ResponseStatus(HttpStatus.CREATED)
    public SetupDtos.SeasonSetup createSeason(@PathVariable String cid, @PathVariable String season, @RequestBody CreateSeason body) {
        return service.createSeason(cid, season, body);
    }

    @DeleteMapping("/setup/competitions/{cid}/seasons/{season}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSeason(@PathVariable String cid, @PathVariable String season) {
        service.deleteSeason(cid, season);
    }
}
