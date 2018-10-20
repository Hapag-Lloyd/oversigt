package com.hlag.oversigt.sources;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.stream.Stream;

import com.hlag.oversigt.connect.db.DatabaseCache;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.Property;

/**
 * @author neumaol
 *
 */
public abstract class AbstractCachingJdbcEventSource<X, T extends OversigtEvent> extends AbstractJdbcEventSource<T> {
	private DatabaseCache<X> cache = null;
	private long limit = 5;

	protected AbstractCachingJdbcEventSource() {
	}

	protected synchronized DatabaseCache<X> getCache() {
		if (cache == null) {
			cache = createCache();
		}
		return cache;
	}

	protected abstract DatabaseCache<X> createCache();

	@Property(name = "Max item count", description = "The maximum number of items to be listed. Set to 0 for unlimited number of items.")
	public long getLimit() {
		return limit;
	}

	public void setLimit(long limit) {
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
	protected void gatherDatabaseInfo(Connection connection) throws SQLException {
		getCache().getItems(connection);
	}
}
