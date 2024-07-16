package buckelieg.jdbc;

import buckelieg.jdbc.fn.*;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import static org.junit.jupiter.api.Assertions.*;

public class FNTests {

  @Test
  public void testTryFunction() throws Exception {
	TryFunction<Integer, Integer, Exception> fn = TryFunction.of(TryFunction.identity());
	assertEquals(1, fn.apply(1));
	assertEquals(2, fn.andThen(i -> i * 2).apply(1));
	assertEquals(0, fn.<Integer>compose(i -> i - 1).apply(1));
	assertThrows(NullPointerException.class, () -> fn.andThen(null).apply(1));
	assertThrows(NullPointerException.class, () -> fn.compose(null).apply(1));
  }

  @Test
  public void testTryBiFunction() throws Exception {
	TryBiFunction<Integer, Integer, Integer, Exception> fn = TryBiFunction.of(Integer::sum);
	assertEquals(3, fn.apply(1, 2));
	assertEquals(6, fn.andThen(sum -> sum * 2).apply(1, 2));
	assertThrows(NullPointerException.class, () -> fn.andThen(null).apply(1, 2));
  }

  @Test
  public void testTryTriFunction() throws Exception {
	TryTriFunction<Integer, Integer, Integer, Integer, Exception> fn = TryTriFunction.of((i1, i2, i3) -> i1 + i2 + i3);
	assertEquals(6, fn.apply(1, 2, 3));
	assertEquals(18, fn.andThen(sum -> sum * 3).apply(1, 2, 3));
	assertThrows(NullPointerException.class, () -> fn.andThen(null).apply(1, 2, 3));
  }

  @Test
  public void testTryQuadFunction() throws Exception {
	TryQuadFunction<Integer, Integer, Integer, Integer, Integer, Exception> fn = TryQuadFunction.of((i1, i2, i3, i4) -> i1 + i2 + i3 + i4);
	assertEquals(10, fn.apply(1, 2, 3, 4));
	assertEquals(40, fn.andThen(sum -> sum * 4).apply(1, 2, 3, 4));
	assertThrows(NullPointerException.class, () -> fn.andThen(null).apply(1, 2, 3, 4));
  }

  @Test
  public void testTryRunnable() throws Exception {
	TryRunnable<Exception> fn = TryRunnable.of(() -> assertInstanceOf(TryRunnable.class, this));
	assertThrows(AssertionFailedError.class, fn::run);
	assertEquals(TryRunnable.NOOP, TryRunnable.NOOP());
  }

  @Test
  public void testTrySupplier() throws Exception {
	TrySupplier<Integer, Exception> fn = TrySupplier.of(() -> 5);
	assertEquals(5, fn.get());
  }

  @Test
  public void testTryConsumer() throws Exception {
	TryConsumer<Integer, Exception> fn = TryConsumer.<Integer, Exception>of(TryConsumer.NOOP())
			.compose(integer -> assertEquals(integer, 1))
			.andThen(integer -> assertEquals(integer, 1));
	fn.accept(1);
	assertThrows(NullPointerException.class, () -> fn.andThen(null).accept(1));
	assertThrows(NullPointerException.class, () -> fn.compose(null).accept(1));
	assertEquals(TryConsumer.NOOP, TryConsumer.NOOP());
  }

  @Test
  public void testTryBiConsumer() throws Exception {
	TryBiConsumer<Integer, Integer, Exception> fn = TryBiConsumer.of(TryBiConsumer.NOOP());
	fn.andThen((i1, i2) -> {
	  assertEquals(i1, 1);
	  assertEquals(i2, 2);
	}).accept(1, 2);
	assertThrows(NullPointerException.class, () -> fn.andThen(null).accept(1, 2));
	assertEquals(TryBiConsumer.NOOP, TryBiConsumer.NOOP());
  }

  @Test
  public void testTryTriConsumer() throws Exception {
	TryTriConsumer<Integer, Integer, Integer, Exception> fn = TryTriConsumer.of((i1, i2, i3) -> {
	  assertEquals(i1, 1);
	  assertEquals(i2, 2);
	  assertEquals(i3, 3);
	});
	fn.accept(1, 2, 3);
	assertEquals(TryTriConsumer.NOOP, TryTriConsumer.NOOP());
  }

  @Test
  public void testTryQuadConsumer() throws Exception {
	TryQuadConsumer<Integer, Integer, Integer, Integer, Exception> fn = TryQuadConsumer.of((i1, i2, i3, i4) -> {
	  assertEquals(i1, 1);
	  assertEquals(i2, 2);
	  assertEquals(i3, 3);
	  assertEquals(i4, 4);
	});
	fn.accept(1, 2, 3, 4);
	assertEquals(TryQuadConsumer.NOOP, TryQuadConsumer.NOOP());
  }

  @Test
  public void testTryPredicate() throws Exception {
	TryPredicate<Integer, Exception> fn = TryPredicate.of(TryPredicate.FALSE());
	assertTrue(fn.negate().test(1));
	assertFalse(fn.test(1));
	assertEquals(TryPredicate.TRUE, TryPredicate.TRUE());
	assertEquals(TryPredicate.FALSE, TryPredicate.FALSE());
	assertTrue(TryPredicate.<Integer, Exception>TRUE().or(TryPredicate.TRUE()).test(1));
	assertFalse(TryPredicate.<Integer, Exception>FALSE().or(TryPredicate.FALSE()).test(1));
	assertFalse(TryPredicate.<Integer, Exception>TRUE().and(TryPredicate.FALSE()).test(1));
	assertFalse(fn.and(TryPredicate.TRUE()).test(1));
	assertTrue(TryPredicate.<Integer, Exception>not(TryPredicate.FALSE()).test(1));
	assertFalse(TryPredicate.<Integer, Exception>not(TryPredicate.TRUE()).test(1));
	assertTrue(TryPredicate.isEqual(fn).test(fn));
	assertFalse(TryPredicate.isEqual(fn).test(TryPredicate.TRUE()));
	assertFalse(TryPredicate.isEqual(null).test(fn));
	assertThrows(NullPointerException.class, () -> TryPredicate.not(null).test(1));
	assertThrows(NullPointerException.class, () -> TryPredicate.of(null).test(1));
	assertThrows(NullPointerException.class, () -> fn.or(null).test(1));
	assertThrows(NullPointerException.class, () -> fn.and(null).test(1));
  }

  @Test
  public void testTryBiPredicate() throws Exception {
	TryBiPredicate<Integer, Integer, Exception> fn = TryBiPredicate.of(TryBiPredicate.FALSE());
	assertTrue(fn.negate().test(1, 2));
	assertFalse(fn.test(1, 2));
	assertEquals(TryPredicate.TRUE, TryPredicate.TRUE());
	assertEquals(TryPredicate.FALSE, TryPredicate.FALSE());
	assertTrue(TryBiPredicate.<Integer, Integer, Exception>TRUE().or(TryBiPredicate.TRUE()).test(1, 2));
	assertFalse(TryBiPredicate.<Integer, Integer, Exception>FALSE().or(TryBiPredicate.FALSE()).test(1, 2));
	assertFalse(TryBiPredicate.<Integer, Integer, Exception>TRUE().and(TryBiPredicate.FALSE()).test(1, 2));
	assertFalse(fn.and(TryBiPredicate.TRUE()).test(1, 2));
	assertThrows(NullPointerException.class, () -> TryPredicate.not(null).test(1));
	assertThrows(NullPointerException.class, () -> TryPredicate.of(null).test(1));
	assertThrows(NullPointerException.class, () -> fn.or(null).test(1, 1));
	assertThrows(NullPointerException.class, () -> fn.and(null).test(1, 2));
  }

}
