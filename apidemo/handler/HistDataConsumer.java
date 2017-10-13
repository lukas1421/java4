package handler;

@FunctionalInterface
public interface HistDataConsumer<U, V, T> {

    public void apply(U date, V open, V high, V low, V close, T vol);
}
