package pro.uno;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import pro.uno.cards.Card;
import pro.uno.cards.CardActionContext;
import pro.uno.cards.CardPlusFour;
import pro.uno.cards.CardRegistry;

public class LogicUnitTest {

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
