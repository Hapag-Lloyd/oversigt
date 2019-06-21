package com.hlag.oversigt.util;

import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @deprecated Create specialized classes instead of using this class. The class
 *             {@link Tuple} is way too generic to be really useful.
 */
@Deprecated
public class Tuple<A, B> {
	private final A a;

	private final B b;

	public Tuple(final A a, final B b) {
		this.a = a;
		this.b = b;
	}

	public Tuple(final Entry<A, B> entry) {
		this(entry.getKey(), entry.getValue());
	}

	public A getFirst() {
		return a;
	}

	public B getSecond() {
		return b;
	}

	@Override
	public String toString() {
		return String.format("Tuple [a=%s, b=%s]", a, b);
	}

	public <T> T map(final Function<Tuple<A, B>, T> mapper) {
		return mapper.apply(this);
	}

	public <T> T map(final BiFunction<A, B, T> mapper) {
		return mapper.apply(getFirst(), getSecond());
	}

	public <C> Tuple<C, B> mapFirst(final Function<A, C> mapper) {
		return new Tuple<>(mapper.apply(getFirst()), getSecond());
	}

	public <C> Tuple<A, C> mapSecond(final Function<B, C> mapper) {
		return new Tuple<>(getFirst(), mapper.apply(getSecond()));
	}

	public Optional<Tuple<A, B>> filter(final Predicate<Tuple<A, B>> filter) {
		return filter.test(this) ? Optional.of(this) : Optional.empty();
	}

	public Tuple<Optional<A>, Optional<B>> filter(final Predicate<A> filterFirst, final Predicate<B> filterSecond) {
		return new Tuple<>(filterFirst.test(getFirst()) ? Optional.of(getFirst()) : Optional.empty(),
				filterSecond.test(getSecond()) ? Optional.of(getSecond()) : Optional.empty());
	}

	public Tuple<Optional<A>, B> filterFirst(final Predicate<A> filter) {
		return new Tuple<>(filter.test(getFirst()) ? Optional.of(getFirst()) : Optional.empty(), getSecond());
	}

	public Tuple<A, Optional<B>> filterSecond(final Predicate<B> filter) {
		return new Tuple<>(getFirst(), filter.test(getSecond()) ? Optional.of(getSecond()) : Optional.empty());
	}
}
