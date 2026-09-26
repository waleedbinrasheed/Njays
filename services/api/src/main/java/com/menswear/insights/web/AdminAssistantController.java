package com.menswear.insights.web;

import com.menswear.insights.AdminAssistantService;
import com.menswear.insights.AdminInsightsService;
import com.menswear.insights.dto.InsightsDtos;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/assistant")
public class AdminAssistantController {

    private final AdminInsightsService insightsService;
    private final AdminAssistantService assistantService;

    public AdminAssistantController(AdminInsightsService insightsService, AdminAssistantService assistantService) {
        this.insightsService = insightsService;
        this.assistantService = assistantService;
    }

    @GetMapping("/briefing")
    public InsightsDtos.Snapshot briefing() {
        return insightsService.snapshot();
    }

    @PostMapping("/ask")
    public InsightsDtos.AskResponse ask(@RequestBody InsightsDtos.AskRequest request) {
        return assistantService.ask(request == null ? null : request.question());
    }
}
