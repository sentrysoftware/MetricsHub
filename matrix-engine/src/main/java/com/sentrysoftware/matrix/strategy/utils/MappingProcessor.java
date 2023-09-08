package com.sentrysoftware.matrix.strategy.utils;

import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.EMPTY;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.SOURCE_REF_PATTERN;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.SOURCE_VALUE_WITH_DOLLAR_PATTERN;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.sentrysoftware.matrix.common.JobInfo;
import com.sentrysoftware.matrix.common.helpers.MatrixConstants;
import com.sentrysoftware.matrix.common.helpers.state.LinkStatus;
import com.sentrysoftware.matrix.connector.model.monitor.mapping.MappingResource;
import com.sentrysoftware.matrix.connector.model.monitor.task.Mapping;
import com.sentrysoftware.matrix.strategy.source.SourceTable;
import com.sentrysoftware.matrix.telemetry.Monitor;
import com.sentrysoftware.matrix.telemetry.Resource;
import com.sentrysoftware.matrix.telemetry.TelemetryManager;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Slf4j
public class MappingProcessor {

	private static final Pattern MEBIBYTE_2_BYTE_PATTERN = Pattern.compile("mebibyte2byte\\((.+)\\)", Pattern.CASE_INSENSITIVE);
	private static final Pattern LEGACY_LINK_STATUS_PATTERN = Pattern.compile("legacylinkstatus\\((.+)\\)", Pattern.CASE_INSENSITIVE);
	private static final double PERCENT_2_RATIO_FACTOR = 0.01;
	private static final double MEGAHERTZ_2_HERTZ_FACTOR = 1_000_000.0;
	private static final double MEBIBYTE_2_BYTE_FACTOR = 1_048_576.0;
	private static final double MEGABIT_2_BIT_FACTOR = 1_000_000.0;

	private static final String ZERO = "0";
	private static final String ONE = "1";

	private TelemetryManager telemetryManager;
	private Mapping mapping;
	private String id;
	private long collectTime;
	private List<String> row;
	private JobInfo jobInfo;

	@Default
	private Map<String, UnaryOperator<String>> lookupFunctions = new HashMap<>();

	@Default
	private Map<String, BiFunction<String, Monitor, String>> legacyPowerSupplyFunctions = new HashMap<>();

	@Default
	private Map<String, BiFunction<KeyValuePair, Monitor, String>> computationFunctions = new HashMap<>();

	/**
	 * Find the source table instance from the connector namespace.<br>
	 * If we have a hard-coded source then we will create a source wrapping the
	 * csv input.
	 * 
	 * @return {@link Optional} instance of {@link SourceTable}
	 */
	public Optional<SourceTable> lookupSourceTable() {
		final String source = mapping.getSource();

		final Matcher matcher = SOURCE_REF_PATTERN.matcher(source);

		if (matcher.find()) {
			final String sourceKey = matcher.group();
			return Optional.ofNullable(
				telemetryManager
					.getHostProperties()
					.getConnectorNamespace(jobInfo.getConnectorName())
					.getSourceTable(sourceKey)
			);
		}

		// Hard-coded source
		return Optional.of(
			SourceTable
				.builder()
				.table(SourceTable.csvToTable(source, MatrixConstants.SEMICOLON))
				.build()
		);
	}

	/**
	 * This method interprets non context mapping attributes
	 * @return Map<String, String>
	 */
	public Map<String, String> interpretNonContextMappingAttributes() {
		return interpretNonContextMapping(mapping.getAttributes());
	}

	/**
	 * This method interprets non context mapping metrics
	 * @return Map<String, String>
	 */
	public Map<String, String> interpretNonContextMappingMetrics() {
		return interpretNonContextMapping(mapping.getMetrics());
	}

	/**
	 *  This method interprets non context mapping conditional collections
	 * @return Map<String, String>
	 */
	public Map<String, String> interpretNonContextMappingConditionalCollection() {
		return interpretNonContextMapping(mapping.getConditionalCollection());
	}
	
	/**
	 * This method interprets non context mapping legacy text parameters
	 * @return Map<String, String>
	 */
	public Map<String, String> interpretNonContextMappingLegacyTextParameters() {
		return interpretNonContextMapping(mapping.getLegacyTextParameters());
	}

	/**
	 * This method interprets non context mapping.
	 * The key value pairs are filled with values depending on the column type: extraction, awk, rate, etc...
	 * @param keyValuePairs pairs of key values (for example: attribute key and attribute value)
	 * @return Map<String, String>
	 */
	public Map<String, String> interpretNonContextMapping(final Map<String, String> keyValuePairs) { // NOSONAR on cognitive complexity of 16
		if (keyValuePairs == null) {
			return Collections.emptyMap();
		}

		final Map<String, String> result = new HashMap<>();

		keyValuePairs.forEach((key, value) -> {
			if (isColumnExtraction(value)) {
				result.put(key, extractColumnValue(value, key));
			} else if (isAwkScript(value)) {
				result.put(key, executeAwkScript(value));
			} else if (isMegaBit2Bit(value)) { 
				result.put(key, megaBit2bit(value, key));
			} else if (isPercentToRatioFunction(value)) {
				result.put(key, percent2Ratio(value, key));
			} else if (isMegaHertz2HertzFunction(value)) {
				result.put(key, megaHertz2Hertz(value, key));
			} else if (isMebiByte2ByteFunction(value)) {
				result.put(key, mebiByte2Byte(value, key)); // Implemented REMOVE_ME For God's Sake
			} else if (isBooleanFunction(value)) {
				result.put(key, booleanFunction(value));
			} else if (isLegacyLedStatusFunction(value)) {
				result.put(key, legacyLedStatus(value));
			} else if (isLegacyIntrusionStatusFunction(value)) {
				result.put(key, legacyIntrusionStatus(value));
			} else if (isLegacyPredictedFailureFunction(value)) {
				result.put(key, legacyPredictedFailure(value));
			} else if (islegacyNeedsCleaningFucntion(value)) {
				result.put(key, legacyNeedsCleaning(value));
			} else if (isComputePowerShareRatioFunction(value)) {
				result.put(String.format("%s.raw", key), computePowerShareRatio(value));
			} else if (isLegacyLinkStatusFunction(value)) {
				result.put(key, legacyLinkStatusFunction(value, key));
			} else if (isLegacyFullDuplex(value)) {
				result.put(key, legacyFullDuplex(value));
			} else if (isLookupFunction(value)) {
				lookupFunctions.put(key, this::lookup);
			} else if (isLegacyPowerSupplyUtilization(value)) {
				legacyPowerSupplyFunctions.put(key, this::legacyPowerSupplyUtilization);
			} else if (isFakeCounterFunction(value)) {
				computationFunctions.put(key, this::fakeCounter);
			} else if (isRateFunction(value)) {
				computationFunctions.put(key, this::rate);
			} else {
				result.put(key, value);
			}
		});

		return result;
	}

	private String lookup(final String value) {
		final Pattern lookupValuePattern = Pattern.compile("(?<=^lookup\\().+(?=\\)$)");
		final Matcher matcher = lookupValuePattern.matcher(value.trim());
		matcher.find();

		final String[] lookupValues = matcher.group(1).split(",");
		final Map<String, Monitor> typedMonitors = telemetryManager.getMonitors().get(lookupValues[0]);

		if (typedMonitors == null) {
			log.error("No monitors found of type {}.", lookupValues[0]);
			return null;
		}

		final Stream<Monitor> monitors = typedMonitors.values().stream().filter(x -> x.getAttributes().get(lookupValues[2]).equals(lookupValues[3]));
		Monitor monitor = monitors.findFirst().orElse(null);

		if (monitor == null) {
			log.error("No monitors found matching attribute {} with value {}.", lookupValues[2], lookupValues[3]);
			return null;
		}

		return monitor.getAttributes().get(lookupValues[1]);
	}

	private String legacyPowerSupplyUtilization(final String value, final Monitor monitor) {
		return null;
	}

	private String fakeCounter(final KeyValuePair keyValuePair, final Monitor monitor) {
		return null;
	}

	private String rate(final KeyValuePair keyValuePair, final Monitor monitor) {
		return null;
	}

	private boolean isFakeCounterFunction(String value) {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean isLegacyPowerSupplyUtilization(String value) {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean isLookupFunction(String value) {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean isRateFunction(String value) {
		// TODO Auto-generated method stub
		return false;
	}

	private String megaBit2bit(String value, String key) {
		return multiplyValueByFactor(value, key, MEGABIT_2_BIT_FACTOR);
	}

	private boolean isMegaBit2Bit(String value) {
		// TODO Auto-generated method stub
		return false;
	}

	private String legacyFullDuplex(String value) {
		if ("legacyFullDuplex(ok)".equals(value)) {
			return ONE;
		}

		return ZERO;
	}

	private boolean isLegacyFullDuplex(String value) {
		// TODO Auto-generated method stub
		return false;
	}

	private String legacyLinkStatusFunction(String value, String key) {
		final Matcher matcher = LEGACY_LINK_STATUS_PATTERN.matcher(value);
		matcher.find();

		final String extracted = matcher.group(1);
		String extractedValue = extracted;
		if (isColumnExtraction(extracted)) {
			extractedValue = extractColumnValue(extracted, key);
		}

		final Optional<LinkStatus> maybeLinkStatus = LinkStatus.interpret(extractedValue);

		if (maybeLinkStatus.isPresent()) {
			return String.valueOf(maybeLinkStatus.get().getNumericValue());
		}

		// TODO add log
		return null;
	}

	private boolean isLegacyLinkStatusFunction(String value) {
		// TODO Auto-generated method stub
		return false;
	}

	private String computePowerShareRatio(String value) {
		// TODO Auto-generated method stub
		return null;
	}

	private boolean isComputePowerShareRatioFunction(String value) {
		// TODO Auto-generated method stub
		return false;
	}

	private String legacyNeedsCleaning(String value) {
		if ("legacyNeedsCleaning(0)".equals(value)) {
			return ZERO;
		}

		return ONE;
	}

	private boolean islegacyNeedsCleaningFucntion(String value) {
		// TODO Auto-generated method stub
		return false;
	}

	private String legacyPredictedFailure(String value) {
		if ("legacyPredictedFailure(0)".equals(value) || "legacyPredictedFailure(false)".equals(value)) {
			return ZERO;
		}

		return ONE;
	}

	private boolean isLegacyPredictedFailureFunction(String value) {
		// TODO Auto-generated method stub
		return false;
	}

	private String legacyIntrusionStatus(String value) {
		if ("legacyIntrusionStatus(0)".equals(value) || "legacyIntrusionStatus(false)".equals(value)) {
			return ZERO;
		}

		return ONE;
	}

	private boolean isLegacyIntrusionStatusFunction(String value) {
		// TODO Auto-generated method stub
		return false;
	}

	private String legacyLedStatus(String value) {
		// TODO Auto-generated method stub
		return null;
	}

	private boolean isLegacyLedStatusFunction(String value) {
		// TODO Auto-generated method stub
		return false;
	}

	private String booleanFunction(String value) {
		if ("boolean(1)".equals(value) || "boolean(true)".equals(value)) {
			return ONE;
		}

		return ZERO;
	}

	private boolean isBooleanFunction(String value) {
		// TODO Auto-generated method stub
		return false;
	}

	private String mebiByte2Byte(String value, String key) {
		final Matcher matcher = MEBIBYTE_2_BYTE_PATTERN.matcher(value);
		matcher.find();

		final String extracted = matcher.group(1);

		if (isColumnExtraction(extracted)) {
			return multiplyValueByFactor(extractColumnValue(extracted, key), key, MEBIBYTE_2_BYTE_FACTOR);
		} else {
			return multiplyValueByFactor(extracted, key, MEBIBYTE_2_BYTE_FACTOR);
		}
	}

	private boolean isMebiByte2ByteFunction(String value) {
		// TODO Auto-generated method stub
		return false;
	}

	private String megaHertz2Hertz(String value, String key) {
		return multiplyValueByFactor(value, key, MEGAHERTZ_2_HERTZ_FACTOR);
	}

	private boolean isMegaHertz2HertzFunction(String value) {
		// TODO Auto-generated method stub
		return false;
	}

	private String percent2Ratio(String value, String key) {
		return multiplyValueByFactor(value, key, PERCENT_2_RATIO_FACTOR);
	}

	private boolean isPercentToRatioFunction(String value) {
		// TODO Auto-generated method stub
		return false;
	}

	private String executeAwkScript(String value) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * We multiply the value by a predetermined factor, usually for unit conversion
	 * 
	 * @param value		A string with an already extracted value
	 * @param key 
	 * @param factor	Double value to be multiplied to the value
	 * @return			A String containing only the new value
	 */
	private String multiplyValueByFactor(final String value, String key, final double factor) {

		try {
			double doubleValue = Double.parseDouble(value);
			return Double.toString(doubleValue * factor);
		} catch (Exception e) {
			log.error("Hostname {} - ....", jobInfo.getHostname());
			log.debug("Hostname {} - Exception: ", jobInfo.getHostname(), e);
			return null;
		}

	}

	/**
	 * This method extracts column value using a Regex
	 * @param value
	 * @param key
	 * @return string representing the column value
	 */
	private String extractColumnValue(final String value, final String key) {
		final Matcher matcher = getStringRegexMatcher(value);
		matcher.find();
		final int columnIndex = Integer.parseInt(matcher.group(1)) - 1;
		if (columnIndex >= 0 && columnIndex < row.size()) {
			return row.get(columnIndex);
		} else {
			log.warn(
				"Hostname {} - Column number {} is invalid for the source {}. Column number should not exceed the size of the row. key {} - " +
					"Row {} - monitor type {}.",
				jobInfo.getHostname(),
				columnIndex,
				mapping.getSource(),
				key,
				row,
				jobInfo.getMonitorType()
			);
			return EMPTY;
		}
	}

	private boolean isAwkScript(String value) {
		// TODO Auto-generated method stub
		return false;
	}

	/**
	 * This method checks whether a column contains a value to be extracted (e.g: $1, $2, etc ...)
	 * @param value
	 * @return true or false
	 */
	private boolean isColumnExtraction(String value) {
		return getStringRegexMatcher(value).find();
	}

	/**
	 * This method returns the matcher of a regex on a given string value
	 * @param value
	 * @return Matcher
	 */
	private Matcher getStringRegexMatcher(String value) {
		return SOURCE_VALUE_WITH_DOLLAR_PATTERN.matcher(value);
	}

	/**
	 * This method interprets mapping instance mapping resource field
	 * @return Resource
	 */
	public Resource interpretMappingResource() {

		final MappingResource mappingResource = mapping.getResource();

		if (mappingResource != null && mappingResource.hasType()) {
			return Resource.builder()
				.type(
					interpretNonContextMapping(Map.of("type", mappingResource.getType()))
					.get("type")
				)
				.attributes(interpretNonContextMapping(mappingResource.getAttributes()))
				.build();
		}

		return null;
	}

	/**
	 * This method interprets context mapping attributes
	 * @param monitor a given monitor
	 * @return Map<String, String>
	 */
	public Map<String, String> interpretContextMappingAttributes(final Monitor monitor) {
		return interpretContextKeyValuePairs(monitor, mapping.getAttributes());
	}

	/**
	 * This method interprets context mapping metrics
	 * @param monitor a given monitor
	 * @return Map<String, String>
	 */
	public Map<String, String> interpretContextMappingMetrics(final Monitor monitor) {
		return interpretContextKeyValuePairs(monitor, mapping.getMetrics());
	}

	/**
	 * This method interprets context mapping conditional collections
	 * @param monitor a given monitor
	 * @return Map<String, String>
	 */
	public Map<String, String> interpretContextMappingConditionalCollection(final Monitor monitor) {
		return interpretContextKeyValuePairs(monitor, mapping.getConditionalCollection());
	}


	/**
	 * This method interprets context mapping legacy text parameters
	 * @param monitor a given monitor
	 * @return Map<String, String>
	 */
	public Map<String, String> interpretContextMappingLegacyTextParameters(final Monitor monitor) {
		return interpretContextKeyValuePairs(monitor, mapping.getLegacyTextParameters());
	}


	/**
	 * This method interprets context key value pairs
	 * @param monitor a given monitor
	 * @param keyValuePairs key value pairs (for example: attribute key and attribute value)
	 * @return Map<String, String>
	 */
	private Map<String, String> interpretContextKeyValuePairs(final Monitor monitor, final Map<String, String> keyValuePairs) {
		if (keyValuePairs== null) {
			return Collections.emptyMap();
		}

		final Map<String, String> result = new HashMap<>();
		lookupFunctions
			.entrySet()
			.forEach(entry -> {
				final String attributeKey = entry.getKey();
				result.put(
					attributeKey,
					entry.getValue().apply(keyValuePairs.get(attributeKey))
				);
			});

		legacyPowerSupplyFunctions
			.entrySet()
			.forEach(entry -> {
				final String attributeKey = entry.getKey();
				result.put(
					attributeKey,
					entry.getValue().apply(keyValuePairs.get(attributeKey), monitor)
				);
			});

		computationFunctions
			.entrySet()
			.forEach(entry -> {
				final String attributeKey = entry.getKey();
				result.put(
					attributeKey,
					entry.getValue().apply(new KeyValuePair(attributeKey, keyValuePairs.get(attributeKey)), monitor)
				);
			});

		lookupFunctions.clear();
		legacyPowerSupplyFunctions.clear();
		computationFunctions.clear();

		return result;
	}

	@Data
	@Builder
	static class KeyValuePair {
		String key;
		String value;
	}
}
