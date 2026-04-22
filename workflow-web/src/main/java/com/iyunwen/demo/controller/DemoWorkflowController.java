package com.iyunwen.demo.controller;

import com.iyunwen.demo.service.DemoWorkflowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/demo/workflows")
public class DemoWorkflowController {

    private final DemoWorkflowService demoWorkflowService;

    public DemoWorkflowController(DemoWorkflowService demoWorkflowService) {
        this.demoWorkflowService = demoWorkflowService;
    }

    @GetMapping("/parallel-showcase")
    public Map<String, Object> getParallelShowcaseDefinition() {
        return demoWorkflowService.getParallelShowcaseDefinition();
    }

    @GetMapping("/parallel-showcase/run")
    public Map<String, Object> runParallelShowcase() {
        return demoWorkflowService.runParallelShowcase();
    }
}
