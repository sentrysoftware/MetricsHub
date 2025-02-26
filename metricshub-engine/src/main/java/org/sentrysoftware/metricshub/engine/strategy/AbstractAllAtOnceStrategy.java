package org.sentrysoftware.metricshub.engine.strategy;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.MAX_THREADS_COUNT;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_JOBS_PRIORITY;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.OTHER_MONITOR_JOB_TYPES;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.THREAD_TIMEOUT;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.sentrysoftware.metricshub.engine.client.ClientsExecutor;
import org.sentrysoftware.metricshub.engine.common.ConnectorMonitorTypeComparator;
import org.sentrysoftware.metricshub.engine.common.JobInfo;
import org.sentrysoftware.metricshub.engine.common.helpers.KnownMonitorType;
import org.sentrysoftware.metricshub.engine.connector.model.Connector;
import org.sentrysoftware.metricshub.engine.connector.model.ConnectorStore;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.MonitorJob;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.AbstractMonitorTask;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.Discovery;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.Mapping;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.Simple;
import org.sentrysoftware.metricshub.engine.extension.ExtensionManager;
import org.sentrysoftware.metricshub.engine.strategy.source.OrderedSources;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceTable;
import org.sentrysoftware.metricshub.engine.strategy.surrounding.AfterAllStrategy;
import org.sentrysoftware.metricshub.engine.strategy.surrounding.BeforeAllStrategy;
import org.sentrysoftware.metricshub.engine.strategy.utils.MappingProcessor;
import org.sentrysoftware.metricshub.engine.telemetry.MetricFactory;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.MonitorFactory;
import org.sentrysoftware.metricshub.engine.telemetry.Resource;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

/**
 * Abstract strategy class for processing all monitor jobs at once.
 * Extends {@link AbstractStrategy}.
 */
@Slf4j
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractAllAtOnceStrategy extends AbstractStrategy {

	/**
	 * Initializes a new instance of {@code AbstractAllAtOnceStrategy} with the necessary components for executing the strategy.
	 * @param telemetryManager The telemetry manager responsible for managing telemetry data (monitors and metrics).
	 * @param strategyTime     The execution time of the strategy, used for timing purpose.
	 * @param clientsExecutor  An executor service for handling client operations within the strategy.
	 * @param extensionManager The extension manager where all the required extensions are handled.
	 */
	protected AbstractAllAtOnceStrategy(
		@NonNull final TelemetryManager telemetryManager,
		final long strategyTime,
		@NonNull final ClientsExecutor clientsExecutor,
		@NonNull final ExtensionManager extensionManager
	) {
		super(telemetryManager, strategyTime, clientsExecutor, extensionManager);
	}

	/**
	 * This method processes each connector
	 *
	 * @param currentConnector The current connector
	 * @param hostname		   The host name
	 */
	private void process(final Connector currentConnector, final String hostname) {
		// Check whether the strategy job name matches at least one of the monitor jobs names of the current connector
		final boolean connectorHasExpectedJobTypes = hasExpectedJobTypes(currentConnector, getJobName());
		// If the connector doesn't define any monitor job that matches the given strategy job name, log a message then exit the current discovery or simple operation
		if (!connectorHasExpectedJobTypes) {
			log.debug("Connector doesn't define any monitor job of type {}.", getJobName());
			return;
		}
		if (!validateConnectorDetectionCriteria(currentConnector, hostname, getJobName())) {
			log.error(
				"Hostname {} - The connector {} no longer matches the host. Stopping the connector's {} job.",
				hostname,
				currentConnector.getCompiledFilename(),
				getJobName()
			);
			return;
		}

		// Run BeforeAllStrategy that executes beforeAll sources
		final BeforeAllStrategy beforeAllStrategy = BeforeAllStrategy
			.builder()
			.clientsExecutor(clientsExecutor)
			.strategyTime(strategyTime)
			.telemetryManager(telemetryManager)
			.connector(currentConnector)
			.extensionManager(extensionManager)
			.build();

		beforeAllStrategy.run();

		// Sort the connector monitor jobs according to the priority map
		final Map<String, MonitorJob> connectorMonitorJobs = currentConnector
			.getMonitors()
			.entrySet()
			.stream()
			.sorted(
				Comparator.comparing(entry ->
					MONITOR_JOBS_PRIORITY.containsKey(entry.getKey())
						? MONITOR_JOBS_PRIORITY.get(entry.getKey())
						: MONITOR_JOBS_PRIORITY.get(OTHER_MONITOR_JOB_TYPES)
				)
			)
			.collect(
				Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new)
			);

		final Map<String, MonitorJob> sequentialMonitorJobs = connectorMonitorJobs
			.entrySet()
			.stream()
			.filter(entry -> MONITOR_JOBS_PRIORITY.containsKey(entry.getKey()))
			.collect(
				Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new)
			);

		final Map<String, MonitorJob> otherMonitorJobs = connectorMonitorJobs
			.entrySet()
			.stream()
			.filter(entry -> !MONITOR_JOBS_PRIORITY.containsKey(entry.getKey()))
			.collect(
				Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new)
			);

		// Run monitor jobs defined in monitor jobs priority map (host, enclosure, blade, disk_controller and cpu)  in sequential mode
		sequentialMonitorJobs.entrySet().forEach(entry -> processMonitorJob(currentConnector, hostname, entry));

		// If monitor jobs execution is set to "sequential", execute monitor jobs one by one
		if (telemetryManager.getHostConfiguration().isSequential()) {
			otherMonitorJobs.entrySet().forEach(entry -> processMonitorJob(currentConnector, hostname, entry));
		} else {
			// Execute monitor jobs in parallel
			log.info(
				"Hostname {} - Running {} in parallel mode. Connector: {}.",
				hostname,
				getJobName(),
				currentConnector.getConnectorIdentity().getCompiledFilename()
			);

			final ExecutorService threadsPool = Executors.newFixedThreadPool(MAX_THREADS_COUNT);

			otherMonitorJobs
				.entrySet()
				.forEach(entry -> threadsPool.execute(() -> processMonitorJob(currentConnector, hostname, entry)));

			// Order the shutdown
			threadsPool.shutdown();

			try {
				// Blocks until all tasks have completed execution after a shutdown request
				threadsPool.awaitTermination(THREAD_TIMEOUT, TimeUnit.SECONDS);
			} catch (Exception e) {
				if (e instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				log.debug("Hostname {} - Waiting for threads' termination aborted with an error.", hostname, e);
			}
		}
		// Run AfterAllStrategy that executes afterAll sources
		final AfterAllStrategy afterAllStrategy = AfterAllStrategy
			.builder()
			.clientsExecutor(clientsExecutor)
			.strategyTime(strategyTime)
			.telemetryManager(telemetryManager)
			.connector(currentConnector)
			.extensionManager(extensionManager)
			.build();

		afterAllStrategy.run();
	}

	/**
	 * This method processes a monitor job
	 *
	 * @param currentConnector
	 * @param hostname
	 * @param monitorJobEntry
	 */
	private void processMonitorJob(
		final Connector currentConnector,
		final String hostname,
		final Map.Entry<String, MonitorJob> monitorJobEntry
	) {
		final long jobStartTime = System.currentTimeMillis();

		final MonitorJob monitorJob = monitorJobEntry.getValue();

		// Get the monitor task
		AbstractMonitorTask monitorTask = retrieveTask(monitorJob);

		if (monitorTask == null) {
			return;
		}

		final String monitorType = monitorJobEntry.getKey();

		if (isMonitorFiltered(monitorType)) {
			return;
		}

		final JobInfo jobInfo = JobInfo
			.builder()
			.hostname(hostname)
			.connectorId(currentConnector.getCompiledFilename())
			.jobName(getJobName())
			.monitorType(monitorType)
			.build();

		// Build the ordered sources
		final OrderedSources orderedSources = OrderedSources
			.builder()
			.sources(
				monitorTask.getSources(),
				monitorTask.getExecutionOrder().stream().collect(Collectors.toList()), // NOSONAR
				monitorTask.getSourceDep(),
				jobInfo
			)
			.build();

		// Create the sources and the computes for a connector
		processSourcesAndComputes(orderedSources.getSources(), jobInfo);

		// Create the monitors
		final Mapping mapping = monitorTask.getMapping();

		processSameTypeMonitors(currentConnector, mapping, monitorType, hostname, monitorJob);
		final long jobEndTime = System.currentTimeMillis();
		// Set the job duration metric in the host monitor
		setJobDurationMetric(getJobName(), monitorType, currentConnector.getCompiledFilename(), jobStartTime, jobEndTime);
	}

	/**
	 * This method processes same type monitors
	 *
	 * @param connector   The connector instance defining the ID and also used to collect monitor's metrics
	 * @param mapping     The mapping instance defining the attributes, metrics, conditional collection, legacy text parameters and resource
	 * @param monitorType The type of the monitor we currently process
	 * @param hostname    The host name of the monitored resource
	 */
	private void processSameTypeMonitors(
		final Connector connector,
		final Mapping mapping,
		final String monitorType,
		final String hostname,
		final MonitorJob monitorJob
	) {
		final String connectorId = connector.getCompiledFilename();

		// Check the source, so that, we can create the monitor later
		final String source = mapping.getSource();
		if (source == null) {
			log.warn(
				"Hostname {} - No instance tables found with {} during the {} job for the connector {}. Skip processing.",
				hostname,
				monitorType,
				getJobName(),
				connectorId
			);
			return;
		}

		// Checking for defined attributes to create monitors based on them
		// If no attributes are found, skip processing
		// This may indicate a job created to consolidate source values
		if (mapping.getAttributes() == null) {
			log.info(
				"Hostname {} - No mapping attributes defined with {} during the {} job for the connector {}. Skip processing.",
				hostname,
				monitorType,
				getJobName(),
				connectorId
			);
			return;
		}

		// Call lookupSourceTable to find the source table
		final Optional<SourceTable> maybeSourceTable = SourceTable.lookupSourceTable(source, connectorId, telemetryManager);

		if (maybeSourceTable.isEmpty()) {
			log.warn(
				"Hostname {} - The source table {} is not found during the {} job for the connector {}. Skip processing.",
				hostname,
				source,
				getJobName(),
				connectorId
			);
			return;
		}

		// If the source table is not empty, loop over the source table rows
		final List<List<String>> table = maybeSourceTable.get().getTable();

		log.debug(
			"Hostname {} - Start {} {} mapping with source {}, attributes {}, metrics {}, conditional collection {}, legacy text parameters {} " +
			"isResource {} and attached resource {}. Connector ID: {}.",
			hostname,
			monitorType,
			getJobName(),
			mapping.getSource(),
			mapping.getAttributes(),
			mapping.getMetrics(),
			mapping.getConditionalCollection(),
			mapping.getLegacyTextParameters(),
			mapping.isResource(),
			mapping.getAttachToResource(),
			connectorId
		);

		for (int i = 0; i < table.size(); i++) {
			final List<String> row = table.get(i);
			// Init mapping processor
			final MappingProcessor mappingProcessor = MappingProcessor
				.builder()
				.telemetryManager(telemetryManager)
				.mapping(mapping)
				.jobInfo(
					JobInfo
						.builder()
						.connectorId(connectorId)
						.hostname(hostname)
						.monitorType(monitorType)
						.jobName(getJobName())
						.build()
				)
				.collectTime(strategyTime)
				.row(row)
				.indexCounter(i + 1)
				.build();

			// Use the mapping processor to extract attributes and resource
			final Map<String, String> noContextAttributeInterpretedValues =
				mappingProcessor.interpretNonContextMappingAttributes();

			final Resource resource = mappingProcessor.interpretMappingResource();

			// Initialize a monitor factory with the previously created attributes and resources

			final MonitorFactory monitorFactory = MonitorFactory
				.builder()
				.monitorType(monitorType)
				.telemetryManager(telemetryManager)
				.attributes(noContextAttributeInterpretedValues)
				.isResource(mapping.isResource())
				.attachedToResource(resource)
				.connectorId(connectorId)
				.discoveryTime(strategyTime)
				.keys(monitorJob.getKeys())
				.build();

			// The attribute id is mandatory
			if (noContextAttributeInterpretedValues.get(MONITOR_ATTRIBUTE_ID) == null) {
				log.info(
					"Hostname {} - No mapping attribute 'id' found with {} during the {} job for the connector {}. Processed row: {}. The monitor will not be created.",
					hostname,
					monitorType,
					getJobName(),
					connectorId,
					row
				);
				continue;
			}

			// Create or update the monitor
			final Monitor monitor = monitorFactory.createOrUpdateMonitor();

			final Map<String, String> contextAttributes = mappingProcessor.interpretContextMappingAttributes(monitor);

			// Update the monitor's attributes by adding the context attributes
			monitor.addAttributes(contextAttributes);

			// Collect conditional collection
			monitor.addConditionalCollection(mappingProcessor.interpretNonContextMappingConditionalCollection());
			monitor.addConditionalCollection(mappingProcessor.interpretContextMappingConditionalCollection(monitor));

			// Collect metrics
			final Map<String, String> metrics = mappingProcessor.interpretNonContextMappingMetrics();

			metrics.putAll(mappingProcessor.interpretContextMappingMetrics(monitor));

			final MetricFactory metricFactory = new MetricFactory(hostname);

			metricFactory.collectMonitorMetrics(monitorType, connector, monitor, connectorId, metrics, strategyTime, true);

			// Collect legacy parameters
			monitor.addLegacyParameters(mappingProcessor.interpretNonContextMappingLegacyTextParameters());
			monitor.addLegacyParameters(mappingProcessor.interpretContextMappingLegacyTextParameters(monitor));
		}
	}

	/**
	 * This is the main method. It runs the all the job operations
	 */
	public void run() {
		// Get the host name from telemetry manager
		final String hostname = telemetryManager.getHostname();

		// Get the endpoint host monitor
		final Monitor endpointHost = telemetryManager.getEndpointHostMonitor();
		if (endpointHost == null) {
			log.info("Hostname {} - No endpoint host found during {} strategy.", hostname, getJobName());
		} else {
			endpointHost.setDiscoveryTime(strategyTime);
		}

		//Retrieve connector Monitor instances from TelemetryManager
		final Map<String, Monitor> connectorMonitors = telemetryManager
			.getMonitors()
			.get(KnownMonitorType.CONNECTOR.getKey());

		// Check whether the resulting map is null or empty
		if (connectorMonitors == null || connectorMonitors.isEmpty()) {
			log.error(
				"Hostname {} - Collect - No connectors detected in the detection operation. Collect operation will now be stopped.",
				hostname
			);
			return;
		}

		//Filter connectors by their connector_id value (compiled file name) from TelemetryManager's connector store and create a list of Connector instances.
		final ConnectorStore connectorStore = telemetryManager.getConnectorStore();

		// Retrieve the detected connectors file names
		final Set<String> detectedConnectorFileNames = connectorMonitors
			.values()
			.stream()
			.map(monitor -> monitor.getAttributes().get(MONITOR_ATTRIBUTE_ID))
			.collect(Collectors.toSet());

		// Keep only detected/selected connectors, in the store they are indexed by the compiled file name
		// Build the list of the connectors
		final List<Connector> detectedConnectors = connectorStore
			.getStore()
			.entrySet()
			.stream()
			.filter(entry -> detectedConnectorFileNames.contains(entry.getKey()))
			.map(Map.Entry::getValue)
			.collect(Collectors.toList()); //NOSONAR

		// Get only connectors that define monitors
		final List<Connector> connectorsWithMonitorJobs = detectedConnectors
			.stream()
			.filter(connector -> !connector.getMonitors().isEmpty())
			.collect(Collectors.toList()); //NOSONAR

		// Sort connectors by monitor job type: first put hosts then enclosures. If two connectors have the same type of monitor job, sort them by name
		final List<Connector> sortedConnectors = connectorsWithMonitorJobs
			.stream()
			.sorted(new ConnectorMonitorTypeComparator())
			.collect(Collectors.toList()); //NOSONAR

		// Process each connector
		sortedConnectors.forEach(connector -> process(connector, hostname));
	}

	/**
	 * Get the name of the job
	 * @return String value
	 */
	protected abstract String getJobName();

	/**
	 * Retrieve the task of the given {@link MonitorJob}. E.g. {@link Discovery}
	 * or {@link Simple}
	 *
	 * @param monitorJob
	 * @return The {@link AbstractMonitorTask} implementation
	 */
	protected abstract AbstractMonitorTask retrieveTask(MonitorJob monitorJob);
}
