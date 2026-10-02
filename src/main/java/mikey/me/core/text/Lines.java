package mikey.me.core.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Lines {

    private Lines() {
    }

    public static List<String> add(List<String> lines, String text) {
        List<String> next = copy(lines);
        next.add(text == null ? "" : text);
        return List.copyOf(next);
    }

    // line is 1-based. empty if its out of range
    public static Optional<List<String>> set(List<String> lines, int line, String text) {
        if (lines == null || line < 1 || line > lines.size()) {
            return Optional.empty();
        }
        List<String> next = copy(lines);
        next.set(line - 1, text == null ? "" : text);
        return Optional.of(List.copyOf(next));
    }

    public static Optional<List<String>> remove(List<String> lines, int line) {
        if (lines == null || line < 1 || line > lines.size()) {
            return Optional.empty();
        }
        List<String> next = copy(lines);
        next.remove(line - 1);
        return Optional.of(List.copyOf(next));
    }

    private static List<String> copy(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(lines);
    }
}
