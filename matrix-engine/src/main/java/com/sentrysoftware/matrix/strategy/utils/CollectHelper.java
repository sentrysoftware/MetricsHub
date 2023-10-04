package com.sentrysoftware.matrix.strategy.utils;

import com.sentrysoftware.matrix.telemetry.Monitor;
import com.sentrysoftware.matrix.telemetry.metric.NumberMetric;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CollectHelper {

	/**
	 * Get the {@link NumberMetric} value
	 *
	 * @param monitor    The {@link Monitor} instance we wish to extract the {@link NumberMetric} value
	 * @param metricName The name of the {@link NumberMetric} instance
	 * @param previous   Indicate whether we should return the <code>value</code> or the <code>previousValue</code>.
	 * @return a {@link Double} value
	 */
	public static Double getNumberMetricValue(final Monitor monitor, final String metricName, final boolean previous) {
		final NumberMetric metric = monitor.getMetric(metricName, NumberMetric.class);

		if (metric == null) {
			return null;
		}

		return previous ? getDoubleValue(metric.getPreviousValue()) : getDoubleValue(metric.getValue());
	}

	/**
	 * Get the {@link NumberMetric} collect time
	 *
	 * @param monitor       The {@link Monitor} instance we wish to extract the {@link NumberMetric} collect time
	 * @param metricName The name of the {@link NumberMetric} instance
	 * @param previous      Indicate whether we should return the <code>collectTime</code> or the <code>previousCollectTime</code>.
	 * @return a {@link Double} value
	 */
	public static Double getNumberMetricCollectTime(
		final Monitor monitor,
		final String metricRateName,
		final boolean previous
	) {
		final NumberMetric metric = monitor.getMetric(metricRateName, NumberMetric.class);

		if (metric == null) {
			return null;
		}

		return previous ? getDoubleValue(metric.getPreviousCollectTime()) : getDoubleValue(metric.getCollectTime());
	}

	/**
	 * Return the {@link Double} value of the given {@link Number} instance
	 *
	 * @param number	The {@link Number} whose {@link Double} value should be extracted from
	 * @return {@link Double} instance
	 */
	public static Double getDoubleValue(final Number number) {
		if (number == null) {
			return null;
		}

		return number.doubleValue();
	}
}
