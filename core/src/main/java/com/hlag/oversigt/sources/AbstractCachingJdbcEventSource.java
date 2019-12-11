package com.hlag.oversigt.sources;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.stream.Stream;

import com.hlag.oversigt.connect.db.DatabaseCache;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.Property;

/**
 * @author neumaol
 *
 */
public abstract class AbstractCachingJdbcEventSource<X, T extends OversigtEvent> extends AbstractJdbcEventSource<T> {
	private Optional<DatabaseCache<X>> cache = Optional.empty();

	private long limit = 5;

	protected AbstractCachingJdbcEventSource() {}

	protected synchronized DatabaseCache<X> getCache() {
		if (!cache.isPresent()) {
			cache = Optional.of(createCache());
		}
		return cache.get();
	}

	protected abstract DatabaseCache<X> createCache();

	@Property(name = "Max item count",
			description = "The maximum number of items to be listed. Set to 0 for unlimited number of items.")
	public long getLimit() {
		return limit;
	}

	public void setLimit(final long limit) {
		this.limit = limit;
	}

	protected Stream<X> stream() {
		Stream<X> stream = getCache().stream();
		if (getLimit() > 0) {
			stream = stream.limit(getLimit());
		}
		return stream;
	}

	@Override
	protected void gatherDatabaseInfo(final Connection connection) throws SQLException {
		getCache().getItems(connection);
	}
}
