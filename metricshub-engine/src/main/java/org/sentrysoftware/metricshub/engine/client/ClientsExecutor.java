package org.sentrysoftware.metricshub.engine.client;

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

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.sentrysoftware.jflat.JFlat;
import org.sentrysoftware.metricshub.engine.awk.AwkException;
import org.sentrysoftware.metricshub.engine.awk.AwkExecutor;
import org.sentrysoftware.metricshub.engine.common.exception.ClientException;
import org.sentrysoftware.metricshub.engine.common.helpers.LoggingHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.StringHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.TextTableHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.ThreadHelper;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.TransportProtocols;
import org.sentrysoftware.metricshub.engine.configuration.WinRmConfiguration;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import org.sentrysoftware.tablejoin.TableJoin;
import org.sentrysoftware.winrm.WinRMHttpProtocolEnum;
import org.sentrysoftware.winrm.WindowsRemoteCommandResult;
import org.sentrysoftware.winrm.command.WinRMCommandExecutor;
import org.sentrysoftware.winrm.service.client.auth.AuthenticationEnum;
import org.sentrysoftware.winrm.wql.WinRMWqlExecutor;
import org.sentrysoftware.xflat.XFlat;
import org.sentrysoftware.xflat.exceptions.XFlatException;

/**
 * The ClientsExecutor class provides utility methods for executing
 * various operations through Clients. It includes functionalities for executing
 * computations and running scripts. The execution is done on utilities like
 * AWK, JFlat, TableJoin and XFlat are supported.
 */
@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientsExecutor {

	private static final long JSON_2_CSV_TIMEOUT = 60; //seconds

	private TelemetryManager telemetryManager;

	/**
	 * Execute TableJoin
	 *
	 * @param leftTable              The left table.
	 * @param rightTable             The right table.
	 * @param leftKeyColumnNumber    The column number for the key in the left table.
	 * @param rightKeyColumnNumber   The column number for the key in the right table.
	 * @param defaultRightLine       The default line for the right table.
	 * @param wbemKeyType            {@code true} if WBEM.
	 * @param caseInsensitive        {@code true} for case-insensitive comparison.
	 * @return The result of the table join operation.
	 */
	public List<List<String>> executeTableJoin(
		final List<List<String>> leftTable,
		final List<List<String>> rightTable,
		final int leftKeyColumnNumber,
		final int rightKeyColumnNumber,
		final List<String> defaultRightLine,
		final boolean wbemKeyType,
		boolean caseInsensitive
	) {
		LoggingHelper.trace(() ->
			log.trace(
				"Executing Table Join request:\n- Left-table:\n{}\n- Right-table:\n{}\n",
				TextTableHelper.generateTextTable(leftTable),
				TextTableHelper.generateTextTable(rightTable)
			)
		);

		List<List<String>> result = TableJoin.join(
			leftTable,
			rightTable,
			leftKeyColumnNumber,
			rightKeyColumnNumber,
			defaultRightLine,
			wbemKeyType,
			caseInsensitive
		);

		LoggingHelper.trace(() ->
			log.trace(
				"Executed Table Join request:\n- Left-table:\n{}\n- Right-table:\n{}\n- Result:\n{}\n",
				TextTableHelper.generateTextTable(leftTable),
				TextTableHelper.generateTextTable(rightTable),
				TextTableHelper.generateTextTable(result)
			)
		);

		return result;
	}

	/**
	 * Call AwkExecutor in order to execute the Awk script on the given input
	 *
	 * @param embeddedFileScript The embedded file script.
	 * @param input              The input for the Awk script.
	 * @return The result of executing the Awk script.
	 * @throws AwkException if an error occurs during Awk script execution.
	 */
	@WithSpan("AWK")
	public String executeAwkScript(
		@SpanAttribute("awk.script") String embeddedFileScript,
		@SpanAttribute("awk.input") String input
	) throws AwkException {
		if (embeddedFileScript == null || input == null) {
			return null;
		}

		return AwkExecutor.executeAwk(embeddedFileScript, input);
	}

	/**
	 * Execute JSON to CSV operation.
	 *
	 * @param jsonSource    The JSON source string.
	 * @param jsonEntryKey  The JSON entry key.
	 * @param propertyList  The list of properties.
	 * @param separator     The separator for CSV.
	 * @return The CSV representation of the JSON.
	 * @throws TimeoutException       If the execution times out.
	 * @throws ExecutionException     If an execution exception occurs.
	 * @throws InterruptedException  If the execution is interrupted.
	 */
	public String executeJson2Csv(String jsonSource, String jsonEntryKey, List<String> propertyList, String separator)
		throws InterruptedException, ExecutionException, TimeoutException {
		LoggingHelper.trace(() ->
			log.trace(
				"Executing JSON to CSV conversion:\n- Json-source:\n{}\n- Json-entry-key: {}\n" + // NOSONAR
				"- Property-list: {}\n- Separator: {}\n",
				jsonSource,
				jsonEntryKey,
				propertyList,
				separator
			)
		);

		final String hostname = telemetryManager.getHostConfiguration().getHostname();

		final Callable<String> jflatToCSV = () -> {
			try {
				JFlat jsonFlat = new JFlat(jsonSource);

				jsonFlat.parse();

				// Get the CSV
				return jsonFlat.toCSV(jsonEntryKey, propertyList.toArray(new String[0]), separator).toString();
			} catch (IllegalArgumentException e) {
				log.error(
					"Hostname {} - Error detected in the arguments when translating the JSON structure into CSV.",
					hostname
				);
			} catch (Exception e) {
				log.warn("Hostname {} - Error detected when running jsonFlat parsing:\n{}", hostname, jsonSource);
				log.debug("Hostname {} - Exception detected when running jsonFlat parsing: ", hostname, e);
			}

			return null;
		};

		String result = ThreadHelper.execute(jflatToCSV, JSON_2_CSV_TIMEOUT);

		LoggingHelper.trace(() ->
			log.trace(
				"Executed JSON to CSV conversion:\n- Json-source:\n{}\n- Json-entry-key: {}\n" + // NOSONAR
				"- Property-list: {}\n- Separator: {}\n- Result:\n{}\n",
				jsonSource,
				jsonEntryKey,
				propertyList,
				separator,
				result
			)
		);

		return result;
	}

	/**
	 * Parse a XML with the argument properties into a list of values list.
	 *
	 * @param xml        The XML.
	 * @param properties A string containing the paths to properties to retrieve separated by a semi-colon character.<br>
	 *                   If the property comes from an attribute, it will be preceded by a superior character: '>'.
	 * @param recordTag  A string containing the first element xml tags path to convert. example: /rootTag/tag2
	 * @return The list of values list.
	 * @throws XFlatException if an error occurred in the XML parsing.
	 */
	public List<List<String>> executeXmlParsing(final String xml, final String properties, final String recordTag)
		throws XFlatException {
		LoggingHelper.trace(() ->
			log.trace(
				"Executing XML parsing:\n- Xml-source:\n{}\n- Properties: {}\n- Record-tag: {}\n",
				xml,
				properties,
				recordTag
			)
		);

		List<List<String>> result = XFlat.parseXml(xml, properties, recordTag);

		LoggingHelper.trace(() ->
			log.trace(
				"Executed XML parsing:\n- Xml-source:\n{}\n- Properties: {}\n- Record-tag: {}\n- Result:\n{}\n",
				xml,
				properties,
				recordTag,
				TextTableHelper.generateTextTable(properties, result)
			)
		);

		return result;
	}

	/**
	 * Perform a WQL query, either against a CIM server (WBEM) or WMI
	 *
	 * <br>
	 *
	 * @param hostname      Hostname
	 * @param configuration The configuration object specifying how to connect to specified host
	 * @param query         WQL query to execute
	 * @param namespace     The namespace
	 * @return A table (as a {@link List} of {@link List} of {@link String}s)
	 * resulting from the execution of the query.
	 * @throws ClientException when anything wrong happens
	 */
	public List<List<String>> executeWql(
		final String hostname,
		final IConfiguration configuration,
		final String query,
		final String namespace
	) throws ClientException {
		if (configuration instanceof WinRmConfiguration winRmConfiguration) {
			return executeWqlThroughWinRm(hostname, winRmConfiguration, query, namespace);
		}

		throw new IllegalStateException("WQL queries can be executed only in WBEM, WMI and WinRM protocols.");
	}

	/**
	 * Perform a WQL remote command query, either against a CIM server (WBEM) or WMI
	 * <br>
	 *
	 * @param hostname      Hostname
	 * @param configuration The configuration object specifying how to connect to specified host
	 * @param command       Windows remote command to execute
	 * @param embeddedFiles The list of embedded files used in the wql remote command query
	 * @return A table (as a {@link List} of {@link List} of {@link String}s)
	 * resulting from the execution of the query.
	 * @throws ClientException when anything wrong happens
	 */
	public static String executeWinRemoteCommand(
		final String hostname,
		final IConfiguration configuration,
		final String command,
		final List<String> embeddedFiles
	) throws ClientException {
		if (configuration instanceof WinRmConfiguration winRmConfiguration) {
			return executeRemoteWinRmCommand(hostname, winRmConfiguration, command);
		}

		throw new IllegalStateException("Windows commands can be executed only in WMI and WinRM protocols.");
	}

	/**
	 * Execute a WinRM query
	 *
	 * @param hostname           The hostname of the device where the WinRM service is running (<code>null</code> for localhost)
	 * @param winRmConfiguration WinRM Protocol configuration (credentials, timeout)
	 * @param query              The query to execute
	 * @param namespace          The namespace on which to execute the query
	 * @return The result of the query
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	@WithSpan("WQL WinRM")
	public List<List<String>> executeWqlThroughWinRm(
		@SpanAttribute("host.hostname") @NonNull final String hostname,
		@SpanAttribute("wql.config") @NonNull final WinRmConfiguration winRmConfiguration,
		@SpanAttribute("wql.query") @NonNull final String query,
		@SpanAttribute("wql.namespace") @NonNull final String namespace
	) throws ClientException {
		final String username = winRmConfiguration.getUsername();
		final WinRMHttpProtocolEnum httpProtocol = TransportProtocols.HTTP.equals(winRmConfiguration.getProtocol())
			? WinRMHttpProtocolEnum.HTTP
			: WinRMHttpProtocolEnum.HTTPS;
		final Integer port = winRmConfiguration.getPort();
		final List<AuthenticationEnum> authentications = winRmConfiguration.getAuthentications();
		final Long timeout = winRmConfiguration.getTimeout();

		LoggingHelper.trace(() ->
			log.trace(
				"Executing WinRM WQL request:\n- hostname: {}\n- username: {}\n- query: {}\n" + // NOSONAR
				"- protocol: {}\n- port: {}\n- authentications: {}\n- timeout: {}\n- namespace: {}\n",
				hostname,
				username,
				query,
				httpProtocol,
				port,
				authentications,
				timeout,
				namespace
			)
		);

		// launching the request
		try {
			final long startTime = System.currentTimeMillis();

			WinRMWqlExecutor result = WinRMWqlExecutor.executeWql(
				httpProtocol,
				hostname,
				port,
				username,
				winRmConfiguration.getPassword(),
				namespace,
				query,
				timeout * 1000L,
				null,
				authentications
			);

			final long responseTime = System.currentTimeMillis() - startTime;

			final List<List<String>> table = result.getRows();

			LoggingHelper.trace(() ->
				log.trace(
					"Executed WinRM WQL request:\n- hostname: {}\n- username: {}\n- query: {}\n" + // NOSONAR
					"- protocol: {}\n- port: {}\n- authentications: {}\n- timeout: {}\n- namespace: {}\n- Result:\n{}\n- response-time: {}\n",
					hostname,
					username,
					query,
					httpProtocol,
					port,
					authentications,
					timeout,
					namespace,
					TextTableHelper.generateTextTable(table),
					responseTime
				)
			);

			return table;
		} catch (Exception e) {
			log.error("Hostname {} - WinRM WQL request failed. Errors:\n{}\n", hostname, StringHelper.getStackMessages(e));
			throw new ClientException(String.format("WinRM WQL request failed on %s.", hostname), e);
		}
	}

	/**
	 * Execute a WinRM remote command
	 *
	 * @param hostname           The hostname of the device where the WinRM service is running (<code>null</code> for localhost)
	 * @param winRmConfiguration WinRM Protocol configuration (credentials, timeout)
	 * @param command            The command to execute
	 * @return The result of the query
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	@WithSpan("Remote Command WinRM")
	public static String executeRemoteWinRmCommand(
		@SpanAttribute("host.hostname") @NonNull final String hostname,
		@SpanAttribute("winrm.config") @NonNull final WinRmConfiguration winRmConfiguration,
		@SpanAttribute("winrm.command") @NonNull final String command
	) throws ClientException {
		final String username = winRmConfiguration.getUsername();
		final WinRMHttpProtocolEnum httpProtocol = TransportProtocols.HTTP.equals(winRmConfiguration.getProtocol())
			? WinRMHttpProtocolEnum.HTTP
			: WinRMHttpProtocolEnum.HTTPS;
		final Integer port = winRmConfiguration.getPort();
		final List<AuthenticationEnum> authentications = winRmConfiguration.getAuthentications();
		final Long timeout = winRmConfiguration.getTimeout();

		LoggingHelper.trace(() ->
			log.trace(
				"Executing WinRM remote command:\n- hostname: {}\n- username: {}\n- command: {}\n" + // NOSONAR
				"- protocol: {}\n- port: {}\n- authentications: {}\n- timeout: {}\n",
				hostname,
				username,
				command,
				httpProtocol,
				port,
				authentications,
				timeout
			)
		);

		// launching the command
		try {
			final long startTime = System.currentTimeMillis();

			WindowsRemoteCommandResult result = WinRMCommandExecutor.execute(
				command,
				httpProtocol,
				hostname,
				port,
				username,
				winRmConfiguration.getPassword(),
				null,
				timeout * 1000L,
				null,
				null,
				authentications
			);

			final long responseTime = System.currentTimeMillis() - startTime;

			// If the command returns an error
			if (result.getStatusCode() != 0) {
				throw new ClientException(String.format("WinRM remote command failed on %s: %s", hostname, result.getStderr()));
			}

			final String resultStdout = result.getStdout();

			LoggingHelper.trace(() ->
				log.trace(
					"Executed WinRM remote command:\n- hostname: {}\n- username: {}\n- command: {}\n" + // NOSONAR
					"- protocol: {}\n- port: {}\n- authentications: {}\n- timeout: {}\n- Result:\n{}\n- response-time: {}\n",
					hostname,
					username,
					command,
					httpProtocol,
					port,
					authentications,
					timeout,
					resultStdout,
					responseTime
				)
			);

			return resultStdout;
		} catch (Exception e) {
			log.error("Hostname {} - WinRM remote command failed. Errors:\n{}\n", hostname, StringHelper.getStackMessages(e));
			throw new ClientException(String.format("WinRM remote command failed on %s.", hostname), e);
		}
	}
}
