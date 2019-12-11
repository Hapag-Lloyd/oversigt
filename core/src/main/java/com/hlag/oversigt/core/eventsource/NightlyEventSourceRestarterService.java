package com.hlag.oversigt.core.eventsource;

import static com.hlag.oversigt.util.Utils.not;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.controller.EventSourceInstanceController;
import com.hlag.oversigt.model.EventSourceInstance;
import com.hlag.oversigt.util.Utils;

/**
 * Service restarting erroneous event sources once a day.
 *
 * @author neumaol
 */
@Singleton
public class NightlyEventSourceRestarterService extends NightlyService {
	@Inject
	private EventSourceInstanceController eventSourceInstanceController;

	public NightlyEventSourceRestarterService() {
		// no fields to be initialized manually, some will be injected
	}

	/**
	 * Executes one iteration of
	 * {@link com.google.common.util.concurrent.AbstractScheduledService} Sends
	 * event to event bus
	 */
	@Override
	protected final void runOneIteration() {
		eventSourceInstanceController.getEventSourceInstances()
				.stream()
				.filter(not(eventSourceInstanceController::isRunning))
				.filter(EventSourceInstance::isEnabled)
				.map(EventSourceInstance::getId)
				.peek(x -> Utils.sleep((long) (10000 * Math.random())))
				.forEach(id -> eventSourceInstanceController.startInstance(id, true));
	}

	@Override
	protected final String serviceName() {
		return getClass().getSimpleName();
	}
}
