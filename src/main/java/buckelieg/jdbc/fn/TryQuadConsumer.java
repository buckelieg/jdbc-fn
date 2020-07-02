package buckelieg.jdbc.fn;

/**
 *
 * @param <I1> first argument parameter type
 * @param <I2> second argument parameter type
 * @param <I3> third argument parameter type
 * @param <I4> fourth argument parameter type
 * @param <E> exception type
 */
@FunctionalInterface
public interface TryQuadConsumer<I1, I2, I3, I4, E extends Throwable> {

    /**
     *
     * @param i1
     * @param i2
     * @param i3
     * @param i4
     * @throws E an arbitrary exception
     */
    void accept(I1 i1, I2 i2, I3 i3, I4 i4) throws E;

}
