package com.hlag.oversigt.storage;

import java.io.Closeable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.model.EventSourceDescriptor;
import com.hlag.oversigt.model.EventSourceInstance;
import com.hlag.oversigt.model.Widget;
import com.hlag.oversigt.properties.SerializableProperty;

public interface Storage extends Closeable {
	List<String> getEventSourceNames();

	List<String> getDashboardIds();

	List<String> getOwnedDashboardIds(String userid);

	List<String> getEditableDashboardIds(String userid);

	void persistDashboard(Dashboard dashboard);

	boolean deleteDashboard(Dashboard dashboard);

	void updateWidget(Widget widget);

	void deleteWidget(Widget widget);

	<T extends SerializableProperty> List<T> listProperties(Class<T> clazz);

	<T extends SerializableProperty> Optional<T> getProperty(Class<T> clazz, int id);

	<T extends SerializableProperty> T createProperty(Class<T> clazz, String name, Object... parameters);

	void updateProperty(SerializableProperty value);

	void deleteProperty(Class<? extends SerializableProperty> clazz, int id);

	Collection<String> getEventSourcesIds();

	EventSourceInstance loadEventSourceInstance(String id,
			BiFunction<Optional<String>, String, EventSourceDescriptor> descriptorSupplier);

	Map<String, String> getEventSourceInstanceProperties(String id);

	Map<String, String> getEventSourceInstanceDataItems(String id);

	void updateEventSourceClasses(String oldClassName, String newClassName);

	List<Dashboard> loadDashboards();

	Optional<Dashboard> loadDashboard(String id);

	List<Widget> loadWidgetDatas(Dashboard dashboard, Function<String, EventSourceInstance> instanceProvider);

	void createWidget(Dashboard dashboard, Widget widget);

	void persistEventSourceInstance(EventSourceInstance instance);

	void deleteEventSourceInstance(String eventSourceId);
}
