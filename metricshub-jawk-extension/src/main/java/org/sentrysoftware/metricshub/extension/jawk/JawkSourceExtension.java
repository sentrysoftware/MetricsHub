package org.sentrysoftware.metricshub.extension.jawk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Jawk Extension
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

import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.FILE_PATTERN;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.sentrysoftware.jawk.backend.AVM;
import org.sentrysoftware.jawk.ext.JawkExtension;
import org.sentrysoftware.jawk.frontend.AwkParser;
import org.sentrysoftware.jawk.frontend.AwkSyntaxTree;
import org.sentrysoftware.jawk.intermediate.AwkTuples;
import org.sentrysoftware.jawk.util.AwkSettings;
import org.sentrysoftware.jawk.util.ScriptSource;
import org.sentrysoftware.metricshub.engine.awk.UniformPrintStream;
import org.sentrysoftware.metricshub.engine.common.helpers.LoggingHelper;
import org.sentrysoftware.metricshub.engine.connector.model.common.EmbeddedFile;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.Source;
import org.sentrysoftware.metricshub.engine.extension.ICompositeSourceScriptExtension;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceProcessor;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceTable;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceUpdaterProcessor;
import org.sentrysoftware.metricshub.engine.strategy.utils.EmbeddedFileHelper;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

/**
 * This class implements the {@link ICompositeSourceScriptExtension} contract, reports the supported features,
 * processes HTTP sources and criteria.
 */
@Slf4j
public class JawkSourceExtension implements ICompositeSourceScriptExtension {

	// script to AwkTuple
	private static final Map<String, AwkTuples> AWK_CODE_MAP = new ConcurrentHashMap<>();

	// host to Map<String, JawkExtension>
	private static final Map<String, Map<String, Map<String, JawkExtension>>> EXTENSIONS_MAP = new ConcurrentHashMap<>();

	@Override
	public boolean isValidSource(final Source source) {
		return source instanceof JawkSource;
	}

	@Override
	public SourceTable processSource(
		final Source source,
		final String connectorId,
		final TelemetryManager telemetryManager,
		final SourceProcessor sourceProcessor
	) {
		final String hostname = telemetryManager.getHostname();

		if (source == null) {
			log.warn("Hostname {} - Awk Source cannot be null, the Awk operation will return an empty result.", hostname);
			return SourceTable.empty();
		}

		if (!(source instanceof JawkSource jawkSource)) {
			log.warn("Hostname {} - Awk Source is invalid, the Awk operation will return an empty result.", hostname);
			return SourceTable.empty();
		}

		final String script = jawkSource.getScript();

		// An Awk Script is supposed to be only the reference to the EmbeddedFile, so the map contains only one item which is our EmbeddedFile
		final Optional<EmbeddedFile> maybeEmbeddedFile;

		if (!FILE_PATTERN.matcher(script).find()) {
			maybeEmbeddedFile = Optional.of(EmbeddedFile.fromString(script));
		} else {
			try {
				maybeEmbeddedFile =
					EmbeddedFileHelper.findEmbeddedFile(
						script,
						telemetryManager.getEmbeddedFiles(connectorId),
						hostname,
						connectorId
					);
			} catch (Exception exception) {
				log.warn("Hostname {} - Awk Operation script {} has not been set correctly.", hostname, script);
				return SourceTable.empty();
			}
		}

		if (maybeEmbeddedFile.isEmpty()) {
			log.warn("Hostname {} - Awk Operation script {} embedded file can't be found.", hostname, script);
			return SourceTable.empty();
		}

		final EmbeddedFile embeddedFile = maybeEmbeddedFile.get();
		final String awkScript = embeddedFile.getContentAsString();

		log.debug("Hostname {} - Awk Operation. AWK Script:\n{}\n", hostname, awkScript);

		final AwkSettings settings = new AwkSettings();

		final String input = jawkSource.getInput();
		if (input != null && !input.isEmpty()) {
			final String inputContent = SourceUpdaterProcessor.replaceSourceReferenceContent(
				input,
				telemetryManager,
				connectorId,
				"Awk",
				source.getKey()
			);

			if (inputContent != null && !inputContent.isEmpty()) {
				settings.setInput(new ByteArrayInputStream(inputContent.getBytes(StandardCharsets.UTF_8)));
			}
		}

		// Create the OutputStream
		final ByteArrayOutputStream resultBytesStream = new ByteArrayOutputStream();
		final UniformPrintStream resultStream = new UniformPrintStream(resultBytesStream);
		settings.setOutputStream(resultStream);

		// We don't want to see error messages because of formatting issues
		settings.setCatchIllegalFormatExceptions(true);

		// We force \n as the Record Separator (RS) because even if running on Windows
		// we're passing Java strings, where end of lines are simple \n
		settings.setDefaultRS("\n");

		final String hostId = telemetryManager.getHostConfiguration().getHostId();
		final MetricsHubExtensionForJawk metricsHubExtensionForJawk = MetricsHubExtensionForJawk
			.builder()
			.sourceProcessor(sourceProcessor)
			.hostname(telemetryManager.getHostname())
			.connectorId(connectorId)
			.build();

		final Map<String, Map<String, JawkExtension>> connectorMap = EXTENSIONS_MAP.computeIfAbsent(
			hostId,
			id -> new ConcurrentHashMap<>()
		);

		final Map<String, JawkExtension> extensions = connectorMap.computeIfAbsent(
			connectorId,
			id ->
				Arrays
					.stream(metricsHubExtensionForJawk.extensionKeywords())
					.collect(Collectors.toMap(key -> key, key -> metricsHubExtensionForJawk))
		);

		// Interpret
		final AVM avm = new AVM(settings, extensions);

		try {
			final AwkTuples tuple = AWK_CODE_MAP.computeIfAbsent(awkScript, code -> getIntermediateCode(code, extensions));
			avm.interpret(tuple);

			// Result
			final SourceTable sourceTable = new SourceTable();
			sourceTable.setRawData(resultBytesStream.toString());
			sourceTable.setTable(SourceTable.csvToTable(sourceTable.getRawData(), TABLE_SEP));
			return sourceTable;
		} catch (Exception e) {
			LoggingHelper.logSourceError(connectorId, source.getKey(), "AwkSource script", hostname, e);
			return SourceTable.empty();
		}
	}

	/**
	 * Generates the "Awk Tuples", i.e. the intermediate Awk code
	 * that can be interpreted afterward.
	 *
	 * @param script Awk script source code to be converted to intermediate code
	 * @param extensions The extensions to be used by the {@link AwkParser}
	 * @return The actual AwkTuples to be interpreted
	 * @throws JawkSourceExtensionRuntimeException when the Awk script is wrong or an error occurs during the parsing
	 */
	public static AwkTuples getIntermediateCode(final String script, final Map<String, JawkExtension> extensions)
		throws JawkSourceExtensionRuntimeException {
		// All scripts need to be prefixed with an extra statement that sets the Record Separator (RS)
		// to the "normal" end-of-line (\n), because Jawk uses line.separator System property, which
		// is \r\n on Windows, thus preventing it from splitting lines properly.
		final ScriptSource awkHeader = new ScriptSource("Header", new StringReader("BEGIN { ORS = RS = \"\\n\"; }"), false);
		final ScriptSource awkSource = new ScriptSource("Body", new StringReader(script), false);
		final List<ScriptSource> sourceList = new ArrayList<>();
		sourceList.add(awkHeader);
		sourceList.add(awkSource);

		// Parse the Awk script
		final AwkTuples tuples = new AwkTuples();
		final AwkParser parser = new AwkParser(false, false, extensions);
		final AwkSyntaxTree ast;
		try {
			ast = parser.parse(sourceList);

			// Produce the intermediate code
			if (ast != null) {
				// 1st pass to tie actual parameters to back-referenced formal parameters
				ast.semanticAnalysis();

				// 2nd pass to tie actual parameters to forward-referenced formal parameters
				ast.semanticAnalysis();
				if (ast.populateTuples(tuples) != 0) {
					throw new ParseException("Syntax problem with the Awk script", 0);
				}
				tuples.postProcess();
				parser.populateGlobalVariableNameToOffsetMappings(tuples);
			}
		} catch (Exception e) {
			throw new JawkSourceExtensionRuntimeException(e.getMessage(), e);
		}

		return tuples;
	}
}
