package com.hlag.oversigt.sources;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import com.google.common.base.Strings;
import com.hlag.oversigt.connect.db.DatabaseCache;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.sources.data.JsonHint;
import com.hlag.oversigt.sources.data.JsonHint.ArrayStyle;
import com.hlag.oversigt.sources.event.MapEvent;
import com.hlag.oversigt.sources.event.MapEvent.Point;

import de.larssh.utils.Nullables;

@EventSource(displayName = "World Map", view = "Worldmap", hiddenDataItems = { "updated-at-message" })
public class WorldMapEventSource extends AbstractCachingJdbcEventSource<Point, MapEvent> {

	private String query = "SELECT ID_NUMBER, LATITUDE, LONGITUDE, TYPE\r\n" + //
			"FROM table\r\n" + //
			"WHERE condition = 1";

	private TypeMapping[] typeMappings = new TypeMapping[] {
			new TypeMapping("TYPE", "type-a", Color.parse("#e75200"), Color.WHITE, 6.0),
			new TypeMapping("TYPE", "type-b", Color.parse("#0dc92c"), Color.WHITE, 5.0) };

	@Override
	protected DatabaseCache<Point> createCache() {
		return DatabaseCache.createCache(this::readShipPositions);
	}

	private Collection<Point> readShipPositions(final Connection connection) throws SQLException {
		return readFromDatabase(connection, this::readPoint, getQuery());
	}

	private Point readPoint(final ResultSet rs) throws SQLException {
		final String id = rs.getString("ID_NUMBER");
		final double dlong = rs.getDouble("LONGITUDE");
		final double dlat = rs.getDouble("LATITUDE");
		TypeMapping mapping = null;
		for (final TypeMapping tm : getTypeMappings()) {
			final String dbValue = Strings.nullToEmpty(rs.getString(tm.field)).trim();
			if (Strings.nullToEmpty(tm.value).equals(dbValue)) {
				mapping = tm;
				break;
			}
		}
		if (mapping == null) {
			return new Point(id, dlong, dlat);
		}
		return new Point(id, dlong, dlat, mapping.fill, mapping.stroke, mapping.size);
	}

	@Property(name = "Query",
			description = "The query used to read position information. The result must contain three columns: ID_NUMBER, LONGITUDE, LATITUDE. Each point will be identified by the ID_NUMBER. Once an ID_NUMBER is known the point belonging to it will be moved with every subsequent call. If the latest query result does not contain a record with the respective ID_NUMBER the point will be removed from the map. Additional columns can be used to use the type mappings e.g. for different colors of the single points.",
			type = "sql")
	public String getQuery() {
		return query;
	}

	public void setQuery(final String query) {
		this.query = query;
	}

	@Property(name = "Type Mappings", description = "Change the color (type) of a point based on column values")
	public TypeMapping[] getTypeMappings() {
		return Nullables.orElseGet(typeMappings, () -> new TypeMapping[0]);
	}

	public void setTypeMappings(final TypeMapping[] typeMappings) {
		this.typeMappings = typeMappings;
	}

	@Override
	protected MapEvent produceEventFromData() {
		return new MapEvent(stream().collect(Collectors.toList()));
	}

	@JsonHint(arrayStyle = ArrayStyle.TABS, headerTemplate = "Mapping {{i1}}")
	public static class TypeMapping {
		private final String field;

		private final String value;

		@NotNull
		private final Color fill;

		@NotNull
		private final Color stroke;

		@NotNull
		private final double size;

		public TypeMapping(final String field,
				final String value,
				final Color fill,
				final Color stroke,
				final double size) {
			this.field = field;
			this.value = value;
			this.fill = fill;
			this.stroke = stroke;
			this.size = size;
		}
	}
}
