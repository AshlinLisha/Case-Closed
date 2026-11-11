package com.caseclosed.model;

import java.util.LinkedHashSet;
import java.util.Set;

/** Everything the player has done so far. This is what gets saved to saves/progress.json. */
public class GameProgress {
    private Set<String> discoveredClues = new LinkedHashSet<>();
    private Set<String> askedQuestions = new LinkedHashSet<>();
    private boolean finished;
    private int score;

    public Set<String> getDiscoveredClues() { return discoveredClues; }
    public void setDiscoveredClues(Set<String> discoveredClues) { this.discoveredClues = discoveredClues; }
    public Set<String> getAskedQuestions() { return askedQuestions; }
    public void setAskedQuestions(Set<String> askedQuestions) { this.askedQuestions = askedQuestions; }
    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
}
