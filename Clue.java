package com.caseclosed.model;

/**
 * A piece of evidence.
 * requiresClue / unlockedByQuestion decide when it can be inspected.
 * keyEvidence = true means it really points to the culprit.
 */
public record Clue(String id, String title, String location, String description,
                   String requiresClue, String unlockedByQuestion, boolean keyEvidence,
                   String contradictsSuspect, String contradictionText,
                   String clearsSuspect, String clearsText) {
}
