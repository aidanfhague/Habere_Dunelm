package monopoly.engine;

public class GameConfig {

    private final int startingCash;
    private final int salaryForPassingGo;
    private final int jailFine;
    private final int jailMaxTurns;

    // Full constructor (recommended: single source of truth)
    public GameConfig(int startingCash, int salaryForPassingGo, int jailFine, int jailMaxTurns) {
        this.startingCash = startingCash;
        this.salaryForPassingGo = salaryForPassingGo;
        this.jailFine = jailFine;
        this.jailMaxTurns = jailMaxTurns;
    }

    // Optional convenience constructor (uses default jail settings)
    public GameConfig(int startingCash, int salaryForPassingGo) {
        this(startingCash, salaryForPassingGo, 50, 3);
    }

    public int getStartingCash() { return startingCash; }
    public int getSalaryForPassingGo() { return salaryForPassingGo; }
    public int getJailFine() { return jailFine; }
    public int getJailMaxTurns() { return jailMaxTurns; }

    public static GameConfig ukDefaults() {
        return new GameConfig(1500, 200, 50, 3);
    }
}


