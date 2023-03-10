package com.sentrysoftware.matrix.connector.model;

import static com.fasterxml.jackson.annotation.Nulls.SKIP;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.sentrysoftware.matrix.connector.deserializer.custom.EmbeddedFilesDeserializer;
import com.sentrysoftware.matrix.connector.deserializer.custom.ExtendsDeserializer;
import com.sentrysoftware.matrix.connector.deserializer.custom.SourcesDeserializer;
import com.sentrysoftware.matrix.connector.model.common.TranslationTable;
import com.sentrysoftware.matrix.connector.model.identity.ConnectorIdentity;
import com.sentrysoftware.matrix.connector.model.metric.MetricDefinition;
import com.sentrysoftware.matrix.connector.model.monitor.MonitorJob;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.Source;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Connector implements Serializable {

	private static final long serialVersionUID = 1L;

	@JsonProperty("connector")
	private ConnectorIdentity connectorIdentity;

	@JsonProperty("extends")
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = ExtendsDeserializer.class)
	@Default
	private Set<String> extendsConnectors = new LinkedHashSet<>();

	@Default
	private Map<String, MetricDefinition> metrics = new HashMap<>();

	@Default
	@JsonSetter(nulls = SKIP)
	private Map<String, String> constants = new HashMap<>();

	@Default
	@JsonSetter(nulls = SKIP)
	private Set<String> sudoCommands = new HashSet<>();

	@Default
	@JsonDeserialize(using = SourcesDeserializer.class)
	@JsonSetter(nulls = SKIP)
	private Map<String, Source> pre = new HashMap<>();

	@Default
	private Map<String, MonitorJob> monitors = new LinkedHashMap<>();

	@Default
	@JsonDeserialize(using = EmbeddedFilesDeserializer.class)
	@JsonSetter(nulls = SKIP)
	private Map<String, String> embedded = new HashMap<>();

	@Default
	@JsonSetter(nulls = SKIP)
	private Map<String, TranslationTable> translations = new HashMap<>();

	@Default
	private Set<Class <? extends Source>> sourceTypes = new HashSet<>();

	@Default
	private List<Set<String>> preSourceDep = new ArrayList<>();

	/**
	 * Get the connector identity and create it if it is not created yet.
	 */
	public ConnectorIdentity getOrCreateConnectorIdentity() {
		if (connectorIdentity == null) {
			connectorIdentity = new ConnectorIdentity();
		}

		return connectorIdentity;
	}
}
