package com.sentrysoftware.hardware.agent.service.opentelemetry.mapping;

import static com.sentrysoftware.hardware.agent.service.opentelemetry.mapping.MappingConstants.*;

import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.SIZE;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.ERROR_COUNT_ALARM_THRESHOLD;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.ERROR_COUNT_WARNING_THRESHOLD;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.sentrysoftware.hardware.agent.dto.metric.MetricInfo;
import com.sentrysoftware.hardware.agent.dto.metric.StaticIdentifyingAttribute;
import com.sentrysoftware.matrix.common.meta.monitor.IMetaMonitor;
import com.sentrysoftware.matrix.common.meta.monitor.LogicalDisk;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LogicalDiskMapping {

	private static final String LOGICAL_DISK_STATUS_METRIC_NAME = "hw.logical_disk.status";
	private static final String LOGICAL_DISK_USAGE_METRIC_NAME = "hw.logical_disk.usage";
	private static final String LOGICAL_DISK_UTILIZATION_METRIC_NAME = "hw.logical_disk.utilization";
	private static final String LOGICAL_DISK_NAME = "logical disk";

	/**
	 * Build logical disk metrics map
	 *
	 * @return  {@link Map} where the metrics are indexed by the matrix parameter name
	 */
	static Map<String, List<MetricInfo>> buildLogicalDiskMetricsMapping() {
		final Map<String, List<MetricInfo>> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

		map.put(
			IMetaMonitor.STATUS.getName(),
			List.of(
				MetricInfo
					.builder()
					.name(LOGICAL_DISK_STATUS_METRIC_NAME)
					.description(createStatusDescription(LOGICAL_DISK_NAME, OK_ATTRIBUTE_VALUE))
					.identifyingAttribute(
						StaticIdentifyingAttribute
							.builder()
							.key(STATE_ATTRIBUTE_KEY)
							.value(OK_ATTRIBUTE_VALUE)
							.build()
					)
					.predicate(OK_STATUS_PREDICATE)
					.build(),
				MetricInfo
					.builder()
					.name(LOGICAL_DISK_STATUS_METRIC_NAME)
					.description(createStatusDescription(LOGICAL_DISK_NAME, DEGRADED_ATTRIBUTE_VALUE))
					.identifyingAttribute(
						StaticIdentifyingAttribute
							.builder()
							.key(STATE_ATTRIBUTE_KEY)
							.value(DEGRADED_ATTRIBUTE_VALUE)
							.build()
					)
					.predicate(DEGRADED_STATUS_PREDICATE)
					.build(),
				MetricInfo
					.builder()
					.name(LOGICAL_DISK_STATUS_METRIC_NAME)
					.description(createStatusDescription(LOGICAL_DISK_NAME, FAILED_ATTRIBUTE_VALUE))
					.identifyingAttribute(
						StaticIdentifyingAttribute
							.builder()
							.key(STATE_ATTRIBUTE_KEY)
							.value(FAILED_ATTRIBUTE_VALUE)
							.build()
					)
					.predicate(FAILED_STATUS_PREDICATE)
					.build()
			)
		);

		map.put(
			IMetaMonitor.PRESENT.getName(),
			Collections.singletonList(
				MetricInfo
					.builder()
					.name(LOGICAL_DISK_STATUS_METRIC_NAME)
					.description(createPresentDescription(LOGICAL_DISK_NAME))
					.identifyingAttribute(
						StaticIdentifyingAttribute
							.builder()
							.key(STATE_ATTRIBUTE_KEY)
							.value(PRESENT_ATTRIBUTE_VALUE)
							.build()
					)
					.predicate(PRESENT_PREDICATE)
					.build()
			)
		);

		map.put(
			LogicalDisk.UNALLOCATED_SPACE.getName(),
			Collections.singletonList(
				MetricInfo
					.builder()
					.name(LOGICAL_DISK_USAGE_METRIC_NAME)
					.description("Amount of unused space in the logical disk.")
					.unit(BYTES_UNIT)
					.factor(BYTES_TO_GB_FACTOR)
					.identifyingAttribute(
						StaticIdentifyingAttribute
							.builder()
							.key(STATE_ATTRIBUTE_KEY)
							.value(FREE_ATTRIBUTE_VALUE)
							.build()
					)
					.build()
			)
		);

		map.put(
			LogicalDisk.ALLOCATED_SPACE.getName(),
			Collections.singletonList(
				MetricInfo
					.builder()
					.name(LOGICAL_DISK_USAGE_METRIC_NAME)
					.description("Amount of used space in the logical disk.")
					.unit(BYTES_UNIT)
					.identifyingAttribute(
						StaticIdentifyingAttribute
							.builder()
							.key(STATE_ATTRIBUTE_KEY)
							.value(USED_ATTRIBUTE_VALUE)
							.build()
					)
					.build()
			)
		);

		map.put(
			LogicalDisk.ALLOCATED_SPACE_PERCENT.getName(),
			Collections.singletonList(
				MetricInfo
					.builder()
					.name(LOGICAL_DISK_UTILIZATION_METRIC_NAME)
					.description("Ratio of used space in the logical disk.")
					.unit(RATIO_UNIT)
					.factor(RATIO_FACTOR)
					.identifyingAttribute(
						StaticIdentifyingAttribute
							.builder()
							.key(STATE_ATTRIBUTE_KEY)
							.value(USED_ATTRIBUTE_VALUE)
							.build()
					)
					.build()
			)
		);

		map.put(
			LogicalDisk.UNALLOCATED_SPACE_PERCENT.getName(),
			Collections.singletonList(
				MetricInfo
					.builder()
					.name(LOGICAL_DISK_UTILIZATION_METRIC_NAME)
					.description("Ratio of unused space in the logical disk.")
					.unit(RATIO_UNIT)
					.factor(RATIO_FACTOR)
					.identifyingAttribute(
						StaticIdentifyingAttribute
							.builder()
							.key(STATE_ATTRIBUTE_KEY)
							.value(FREE_ATTRIBUTE_VALUE)
							.build()
					)
					.build()
			)
		);

		map.put(
			LogicalDisk.ERROR_COUNT.getName(),
			Collections.singletonList(
				MetricInfo
					.builder()
					.name("hw.logical_disk.errors")
					.description("Number of errors encountered by the logical disk since the start of the Hardware Sentry Agent.")
					.unit(ERRORS_UNIT)
					.build()
			)
		);


		return map;
	}

	/**
	 * Create LogicalDisk Metadata to metrics map
	 * 
	 * @return {@link Map} of {@link MetricInfo} instances indexed by the matrix parameter names
	 */
	static Map<String, List<MetricInfo>> logicalDiskMetadataToMetrics() {
		final Map<String, List<MetricInfo>> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

		map.put(
			SIZE,
			Collections.singletonList(
				MetricInfo
					.builder()
					.name("hw.logical_disk.limit")
					.unit(BYTES_UNIT)
					.description("Logical disk size.")
					.build()
			)
		);

		map.put(
			ERROR_COUNT_WARNING_THRESHOLD,
			Collections.singletonList(
				MetricInfo
					.builder()
					.name("hw.logical_disk.errors_warning")
					.description(WARNING_THRESHOLD_OF_ERRORS)
					.unit(ERRORS_UNIT)
					.build()
			)
		);

		map.put(
			ERROR_COUNT_ALARM_THRESHOLD,
			Collections.singletonList(
				MetricInfo
					.builder()
					.name("hw.logical_disk.errors_alarm")
					.description(ALARM_THRESHOLD_OF_ERRORS)
					.unit(ERRORS_UNIT)
					.build()
			)
		);

		return map;
	}
}
