package buckelieg.jdbc.fn;


import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Represents an operation that accepts two input arguments and returns no
 * result.  This is the two-arity specialization of {@link TryConsumer}.
 * Unlike most other functional interfaces, {@code TryBiConsumer} is expected
 * to operate via side-effects.
 *
 * <p>This is a <a href="package-summary.html">functional interface</a>
 * whose functional method is {@link #accept(Object, Object)}.
 *
 * @param <T> the type of the first argument to the operation
 * @param <U> the type of the second argument to the operation
 * @param <E> the type of the exception thrown
 * @see TryConsumer
 */
@FunctionalInterface
public interface TryBiConsumer<T, U, E extends Throwable> {

    /**
     * Performs this operation on the given arguments.
     *
     * @param t the first input argument
     * @param u the second input argument
     * @throws E an exception
     */
    void accept(T t, U u) throws E;

    /**
     * Returns reference of lambda expression
     * <br/>Typical usage is:
     * <br/>{@code TryBiConsumer.of((x, y) -> {}).andThen((x, y) -> {});}
     *
     * @param tryBiConsumer a function
     * @return lambda as {@link TryBiConsumer} reference
     * @throws NullPointerException if tryBiConsumer is null
     */
    static <I, O, E extends Throwable> TryBiConsumer<I, O, E> of(TryBiConsumer<I, O, E> tryBiConsumer) {
        return requireNonNull(tryBiConsumer);
    }

    /**
     * Returns a composed {@code BiConsumer} that performs, in sequence, this
     * operation followed by the {@code after} operation. If performing either
     * operation throws an exception, it is relayed to the caller of the
     * composed operation.  If performing this operation throws an exception,
     * the {@code after} operation will not be performed.
     *
     * @param after the operation to perform after this operation
     * @return a composed {@code BiConsumer} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws E                    an exception
     * @throws NullPointerException if {@code after} is null
     */
    default TryBiConsumer<T, U, E> andThen(TryBiConsumer<? super T, ? super U, ? extends E> after) throws E {
        Objects.requireNonNull(after);
        return (l, r) -> {
            accept(l, r);
            after.accept(l, r);
        };
    }
}
