package com.example.portfolio.reporting;

import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/backtests")
class BacktestReportController {
    private final BacktestReportStore reports;

    BacktestReportController(BacktestReportStore reports) {
        this.reports = reports;
    }

    @GetMapping("/latest")
    BacktestReportStore.RunView latest(Principal principal) {
        return reports.latest(principal.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/{id}")
    BacktestReportStore.RunView find(@PathVariable UUID id, Principal principal) {
        return reports.find(principal.getName(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
