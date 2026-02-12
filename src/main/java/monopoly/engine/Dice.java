package monopoly.engine;

import java.util.Random;

public class Dice {
    private final Random random;

    public Dice(Random random) { this.random = random; }
    public Dice() { this(new Random()); }

    public Roll roll2d6() {
        int d1 = random.nextInt(6) + 1;
        int d2 = random.nextInt(6) + 1;
        return new Roll(d1, d2);
    }

    public static final class Roll {
        private final int die1;
        private final int die2;

        public Roll(int die1, int die2) {
            this.die1 = die1;
            this.die2 = die2;
        }

        public int die1() { return die1; }
        public int die2() { return die2; }
        public int total() { return die1 + die2; }
        public boolean isDouble() { return die1 == die2; }

        @Override public String toString() {
            return die1 + "+" + die2 + "=" + total() + (isDouble() ? " (DOUBLE)" : "");
        }
    }
}


