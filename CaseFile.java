package com.caseclosed.model;

import java.util.List;

/** The whole mystery, loaded from data/cases.json. (A record is a compact class with getters built in.) */
public record CaseFile(String title, String tagline, String briefing, String culpritId,
                       String solution, List<Suspect> suspects, List<Clue> clues) {
}
