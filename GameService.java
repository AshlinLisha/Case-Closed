package com.caseclosed.service;

import com.caseclosed.model.CaseFile;
import com.caseclosed.model.Clue;
import com.caseclosed.model.GameProgress;
import com.caseclosed.model.Suspect;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * All the game rules live here. The controller only forwards requests to this class.
 * Methods are synchronized so two requests can never corrupt the save file (single-player demo).
 */
@Service
public class GameService {

    private static final Path SAVE_FILE = Path.of("saves", "progress.json");

    private final ObjectMapper mapper;
    private CaseFile caseFile;
    private GameProgress progress = new GameProgress();

    public GameService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    void init() throws IOException {
        try (InputStream in = new ClassPathResource("data/cases.json").getInputStream()) {
            caseFile = mapper.readValue(in, CaseFile.class);
        }
        if (Files.exists(SAVE_FILE)) {
            try {
                progress = mapper.readValue(SAVE_FILE.toFile(), GameProgress.class);
            } catch (IOException e) {
                System.out.println("Save file unreadable, starting fresh: " + e.getMessage());
                progress = new GameProgress();
            }
        }
    }

    private void save() {
        try {
            Files.createDirectories(SAVE_FILE.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(SAVE_FILE.toFile(), progress);
        } catch (IOException e) {
            System.out.println("Could not save progress: " + e.getMessage());
        }
    }

    // ---------- case + progress ----------

    public synchronized Map<String, Object> getCaseInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", caseFile.title());
        m.put("tagline", caseFile.tagline());
        m.put("briefing", caseFile.briefing());
        m.put("progress", getProgress());
        return m;
    }

    public synchronized Map<String, Object> getProgress() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cluesFound", progress.getDiscoveredClues().size());
        m.put("totalClues", caseFile.clues().size());
        m.put("questionsAsked", progress.getAskedQuestions().size());
        m.put("finished", progress.isFinished());
        m.put("score", progress.getScore());
        return m;
    }

    public synchronized void reset() {
        progress = new GameProgress();
        save();
    }

    // ---------- suspects ----------

    public synchronized List<Map<String, Object>> getSuspects() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Suspect s : caseFile.suspects()) {
            List<Map<String, Object>> questions = new ArrayList<>();
            for (Suspect.Question q : s.questions()) {
                boolean asked = progress.getAskedQuestions().contains(q.id());
                Map<String, Object> qm = new LinkedHashMap<>();
                qm.put("id", q.id());
                qm.put("text", q.text());
                qm.put("asked", asked);
                qm.put("answer", asked ? q.answer() : null); // answers stay hidden until asked
                questions.add(qm);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id());
            m.put("name", s.name());
            m.put("role", s.role());
            m.put("bio", s.bio());
            m.put("motive", s.motive());
            m.put("alibi", s.alibi());
            m.put("questions", questions);
            out.add(m);
        }
        return out;
    }

    public synchronized Map<String, Object> askQuestion(String suspectId, String questionId) {
        Suspect suspect = findSuspect(suspectId);
        Suspect.Question question = suspect.questions().stream()
                .filter(q -> q.id().equals(questionId))
                .findFirst()
                .orElseThrow(() -> notFound("Unknown question: " + questionId));

        List<String> newLeads = new ArrayList<>();
        if (progress.getAskedQuestions().add(questionId)) {
            for (Clue c : caseFile.clues()) {
                if (questionId.equals(c.unlockedByQuestion())) {
                    newLeads.add(c.title());
                }
            }
            save();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("answer", question.answer());
        m.put("newLeads", newLeads);
        return m;
    }

    // ---------- clues ----------

    public synchronized List<Map<String, Object>> getClues() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Clue c : caseFile.clues()) {
            out.add(clueView(c));
        }
        return out;
    }

    public synchronized Map<String, Object> inspectClue(String clueId) {
        Clue clue = findClue(clueId);
        String lock = lockReason(clue);
        if (lock != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, lock);
        }
        if (progress.getDiscoveredClues().add(clueId)) {
            save();
        }
        return clueView(clue);
    }

    /** Compares a discovered clue with a suspect's statements. */
    public synchronized Map<String, Object> compare(String clueId, String suspectId) {
        Clue clue = findClue(clueId);
        Suspect suspect = findSuspect(suspectId);
        if (!progress.getDiscoveredClues().contains(clueId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inspect this clue before comparing it.");
        }
        String result;
        String message;
        if (suspect.id().equals(clue.contradictsSuspect())) {
            result = "CONTRADICTS";
            message = clue.contradictionText();
        } else if (suspect.id().equals(clue.clearsSuspect())) {
            result = "SUPPORTS_ALIBI";
            message = clue.clearsText();
        } else {
            result = "NO_LINK";
            message = "This evidence doesn't connect to " + suspect.name() + "'s statements.";
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("result", result);
        m.put("message", message);
        return m;
    }

    // ---------- final verdict ----------

    public synchronized Map<String, Object> accuse(String suspectId, List<String> clueIds) {
        Suspect accused = findSuspect(suspectId);
        int keyHits = 0;
        int wrongClues = 0;
        for (String id : new LinkedHashSet<>(clueIds == null ? List.<String>of() : clueIds)) {
            Clue c = findClue(id);
            if (!progress.getDiscoveredClues().contains(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "You can't cite evidence you haven't found: " + c.title());
            }
            if (c.keyEvidence()) keyHits++; else wrongClues++;
        }
        long totalKey = caseFile.clues().stream().filter(Clue::keyEvidence).count();
        boolean correct = accused.id().equals(caseFile.culpritId());

        int score = 0;
        String rank;
        String explanation;
        if (correct) {
            score = Math.max(0, 50 + keyHits * 10 - wrongClues * 5);
            rank = score >= 90 ? "Master Detective" : score >= 70 ? "Sharp Investigator" : "Lucky Break";
            explanation = caseFile.solution() + " You cited " + keyHits + " of " + totalKey
                    + " key clues" + (wrongClues > 0 ? " and " + wrongClues + " that didn't matter." : ".");
        } else {
            rank = "Case Unsolved";
            String why = caseFile.clues().stream()
                    .filter(c -> accused.id().equals(c.clearsSuspect()))
                    .map(Clue::clearsText).findFirst()
                    .orElse("The evidence doesn't back this accusation up.");
            explanation = why + " The real thief is still out there. Re-examine the evidence and try again.";
        }
        progress.setFinished(correct);
        progress.setScore(score);
        save();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("correct", correct);
        m.put("rank", rank);
        m.put("score", score);
        m.put("explanation", explanation);
        return m;
    }

    // ---------- helpers ----------

    private Map<String, Object> clueView(Clue c) {
        boolean found = progress.getDiscoveredClues().contains(c.id());
        String lock = found ? null : lockReason(c);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id());
        m.put("status", found ? "DISCOVERED" : lock == null ? "AVAILABLE" : "LOCKED");
        m.put("title", lock == null ? c.title() : "Unknown clue");
        m.put("location", lock == null ? c.location() : "Location unknown");
        m.put("description", found ? c.description() : null);
        m.put("lockedReason", lock);
        return m;
    }

    private String lockReason(Clue c) {
        if (c.requiresClue() != null && !progress.getDiscoveredClues().contains(c.requiresClue())) {
            return "Inspect other evidence first.";
        }
        if (c.unlockedByQuestion() != null && !progress.getAskedQuestions().contains(c.unlockedByQuestion())) {
            return "Someone in the Suspect Room knows about this. Keep interrogating.";
        }
        return null;
    }

    private Suspect findSuspect(String id) {
        return caseFile.suspects().stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> notFound("Unknown suspect: " + id));
    }

    private Clue findClue(String id) {
        return caseFile.clues().stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> notFound("Unknown clue: " + id));
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
