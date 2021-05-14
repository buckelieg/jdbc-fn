package buckelieg.jdbc.fn;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public interface TryQuadFunction<I1, I2, I3, I4, O, E extends Throwable> {


    /**
     * Represents some three-argument function which might throw an Exception
     *
     * @param input1 first argument
     * @param input2 second argument
     * @param input3 third argument
     * @param input4 fourth argument
     * @return output
     * @throws E an exception
     */
    O apply(I1 input1, I2 input2, I3 input3, I4 input4) throws E;

    /**
     * Returns a composed function that first applies this function to
     * its input, and then applies the {@code after} function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <R>   the type of output of the {@code after} function, and of the
     *              composed function
     * @param after the function to apply after this function is applied
     * @return a composed function that first applies this function and then
     * applies the {@code after} function
     * @throws E                    an exception
     * @throws NullPointerException if after is null
     */
    default <R> TryQuadFunction<I1, I2, I3, I4, R, E> andThen(TryFunction<? super O, ? extends R, E> after) throws E {
        Objects.requireNonNull(after);
        return (I1 input1, I2 input2, I3 input3, I4 input4) -> after.apply(apply(input1, input2, input3, input4));
    }

    /**
     * Returns reference of lambda expression
     *
     * @param tryQuadFunction a function
     * @return lambda as {@link TryTriFunction} reference
     * @throws NullPointerException if tryTriFunction is null
     */
    static <I1, I2, I3, I4, O, E extends Throwable> TryQuadFunction<I1, I2, I3, I4, O, E> of(TryQuadFunction<I1, I2, I3, I4, O, E> tryQuadFunction) {
        return requireNonNull(tryQuadFunction);
    }

}
