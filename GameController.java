package com.caseclosed.controller;

import com.caseclosed.service.GameService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Receives website requests and hands them to GameService. No game logic here. */
@RestController
@RequestMapping("/api/game")
public class GameController {

    public record AccuseRequest(String suspectId, List<String> clueIds) {
    }

    private final GameService service;

    public GameController(GameService service) {
        this.service = service;
    }

    @GetMapping("/case")
    public Map<String, Object> getCase() { return service.getCaseInfo(); }

    @GetMapping("/progress")
    public Map<String, Object> getProgress() { return service.getProgress(); }

    @GetMapping("/suspects")
    public List<Map<String, Object>> getSuspects() { return service.getSuspects(); }

    @PostMapping("/suspects/{suspectId}/questions/{questionId}")
    public Map<String, Object> ask(@PathVariable String suspectId, @PathVariable String questionId) {
        return service.askQuestion(suspectId, questionId);
    }

    @GetMapping("/clues")
    public List<Map<String, Object>> getClues() { return service.getClues(); }

    @PostMapping("/clues/{clueId}/inspect")
    public Map<String, Object> inspect(@PathVariable String clueId) { return service.inspectClue(clueId); }

    @GetMapping("/compare")
    public Map<String, Object> compare(@RequestParam String clueId, @RequestParam String suspectId) {
        return service.compare(clueId, suspectId);
    }

    @PostMapping("/accuse")
    public Map<String, Object> accuse(@RequestBody AccuseRequest request) {
        return service.accuse(request.suspectId(), request.clueIds());
    }

    @PostMapping("/reset")
    public void reset() { service.reset(); }
}
