package com.sentrysoftware.matrix.converter.state.computes.awk;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sentrysoftware.matrix.converter.state.IConnectorStateConverter;
import com.sentrysoftware.matrix.converter.state.computes.common.ComputeTypeProcessor;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ConnectorAwkProperty {

	private static final String HDF_TYPE_VALUE = "Awk";
	private static final String YAML_TYPE_VALUE = "awk";

	public static Set<IConnectorStateConverter> getConnectorProperties() {

		return Stream.of(
			new ComputeTypeProcessor(HDF_TYPE_VALUE, YAML_TYPE_VALUE),
			new AwkScriptProcessor(),
			new KeepOnlyRegExpProcessor(),
			new ExcludeRegExpProcessor(),
			new SelectColumnsProcessor(),
			new SeparatorsProcessor()
		)
		.collect(Collectors.toSet());
	}
}