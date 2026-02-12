package monopoly.setup;

import monopoly.engine.*;

import java.util.ArrayList;
import java.util.List;

import static monopoly.setup.BoardDestinations.*;

public final class CardFactory {

    public static List<Card> chanceCards() {
        List<Card> cards = new ArrayList<>();

        cards.add(new Card(CardType.CHANCE, "Go back 3 spaces", engine ->
                engine.moveCurrentPlayerRelative(-3, true), false));

        cards.add(new Card(CardType.CHANCE, "Assignment overdue - Go to Billy B. Do not pass LOAN DROP, DO NOT COLLECT £200",
                engine -> engine.goToJailNoGoSalary(), false));

        cards.add(new Card(CardType.CHANCE, "Extension earnt! Get out of Billy B Free",
                engine -> engine.awardGetOutOfJailFree(engine.getLastDrawnCard()), true));

        cards.add(new Card(CardType.CHANCE, "Advance to the nearest Night Club (Station). If unowned, you may buy it. If owned, pay double rent.",
                engine -> engine.advanceToNearestStationDoubleRent(), false));

        cards.add(new Card(CardType.CHANCE, "Damp! For each house pay £25, for each hotel pay £100",
                engine -> engine.payPerBuilding(25, 100), false));

        cards.add(new Card(CardType.CHANCE, "Penance - Advance to Durham Cathedral. If unowned you may buy it.",
                engine -> engine.advanceToAbsolute(DURHAM_CATHEDRAL, true), false));

        cards.add(new Card(CardType.CHANCE, "Travel to Klute. If you pass LOAN DROP collect £200",
                engine -> engine.advanceToAbsolute(5, true), false));

        cards.add(new Card(CardType.CHANCE, "Advance to University College. If you pass LOAN DROP you may collect £200",
                engine -> engine.advanceToAbsolute(UNIVERSITY_COLLEGE, true), false));

        cards.add(new Card(CardType.CHANCE, "Convert to the Darkside - Advance and spend the night in Hatfield",
                engine -> engine.advanceToAbsolute(HATFIELD, true), false));

        cards.add(new Card(CardType.CHANCE, "Your Friend competes at Fight Night - Donate £15",
                engine -> engine.payBank(15), false));

        cards.add(new Card(CardType.CHANCE, "Advance to LOAN DROP",
                engine -> engine.advanceToAbsolute(GO, true), false));

        cards.add(new Card(CardType.CHANCE, "You land an internship - Receive £200",
                engine -> engine.receiveBank(200), false));

        cards.add(new Card(CardType.CHANCE, "Urinating on the rugby pitch - Pay each player £50 in damages / Lose at DU poker",
                engine -> engine.payEachOtherPlayer(50), false));

        cards.add(new Card(CardType.CHANCE, "Advance to Utilities. If unowned, you may buy it. If owned, pay 10x dice.",
                engine -> engine.advanceToNearestUtilitySpecialRent(), false));

        cards.add(new Card(CardType.CHANCE, "Your roof falls through and your landlord agrees to compensate you… Collect £150",
                engine -> engine.receiveBank(150), false));

        // That’s 15 so far; add one filler if you need exactly 16 chance cards.
        cards.add(new Card(CardType.CHANCE, "Bonus: Receive £20", engine -> engine.receiveBank(20), false));

        return cards;
    }

    public static List<Card> communityChestCards() {
        List<Card> cards = new ArrayList<>();

        cards.add(new Card(CardType.COMMUNITY_CHEST, "Receive a £25 research grant", engine -> engine.receiveBank(25), false));
        cards.add(new Card(CardType.CHANCE, "Extension earnt! Get out of Billy B Free",
                engine -> engine.awardGetOutOfJailFree(engine.getLastDrawnCard()), true));
        cards.add(new Card(CardType.COMMUNITY_CHEST, "Win a DU Poker Evening - Collect £10 from every player", engine -> engine.collectFromEachOtherPlayer(10), false));
        cards.add(new Card(CardType.COMMUNITY_CHEST, "Tickets June Ball - Pay £120", engine -> engine.payBank(120), false));
        cards.add(new Card(CardType.COMMUNITY_CHEST, "Assignment overdue - Go to Billy B. Do not pass LOAN DROP, DO NOT COLLECT £200", engine -> engine.goToJailNoGoSalary(), false));
        cards.add(new Card(CardType.COMMUNITY_CHEST, "You book DUCFS tickets. Pay £50", engine -> engine.payBank(50), false));
        cards.add(new Card(CardType.COMMUNITY_CHEST, "Advance to your LOAN DROP", engine -> engine.advanceToAbsolute(GO, true), false));

        cards.add(new Card(CardType.COMMUNITY_CHEST,
                "Your pipes burst. Pay for repairs: £40 per house, £115 per hotel",
                engine -> engine.payPerBuilding(40, 115), false));

        cards.add(new Card(CardType.COMMUNITY_CHEST, "You make DU - double portions! Receive £100", engine -> engine.receiveBank(100), false));
        cards.add(new Card(CardType.COMMUNITY_CHEST, "Clubcard points - Collect £20", engine -> engine.receiveBank(20), false));
        cards.add(new Card(CardType.COMMUNITY_CHEST, "Your Thesis is published! Collect £10", engine -> engine.receiveBank(10), false));
        cards.add(new Card(CardType.COMMUNITY_CHEST, "Best flirt in college - Collect £50", engine -> engine.receiveBank(50), false));
        cards.add(new Card(CardType.COMMUNITY_CHEST, "Holy Grail wants your smooth tunes - Receive £200 from bank", engine -> engine.receiveBank(200), false));
        cards.add(new Card(CardType.COMMUNITY_CHEST, "Get caught on Castle roof - Pay £50", engine -> engine.payBank(50), false));

        cards.add(new Card(CardType.COMMUNITY_CHEST,
                "You embezzle alumni money - Roll 7+, win £100; else pay £150 and go straight to Billy B",
                engine -> engine.gambleThenMaybeJail(7, 100, 150), false));

        cards.add(new Card(CardType.COMMUNITY_CHEST, "You get a bunk mate and save on heating - Collect £50", engine -> engine.receiveBank(50), false));

        return cards;
    }

    private CardFactory() {}
}
