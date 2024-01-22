package com.sentrysoftware.metricshub.hardware.it;

import com.sentrysoftware.metricshub.engine.client.ClientsExecutor;
import com.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import com.sentrysoftware.metricshub.engine.configuration.SnmpConfiguration;
import com.sentrysoftware.metricshub.engine.configuration.SnmpConfiguration.SnmpVersion;
import com.sentrysoftware.metricshub.engine.connector.model.ConnectorStore;
import com.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import com.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import com.sentrysoftware.metricshub.hardware.it.job.SnmpITJob;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DellOpenManageIT {
	static {
		Locale.setDefault(Locale.US);
	}

	private static final String CONNECTOR_ID = "DellOpenManage";
	private static final Path CONNECTOR_DIRECTORY = Paths.get(
		"src",
		"it",
		"resources",
		"snmp",
		"DellOpenManageIT",
		"connectors"
	);
	private static final String LOCALHOST = "localhost";

	private static TelemetryManager telemetryManager;
	private static ClientsExecutor clientsExecutor;

	@BeforeAll
	static void setUp() throws Exception {
		final SnmpConfiguration snmpConfiguration = SnmpConfiguration
			.builder()
			.community("public")
			.version(SnmpVersion.V1)
			.timeout(120L)
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(LOCALHOST)
			.hostname(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.selectedConnectors(Set.of(CONNECTOR_ID))
			.configurations(Map.of(SnmpConfiguration.class, snmpConfiguration))
			.build();

		final ConnectorStore connectorStore = new ConnectorStore(CONNECTOR_DIRECTORY);

		telemetryManager =
			TelemetryManager.builder().connectorStore(connectorStore).hostConfiguration(hostConfiguration).build();

		clientsExecutor = new ClientsExecutor(telemetryManager);
	}

	@Test
	void test() throws Exception {
		new SnmpITJob(clientsExecutor, telemetryManager)
			.withServerRecordData("snmp/DellOpenManageIT/input/input.snmp")
			.executeDiscoveryStrategy()
			.executeCollectStrategy()
			.verifyExpected("snmp/DellOpenManageIT/expected/expected.json");
	}
}
