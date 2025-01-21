package org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source;

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

import static com.fasterxml.jackson.annotation.Nulls.FAIL;
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.sentrysoftware.metricshub.engine.common.helpers.StringHelper.addNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.sentrysoftware.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.sentrysoftware.metricshub.engine.strategy.source.ISourceProcessor;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceTable;

/**
 * Represents a source that executes an jawk script to retrieve data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder
public class JawkSource extends Source {

	private static final long serialVersionUID = 1L;

	/**
	 * The JAWK script to be executed for the computation task.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private String script;

	/**
	 * The input on which to execute the JAWK task.
	 */
	@JsonSetter(nulls = SKIP)
	private String input;

	/**
	 * The separators parameter for the JAWK task.
	 */
	private String separators;

	/**
	 * Builder for creating instances of {@code JawkSource}.
	 *
	 * @param type                  The type of the source.
	 * @param computes              List of computations to be applied to the source.
	 * @param forceSerialization    Flag indicating whether to force serialization.
	 * @param key                   The key associated with the source.
	 * @param executeForEachEntryOf The execution context for each entry of the source.
	 * @param script                The script to execute.
	 * @param input                 The input on which to execute the JAWK task.
	 * @param separators            The separators parameter for the JAWK task.
	 */
	@Builder
	@JsonCreator
	public JawkSource(
		@JsonProperty(value = "type") String type,
		@JsonProperty(value = "computes") List<Compute> computes,
		@JsonProperty(value = "forceSerialization") boolean forceSerialization,
		@JsonProperty(value = "key") String key,
		@JsonProperty(value = "executeForEachEntryOf") ExecuteForEachEntryOf executeForEachEntryOf,
		@JsonProperty(value = "script") String script,
		@JsonProperty(value = "input") final String input,
		@JsonProperty(value = "separators") final String separators
	) {
		super(type, computes, forceSerialization, key, executeForEachEntryOf);
		this.script = script;
		this.input = input;
		this.separators = separators;
	}

	@Override
	public Source copy() {
		return JawkSource
			.builder()
			.type(type)
			.key(key)
			.forceSerialization(forceSerialization)
			.computes(getComputes() != null ? new ArrayList<>(getComputes()) : null)
			.executeForEachEntryOf(executeForEachEntryOf != null ? executeForEachEntryOf.copy() : null)
			.script(script)
			.input(input)
			.separators(separators)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		// For now, there is nothing to update
	}

	@Override
	public SourceTable accept(ISourceProcessor sourceProcessor) {
		return sourceProcessor.process(this);
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- script=", script);
		addNonNull(stringJoiner, "- input=", input);
		addNonNull(stringJoiner, "- separators=", separators);

		return stringJoiner.toString();
	}
}
