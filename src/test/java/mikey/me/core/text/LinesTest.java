package mikey.me.core.text;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinesTest {

    @Test
    void editsByOneBasedIndexWithoutTouchingTheOriginal() {
        List<String> original = List.of("a", "b");

        assertEquals(List.of("a", "b", "c"), Lines.add(original, "c"));
        assertEquals(List.of("a", "z"), Lines.set(original, 2, "z").orElseThrow());
        assertEquals(List.of("b"), Lines.remove(original, 1).orElseThrow());
        assertEquals(List.of("a", "b"), original);
    }

    @Test
    void blankLineIsFineAndBadIndexesAreEmpty() {
        assertEquals(List.of(""), Lines.add(List.of(), null));
        assertEquals(List.of(), Lines.remove(List.of("only"), 1).orElseThrow());
        assertEquals(Optional.empty(), Lines.set(List.of("a"), 0, "z"));
        assertEquals(Optional.empty(), Lines.set(List.of("a"), 2, "z"));
        assertEquals(Optional.empty(), Lines.remove(List.of("a"), 2));
        assertEquals(Optional.empty(), Lines.remove(null, 1));
    }
}
