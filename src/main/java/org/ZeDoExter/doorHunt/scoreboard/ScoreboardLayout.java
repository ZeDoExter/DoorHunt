package org.ZeDoExter.doorHunt.scoreboard;

import java.util.List;

public class ScoreboardLayout {
    private final String title;
    private final List<String> lines;

    public ScoreboardLayout(String title, List<String> lines) {
        this.title = title;
        this.lines = lines;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getLines() {
        return lines;
    }
}
