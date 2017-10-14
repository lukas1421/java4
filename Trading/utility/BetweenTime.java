package utility;

import java.util.function.Predicate;

@FunctionalInterface
public interface BetweenTime<LocalTime, Boolean> {

    Predicate<LocalTime> between(LocalTime u1, boolean b1, LocalTime u2, boolean b2);
}
