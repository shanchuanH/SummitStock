package com.example.portfolio.brief;

import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/brief")
public class ExecutiveBriefController {
    private final ExecutiveBriefQueryService briefs;

    public ExecutiveBriefController(ExecutiveBriefQueryService briefs) {
        this.briefs = briefs;
    }

    @GetMapping("/today")
    ExecutiveBriefQueryService.ExecutiveBrief today(Principal principal) {
        return briefs.today(principal.getName());
    }
}
