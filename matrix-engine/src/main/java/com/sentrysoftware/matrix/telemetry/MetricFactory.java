package com.sentrysoftware.matrix.telemetry;

import com.sentrysoftware.matrix.connector.model.Connector;
import com.sentrysoftware.matrix.connector.model.metric.MetricDefinition;
import com.sentrysoftware.matrix.connector.model.metric.MetricType;
import com.sentrysoftware.matrix.connector.model.metric.StateSet;
import com.sentrysoftware.matrix.strategy.detection.ConnectorTestResult;
import com.sentrysoftware.matrix.telemetry.metric.AbstractMetric;
import com.sentrysoftware.matrix.telemetry.metric.NumberMetric;
import com.sentrysoftware.matrix.telemetry.metric.StateSetMetric;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.COMMA;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.CONNECTOR_STATUS_METRIC_KEY;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.EMPTY;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.MONITOR_ATTRIBUTE_ID;
@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MetricFactory {

	private static final Pattern METRIC_ATTRIBUTES_PATTERN = Pattern.compile("\\{(.*?)\\}");

	private TelemetryManager telemetryManager;

	/**
	 * This method sets a stateSet metric in the monitor
	 *
	 * @param monitor a given monitor
	 * @param metricName the metric's name
	 * @param value the metric's value
	 * @param stateSet array of states values. E.g. [ "ok", "degraded", "failed" ]
	 * @param collectTime the metric's collect time
	 * @return collected metric
	 */
	public StateSetMetric collectStateSetMetric(
			final Monitor monitor,
			final String metricName,
			final String value,
			final String[] stateSet,
			final long collectTime
	) {

		final StateSetMetric metric = monitor.getMetric(metricName, StateSetMetric.class);
		if (metric == null) {
			// Add the metric directly in the monitor's metrics
			final StateSetMetric newMetric = StateSetMetric
				.builder()
				.stateSet(stateSet)
				.name(metricName)
				.collectTime(collectTime)
				.value(value)
				.attributes(extractAttributesFromMetricName(metricName))
				.build();
			monitor.addMetric(metricName, newMetric);
			return newMetric;
		} else {
			// stateSet, metricName, and metric's attributes will never change over the collects
			// so, we only set the value and collect time
			metric.setValue(value);
			metric.setCollectTime(collectTime);
			return metric;
		}
	}

	/**
	 * This method extracts the metric attributes from its name
	 *
	 * @param metricName the metric's name
	 * @return a Map with attributes names as keys and attributes values as values
	 */
	public static Map<String, String> extractAttributesFromMetricName(final String metricName) {

		// Create a map to store the extracted attributes
		final Map<String, String> attributes = new HashMap<>();

		// Create a Matcher object
		final Matcher matcher = METRIC_ATTRIBUTES_PATTERN.matcher(metricName);

		if (matcher.find()) {
			final String attributeMap = matcher.group(1);

			// Split the attribute map into key-value pairs
			final String[] keyValuePairs = attributeMap.split(COMMA);

			// Iterate through the key-value pairs
			for (String pair : keyValuePairs) {
				final String[] parts = pair.trim().split("=");
				if (parts.length == 2) {
					// Set the key-value pair and remove the double quotes from the value
					attributes.put(parts[0], parts[1].replace("\"", EMPTY));
				}
			}
		}

		return attributes;
	}

	/**
	 * This method sets number metric in the monitor
	 *
	 * @param monitor a given monitor
	 * @param name the metric's name
	 * @param value the metric's value
	 * @param collectTime the metric's collect time
	 * @return collected metric
	 */
	public NumberMetric collectNumberMetric(
		final Monitor monitor,
		final String name,
		@NonNull final Double value,
		final Long collectTime
	) {

		final NumberMetric metric = monitor.getMetric(name, NumberMetric.class);
		if (metric == null) {
			// Add the metric directly in the monitor's metrics
			final NumberMetric newMetric = NumberMetric
				.builder()
				.name(name)
				.collectTime(collectTime)
				.value(value)
				.attributes(extractAttributesFromMetricName(name))
				.build();
			monitor.addMetric(name , newMetric);
			return newMetric;
		} else {
			// stateSet, metricName, and metric's attributes will never change over the collects
			// so, we only set the value and collect time
			metric.setValue(value);
			metric.setCollectTime(collectTime);
			return metric;
		}

	}

	/**
	 * This method sets number metric in the monitor
	 *
	 * @param monitor a given monitor
	 * @param name the metric's name
	 * @param value the metric's value
	 * @param collectTime the metric's collect time
	 * @return collected metric
	 */

	public NumberMetric collectNumberMetric(
		final Monitor monitor,
		final String name,
		@NonNull final String value,
		final Long collectTime
	) {

		try {
			return collectNumberMetric(monitor, name, Double.parseDouble(value), collectTime);
		} catch (Exception e) {
			log.warn("Hostname {} - Cannot parse the {} value '{}' for monitor id {}. {} won't be collected",
				telemetryManager.getHostConfiguration().getHostname(),
				name,
				value,
				monitor.getAttributes().get(MONITOR_ATTRIBUTE_ID),
				name
			);
			return null;
		}
	}

	/**
	 * Collect the connector status metric as a number
	 *
	 * @param connectorTestResult contains information about connector tests
	 * @param monitor the monitor we currently collect its status metric
	 * @param strategyTime strategy time
	 */
	public void collectConnectorStatusNumberMetric(
		final ConnectorTestResult connectorTestResult,
		final Monitor monitor,
		final long strategyTime
	) {
		final MetricFactory metricFactory = new MetricFactory(telemetryManager);
		if (connectorTestResult.isSuccess()) {
			metricFactory.collectNumberMetric(monitor, CONNECTOR_STATUS_METRIC_KEY, 1.0, strategyTime);
		} else {
			metricFactory.collectNumberMetric(monitor, CONNECTOR_STATUS_METRIC_KEY, 0.0, strategyTime);
		}
	}

	/**
	 * This method returns a metric definition based on the extracted metric name (metric name without attributes)
	 * @param connector a given connector
	 * @param monitor a given monitor
	 * @param metricName a given metric name
	 * @return MetricDefinition instance
	 */
	public MetricDefinition getMetricDefinitionFromExtractedMetricName(final Connector connector, final Monitor monitor, final String metricName) {

		// Get monitor metrics from connector
		final Map<String, MetricDefinition> metricDefinitionMap = connector.getMetrics();

		// Remove attribute parts from the metric name
		String extractedName = extractName(metricName);

		// Retrieve the metric definition using the extracted name
		return metricDefinitionMap.get(extractedName);
	}

	/**
	 * This method collects a metric using the connector metrics
	 *
	 * @param connector    a given connector
	 * @param monitor      a given monitor
	 * @param strategyTime strategy time
	 * @param metricName   metric's name
	 * @param metricValue  metric's value
	 * @return AbstractMetric instance
	 */
	public AbstractMetric collectMetricUsingConnector(
		final Connector connector,
		final Monitor monitor,
		final long strategyTime,
		final String metricName,
		final String metricValue
	) {

		AbstractMetric metric = null;

		// Retrieve the metric definition using the extracted metric name
		final MetricDefinition metricDefinition = getMetricDefinitionFromExtractedMetricName(connector, monitor, metricName);

		// Retrieve metric attributes from metric's name
		final Map<String, String> metricAttributes = extractAttributesFromMetricName(metricName);

		// Create a boolean flag to check for the state attribute
		boolean hasStateAttribute = checkForStateAttribute(metricAttributes);

		// Update the Number metric check
		if (metricDefinition == null || (metricDefinition.getType() instanceof MetricType) || hasStateAttribute) {
			metric = collectNumberMetric(monitor, metricName, metricValue, strategyTime);
		} else if (metricDefinition.getType() instanceof StateSet stateSetType) {
			// When metric type is stateSet
			final String[] stateSet = stateSetType.getSet().stream().toArray(String[]::new);
			metric = collectStateSetMetric(monitor, metricName, metricValue, stateSet, strategyTime);
		}
		return metric;
	}

	/**
	 * This method removes attribute parts from the metric name
	 * @param name metric name with or without attributes
	 * @return metric name without attributes
	 */
	private String extractName(final String name) {
		final int openBracketPosition = name.indexOf("{");
		if (openBracketPosition >= 0) {
			return name.substring(0, openBracketPosition);
		}
		return name;
	}

	/**
	 * This method returns a boolean flag to check whether the state attribute exists
	 * @param attributes
	 * @return boolean whether metric attributes contain state attribute
	 */
	public boolean checkForStateAttribute(final Map<String, String> attributes) {
		return attributes.keySet().stream().anyMatch(attributeKey -> attributeKey.equals("hw.status"));
	}

	/**
	 * This method collects monitor metrics
	 * @param monitorType the monitor's type
	 * @param connector connector
	 * @param hostname host name
	 * @param monitor a given monitor
	 * @param connectorId connector id
	 * @param metrics metrics
	 */
	public void collectMonitorMetrics(
		final String monitorType,
		final Connector connector,
		final String hostname,
		final Monitor monitor,
		final String connectorId,
		final Map<String, String> metrics,
		final long strategyTime,
		final boolean isDiscovery
	) {
		for (final Map.Entry<String, String> metricEntry : metrics.entrySet()) {

			final String name = metricEntry.getKey();

			// Check if the conditional collection tells that the metric shouldn't be collected
			if (monitor.isMetricDeactivated(name)){
				continue;
			}

			final String value = metricEntry.getValue();

			if (value == null) {
				log.warn(
						"Hostname {} - No value found for metric {}. Skip metric collection on {}. Connector: {}",
						hostname,
						name,
						monitorType,
						connectorId
				);

				continue;
			}

			// Set the metrics in the monitor using the connector metrics
			final AbstractMetric metric = collectMetricUsingConnector(connector, monitor, strategyTime, name, value);

			// Tell the collect that the refresh time of the discovered
			// metric must be refreshed
			if (isDiscovery && metric != null) {
				metric.setResetMetricTime(true);
			}
		}
	}

}
