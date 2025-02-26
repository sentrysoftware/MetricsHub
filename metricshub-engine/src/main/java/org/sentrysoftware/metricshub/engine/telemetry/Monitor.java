package org.sentrysoftware.metricshub.engine.telemetry;

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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.sentrysoftware.metricshub.engine.alert.AlertRule;
import org.sentrysoftware.metricshub.engine.common.helpers.KnownMonitorType;
import org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants;
import org.sentrysoftware.metricshub.engine.telemetry.metric.AbstractMetric;

/**
 * Represents a monitoring entity with associated metrics, attributes, and alert rules.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Monitor {

	@Default
	private Map<String, AbstractMetric> metrics = new HashMap<>();

	@Default
	private Map<String, String> attributes = new HashMap<>();

	@Default
	private Map<String, String> conditionalCollection = new HashMap<>();

	@Default
	private Map<String, String> legacyTextParameters = new HashMap<>();

	@Default
	private Map<String, List<AlertRule>> alertRules = new HashMap<>();

	private Resource attachToResource;
	private Long discoveryTime;
	private String type;
	private String id;

	@JsonProperty("is_endpoint")
	private boolean isEndpoint;

	@JsonProperty("is_resource")
	private boolean isResource;

	/**
	 * Gets a metric of the specified type by name.
	 *
	 * @param metricName The unique name of the metric.
	 * @param type       The type of the metric.
	 * @param <T>        The metric type T
	 * @return The metric instance, or {@code null} if not found.
	 */
	public <T extends AbstractMetric> T getMetric(final String metricName, final Class<T> type) {
		return type.cast(metrics.get(metricName));
	}

	/**
	 * Get a metric by metric name
	 *
	 * @param metricName The unique name of the metric
	 * @return {@link AbstractMetric} instance
	 */
	public AbstractMetric getMetric(final String metricName) {
		return metrics.get(metricName);
	}

	/***
	 * Add a new metric in the map of metrics
	 *
	 * @param metricName The unique name of the metric
	 * @param metric     The metric instance to add
	 */
	public void addMetric(final String metricName, final AbstractMetric metric) {
		metrics.put(metricName, metric);
	}

	/**
	 * Add the given attributes to the current map of attributes
	 *
	 * @param attributes Map of key-pair values to be added to the current map of attributes
	 */
	public void addAttributes(@NonNull final Map<String, String> attributes) {
		this.attributes.putAll(attributes);
	}

	/**
	 * Add the given conditionalCollection map to the current map of conditionalCollection
	 *
	 * @param conditionalCollection Map of key-pair values to be added to the current map of conditionalCollection
	 */
	public void addConditionalCollection(Map<String, String> conditionalCollection) {
		this.conditionalCollection.putAll(conditionalCollection);
	}

	/**
	 * Add the given legacyTextParameters map to the current map of legacyTextParameters
	 *
	 * @param legacyTextParameters Map of key-pair values to be added to the current map of legacyTextParameters
	 */
	public void addLegacyParameters(Map<String, String> legacyTextParameters) {
		this.legacyTextParameters.putAll(legacyTextParameters);
	}

	/**
	 * This method adds a new attribute to the current monitor
	 * @param key attribute key
	 * @param value attribute value
	 */
	public void addAttribute(final String key, final String value) {
		attributes.put(key, value);
	}

	/**
	 * This method gets an attribute from the current monitor using its key
	 * @param key attribute key
	 * @return attribute value
	 */
	public String getAttribute(final String key) {
		return attributes.get(key);
	}

	/**
	 * This method returns whether a metric is deactivated
	 * @param key metric key
	 * @return boolean
	 */
	public boolean isMetricDeactivated(final String key) {
		return MetricsHubConstants.EMPTY.equals(conditionalCollection.get(key));
	}

	/**
	 * This method checks if the monitor type is {@code host} and represents an endpoint.
	 *
	 * @return {@code true} if the monitor is of type "host" and is an endpoint; {@code false} otherwise.
	 */
	public boolean isEndpointHost() {
		return KnownMonitorType.HOST.getKey().equals(type) && isEndpoint();
	}

	/**
	 * Set isEndpoint to <code>true</code> or <code>false</code>
	 *
	 * @param isEndpoint boolean value
	 */
	public void setIsEndpoint(final boolean isEndpoint) {
		this.isEndpoint = isEndpoint;
	}

	/**
	 * Set the current monitor as endpoint
	 */
	public void setAsEndpoint() {
		setIsEndpoint(true);
	}
}
