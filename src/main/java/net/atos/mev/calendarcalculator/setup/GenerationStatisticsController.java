package net.atos.mev.calendarcalculator.setup;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/setup/competitions/{cid}/seasons/{season}/versions/{n}")
public class GenerationStatisticsController {
    private final GenerationStatisticsService statistics;

    public GenerationStatisticsController(GenerationStatisticsService statistics) {
        this.statistics = statistics;
    }

    @GetMapping("/statistics")
    public GenerationStatisticsService.Statistics statistics(@PathVariable String cid, @PathVariable String season, @PathVariable int n) {
        return statistics.statistics(cid, season, n);
    }

    @GetMapping("/statistics.csv")
    public ResponseEntity<byte[]> csv(@PathVariable String cid, @PathVariable String season, @PathVariable int n) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("statistics-v" + n + ".csv").build().toString())
                .body(statistics.csv(statistics.statistics(cid, season, n)));
    }
}
