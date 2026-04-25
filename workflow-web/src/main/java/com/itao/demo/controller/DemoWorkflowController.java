package com.itao.demo.controller;

import com.itao.demo.service.DemoWorkflowService;
import com.itao.demo.dto.DemoWorkflowRequest;
import com.itao.demo.dto.DemoWorkflowResumeRequest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/studio-metadata")
    public Map<String, Object> getStudioMetadata() {
        return demoWorkflowService.getStudioMetadata();
    }

    @PostMapping("/run")
    public Map<String, Object> runConfiguredWorkflow(@RequestBody DemoWorkflowRequest request) {
        return demoWorkflowService.runConfiguredWorkflow(request);
    }

    @PostMapping("/chat")
    public Map<String, Object> chatConfiguredWorkflow(@RequestBody DemoWorkflowRequest request) {
        return demoWorkflowService.chatConfiguredWorkflow(request);
    }

    @PostMapping("/resume")
    public Map<String, Object> resumeConfiguredWorkflow(@RequestBody DemoWorkflowResumeRequest request) {
        return demoWorkflowService.resumeConfiguredWorkflow(request);
    }

    @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChatConfiguredWorkflow(@RequestBody DemoWorkflowRequest request) {
        return demoWorkflowService.streamChatConfiguredWorkflow(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", "FAILED",
                "reason", ex.getMessage()
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", "FAILED",
                "reason", ex.getMessage()
        ));
    }
}
