package com.sentrysoftware.matrix.strategy.source;

import static com.sentrysoftware.matrix.constants.Constants.ENCLOSURE_COLLECT_SOURCE_1;
import static com.sentrysoftware.matrix.constants.Constants.EXPECTED_RESULT;
import static com.sentrysoftware.matrix.constants.Constants.EXPECTED_VAL_1;
import static com.sentrysoftware.matrix.constants.Constants.EXPECTED_VAL_1_AND_2;
import static com.sentrysoftware.matrix.constants.Constants.EXPECTED_VAL_1_AND_2_ARRAY;
import static com.sentrysoftware.matrix.constants.Constants.EXPECTED_VAL_2;
import static com.sentrysoftware.matrix.constants.Constants.LOCALHOST;
import static com.sentrysoftware.matrix.constants.Constants.MONITOR_ID_ATTRIBUTE_VALUE;
import static com.sentrysoftware.matrix.constants.Constants.MY_CONNECTOR_1_NAME;
import static com.sentrysoftware.matrix.constants.Constants.PASSWORD;
import static com.sentrysoftware.matrix.constants.Constants.URL;
import static com.sentrysoftware.matrix.constants.Constants.USERNAME;
import static com.sentrysoftware.matrix.constants.Constants.VALUE_A1;
import static com.sentrysoftware.matrix.constants.Constants.VALUE_B1;
import static com.sentrysoftware.matrix.constants.Constants.VALUE_C1;
import static com.sentrysoftware.matrix.constants.Constants.VALUE_VAL1;
import static com.sentrysoftware.matrix.constants.Constants.VALUE_VAL2;
import static com.sentrysoftware.matrix.constants.Constants.VALUE_VAL3;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sentrysoftware.matrix.configuration.HostConfiguration;
import com.sentrysoftware.matrix.configuration.HttpConfiguration;
import com.sentrysoftware.matrix.connector.model.common.DeviceKind;
import com.sentrysoftware.matrix.connector.model.common.EntryConcatMethod;
import com.sentrysoftware.matrix.connector.model.common.ExecuteForEachEntryOf;
import com.sentrysoftware.matrix.connector.model.common.HttpMethod;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.HttpSource;
import com.sentrysoftware.matrix.telemetry.ConnectorNamespace;
import com.sentrysoftware.matrix.telemetry.HostProperties;
import com.sentrysoftware.matrix.telemetry.TelemetryManager;

@ExtendWith(MockitoExtension.class)
class SourceUpdaterProcessorTest {

	@Mock
	private ISourceProcessor sourceProcessor;

	@Test
	void testProcessHTTPSource() {
		final HttpConfiguration httpConfiguration = HttpConfiguration.builder()
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.port(161)
			.timeout(120L)
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(LOCALHOST)
			.hostId(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(HttpConfiguration.class, httpConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(hostConfiguration)
			.build();

		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(HttpSource.class));
		assertEquals(SourceTable.empty(),
			new SourceUpdaterProcessor(sourceProcessor, telemetryManager, MY_CONNECTOR_1_NAME, MONITOR_ID_ATTRIBUTE_VALUE)
				.process(HttpSource.builder().url(URL).method(HttpMethod.GET).build()));

		final HttpSource httpSource = HttpSource.builder().url(URL).build();
		httpSource.setExecuteForEachEntryOf(ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).build());

		final SourceTable sourceTable = SourceTable.builder()
			.table(Arrays.asList(
				Arrays.asList(VALUE_VAL1, VALUE_VAL2, VALUE_VAL3),
				Arrays.asList(VALUE_A1, VALUE_B1, VALUE_C1)))
			.build();

		final HostProperties hostProperties = HostProperties
			.builder()
			.build();

		hostProperties
			.getConnectorNamespace(MY_CONNECTOR_1_NAME)
			.addSourceTable(ENCLOSURE_COLLECT_SOURCE_1, sourceTable);

		telemetryManager.setHostProperties(hostProperties);

		final SourceTable expected1 = SourceTable.builder().rawData(EXPECTED_VAL_1).build();
		final SourceTable expected2 = SourceTable.builder().rawData(EXPECTED_VAL_2).build();
		doReturn(expected1, expected2).when(sourceProcessor).process(any(HttpSource.class));
		SourceTable result = new SourceUpdaterProcessor(sourceProcessor, telemetryManager, MY_CONNECTOR_1_NAME, MONITOR_ID_ATTRIBUTE_VALUE).process(httpSource);
		assertEquals(EXPECTED_VAL_1_AND_2, result.getRawData());

		httpSource.setExecuteForEachEntryOf(ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).concatMethod(EntryConcatMethod.LIST).build());
		doReturn(expected1, expected2).when(sourceProcessor).process(any(HttpSource.class));
		result = new SourceUpdaterProcessor(sourceProcessor, telemetryManager, MY_CONNECTOR_1_NAME, MONITOR_ID_ATTRIBUTE_VALUE).process(httpSource);
		assertEquals(EXPECTED_VAL_1_AND_2, result.getRawData());

		httpSource.setExecuteForEachEntryOf(ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).concatMethod(EntryConcatMethod.JSON_ARRAY).build());
		doReturn(expected1, expected2).when(sourceProcessor).process(any(HttpSource.class));
		result = new SourceUpdaterProcessor(sourceProcessor, telemetryManager, MY_CONNECTOR_1_NAME, MONITOR_ID_ATTRIBUTE_VALUE).process(httpSource);
		assertEquals(EXPECTED_VAL_1_AND_2_ARRAY, result.getRawData());

		httpSource.setExecuteForEachEntryOf(ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).concatMethod(EntryConcatMethod.JSON_ARRAY_EXTENDED).build());
		doReturn(expected1, expected2).when(sourceProcessor).process(any(HttpSource.class));
		result = new SourceUpdaterProcessor(sourceProcessor, telemetryManager, MY_CONNECTOR_1_NAME, MONITOR_ID_ATTRIBUTE_VALUE).process(httpSource);

		assertEquals(EXPECTED_RESULT, result.getRawData());
	}
}
