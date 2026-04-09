package pro.uno;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import pro.uno.cards.Card;
import pro.uno.cards.CardActionContext;
import pro.uno.cards.CardPlusFour;
import pro.uno.cards.CardRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LogicUnitTest {

    @Test
    public void aiLogic_choosesBestCard() {
        HostService host = new HostService();
        List<String> hand = Arrays.asList("card_red_1", "power_red_plus", "super_black_color");
        List<String> playable = Arrays.asList("card_red_1", "power_red_plus", "super_black_color");

        String best = host.pickBestAICard(playable, hand);
        assertEquals("card_red_1", best); // Normal cards have highest score (10) in my current simple logic

        List<String> playable2 = Arrays.asList("power_red_plus", "super_black_color");
        String best2 = host.pickBestAICard(playable2, hand);
        assertEquals("power_red_plus", best2); // Power cards (5) > Super cards (1)
    }

    @Test
    public void aiLogic_choosesBestColor() {
        HostService host = new HostService();
        List<String> hand = Arrays.asList("card_red_1", "card_blue_1", "card_blue_5", "card_green_2");

        String bestColor = host.pickBestAIColor(hand);
        assertEquals("blue", bestColor);
    }

    @Test
    public void cardFormatter_normalizesAndHumanizesCards() {
        assertEquals("super_black_plus", CardFormatter.normalize("super_red_plus"));
        assertEquals("", CardFormatter.normalize("power_black_block"));
        assertTrue(CardFormatter.isWild("super_black_plus"));
        assertFalse(CardFormatter.isWild("card_red_5"));
    }

    @Test
    public void cardCatalog_buildsRecognizedEffects() {
        assertEquals(CardEffect.REVERSE, CardCatalog.effectFor(UnoCard.from("power_red_reverse")));
        assertEquals(CardEffect.WILD_DRAW_FOUR, CardCatalog.effectFor(UnoCard.from("super_black_plus")));
        assertEquals(CardEffect.NONE, CardCatalog.effectFor(UnoCard.from("card_green_5")));
    }

    @Test
    public void cardCatalog_validatesCards() {
        assertTrue(CardCatalog.isValid(UnoCard.from("card_red_3")));
        assertTrue(CardCatalog.isValid(UnoCard.from("super_black_color")));
        assertFalse(CardCatalog.isValid(UnoCard.from("power_black_reverse")));
    }

    @Test
    public void cardRegistry_buildsTypedCardsAndPlayRules() {
        Card reverse = CardRegistry.create("power_red_reverse");
        Card top = CardRegistry.create("card_red_7");

        assertTrue(CardRegistry.isValid(reverse));
        assertTrue(reverse.canPlayOn(top, "red"));
        assertFalse(CardRegistry.isPlayable("card_blue_5", "card_red_7", "red"));
    }

    @Test
    public void cardHooks_updateWildColorAndDrawCount() {
        CardPlusFour plusFour = new CardPlusFour();
        CardActionContext context = new CardActionContext(plusFour, 1, 4, "green");

        plusFour.onCardPlayed(context);

        assertEquals(4, context.getNextPlayerDrawCount());
        assertTrue(context.shouldSkipNextPlayer());
        assertEquals("green", context.getTopCardColorOverride());
    }
}
