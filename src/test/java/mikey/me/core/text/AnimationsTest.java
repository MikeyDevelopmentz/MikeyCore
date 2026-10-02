package mikey.me.core.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationsTest {

    @Test
    void spotsAnimatedText() {
        assertTrue(Animations.animated("&uhi"));
        assertTrue(Animations.animated("<RAINBOW1>hi</RAINBOW>"));
        assertTrue(Animations.animated("<#ANIM:wave:&f,&b>hi</#ANIM>"));
        assertFalse(Animations.animated("&a still"));
        assertFalse(Animations.animated(null));
    }

    @Test
    void rainbowMovesAndTypewriterGrows() {
        assertNotEquals(Animations.frame("&uab", 0), Animations.frame("&uab", 8));
        assertTrue(Animations.frame("&uab", 0).contains("<#"));
        assertTrue(Animations.frame("<#ANIM:typewriter>hello</#ANIM>", 1).length()
                < Animations.frame("<#ANIM:typewriter>hello</#ANIM>", 4).length());
        assertEquals("plain", Animations.frame("plain", 3));
    }

    @Test
    void waveUsesBothColors() {
        String frame = Animations.frame("<#ANIM:wave:&f,&c>abcd</#ANIM>", 0);
        assertTrue(frame.contains("<#FFFFFF>"));
        assertTrue(frame.contains("<#FF5555>"));
    }

    @Test
    void waveKeepsSurrogatePairsTogether() {
        String emoji = "\uD83D\uDE00";
        String frame = Animations.frame("<#ANIM:wave:&f,&c>" + emoji + "a</#ANIM>", 0);
        assertTrue(frame.contains(emoji), frame);
    }

    @Test
    void otherAnimsKeepSurrogatePairsTogether() {
        String emoji = "\uD83D\uDE00";
        String typed = Animations.frame("<#ANIM:typewriter>" + emoji + "a</#ANIM>", 1);
        String rainbow = Animations.frame("&u" + emoji, 0);
        assertTrue(typed.contains(emoji), typed);
        assertTrue(rainbow.contains(emoji), rainbow);
    }
}
