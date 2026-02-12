package monopoly.engine;

import java.util.List;

public class ActionResult {
    private final boolean ok;
    private final List<String> events;

    private ActionResult(boolean ok, List<String> events) {
        this.ok = ok;
        this.events = events;
    }

    public static ActionResult ok(String... events) {
        return new ActionResult(true, List.of(events));
    }

    public static ActionResult fail(String message) {
        return new ActionResult(false, List.of(message));
    }

    public boolean isOk() { return ok; }
    public List<String> getEvents() { return events; }
}
