package com.sentrysoftware.matrix.connector.parser.state.detection;

import com.sentrysoftware.matrix.connector.parser.state.IConnectorStateParser;
import com.sentrysoftware.matrix.connector.parser.state.detection.http.ConnectorHttpProperty;
import com.sentrysoftware.matrix.connector.parser.state.detection.snmp.ConnectorSnmpProperty;
import com.sentrysoftware.matrix.connector.parser.state.detection.wbem.ConnectorWbemProperty;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConnectorDetectionProperty {

	private ConnectorDetectionProperty() {}

	public static Set<IConnectorStateParser> getConnectorProperties() {

		return Stream
			.of(
				ConnectorSnmpProperty.getConnectorProperties(),
				ConnectorWbemProperty.getConnectorProperties(),
				ConnectorHttpProperty.getConnectorProperties())
			.flatMap(Set::stream)
			.collect(Collectors.toSet());
	}
}