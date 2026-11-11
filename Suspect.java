package com.caseclosed.model;

import java.util.List;

public record Suspect(String id, String name, String role, String bio, String motive,
                      String alibi, List<Question> questions) {

    public record Question(String id, String text, String answer) {
    }
}
