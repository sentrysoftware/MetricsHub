package com.sentrysoftware.matrix.agent.config;

import static com.fasterxml.jackson.annotation.Nulls.SKIP;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.sentrysoftware.matrix.agent.deserialization.AttributesDeserializer;
import com.sentrysoftware.matrix.agent.deserialization.TimeDeserializer;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResourceGroupConfig {

	private String loggerLevel;
	private String outputDirectory;

	@JsonDeserialize(using = TimeDeserializer.class)
	private Long collectPeriod;

	private Integer discoveryCycle;
	private AlertingSystemConfig alertingSystemConfig;
	private Boolean sequential;
	private Boolean resolveHostnameToFqdn;
	private Long jobTimeout;

	@Default
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = AttributesDeserializer.class)
	private Map<String, String> attributes = new HashMap<>();

	@Default
	@JsonSetter(nulls = SKIP)
	private Map<String, Double> metrics = new HashMap<>();

	@Default
	@JsonSetter(nulls = SKIP)
	private Map<String, ResourceConfig> resources = new HashMap<>();
}
