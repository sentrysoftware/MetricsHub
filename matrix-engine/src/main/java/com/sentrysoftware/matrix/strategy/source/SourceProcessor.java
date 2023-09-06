package com.sentrysoftware.matrix.strategy.source;

import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.HTTP_CREDENTIALS_NOT_CONFIGURED_ERROR_MESSAGE;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.HTTP_PERCENT_S_PERCENT_S;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.HTTP_SOURCE_NULL_ERROR_MESSAGE;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.SNMP_GET_CREDENTIALS_NOT_CONFIGURED_ERROR_MESSAGE;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.SNMP_GET_PERCENT_S;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.SNMP_GET_SOURCE_NULL_ERROR_MESSAGE;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.SNMP_GET_TABLE_CREDENTIALS_NOT_CONFIGURED_ERROR_MESSAGE;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.SNMP_GET_TABLE_SOURCE_NULL_ERROR_MESSAGE;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.SNMP_SELECTED_COLUMNS_SPLIT_REGEX;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.SNMP_TABLE_LOG;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.WBEM;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.sentrysoftware.matrix.common.helpers.StringHelper;
import com.sentrysoftware.matrix.common.helpers.TextTableHelper;
import com.sentrysoftware.matrix.configuration.HttpConfiguration;
import com.sentrysoftware.matrix.configuration.SnmpConfiguration;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.CopySource;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.HttpSource;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.IpmiSource;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.OsCommandSource;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.SnmpGetSource;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.SnmpTableSource;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.StaticSource;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.TableJoinSource;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.TableUnionSource;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.WbemSource;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.WmiSource;
import com.sentrysoftware.matrix.matsya.MatsyaClientsExecutor;
import com.sentrysoftware.matrix.matsya.http.HttpRequest;
import com.sentrysoftware.matrix.telemetry.TelemetryManager;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Slf4j
public class SourceProcessor implements ISourceProcessor {

	private TelemetryManager telemetryManager;
	private String connectorName;
	private MatsyaClientsExecutor matsyaClientsExecutor;

	@WithSpan("Source Copy Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final CopySource copySource) {
		// TODO Auto-generated method stub
		return null;
	}

	@WithSpan("Source HTTP Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final HttpSource httpSource) {

		final String hostname = telemetryManager.getHostConfiguration().getHostname();
		if (httpSource == null) {
			log.error(HTTP_SOURCE_NULL_ERROR_MESSAGE, hostname);
			return SourceTable.empty();
		}

		final HttpConfiguration httpConfiguration = (HttpConfiguration) telemetryManager.getHostConfiguration()
			.getConfigurations().get(HttpConfiguration.class);

		if (httpConfiguration == null) {

			log.debug(HTTP_CREDENTIALS_NOT_CONFIGURED_ERROR_MESSAGE, hostname, httpSource);

			return SourceTable.empty();
		}

		try {

			final String result = matsyaClientsExecutor.executeHttp(
				HttpRequest.builder()
					.hostname(hostname)
					.method(httpSource.getMethod().toString())
					.url(httpSource.getUrl())
					.header(httpSource.getHeader(), connectorName, hostname)
					.body(httpSource.getBody(), connectorName, hostname)
					.resultContent(httpSource.getResultContent())
					.authenticationToken(httpSource.getAuthenticationToken())
					.httpConfiguration(httpConfiguration)
					.build(),
				true);

			if (result != null && !result.isEmpty()) {

				return SourceTable
					.builder()
					.rawData(result)
					.build();
			}

		} catch (Exception e) {
			logSourceError(connectorName, httpSource.getKey(), String.format(HTTP_PERCENT_S_PERCENT_S, httpSource.getMethod(),
				httpSource.getUrl()) , hostname, e);
		}

		return SourceTable.empty();
	}

	@WithSpan("Source IPMI Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final IpmiSource ipmiSource) {
		// TODO Auto-generated method stub
		return null;
	}

	@WithSpan("Source OS Command Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final OsCommandSource osCommandSource) {
		// TODO Auto-generated method stub
		return null;
	}

	@WithSpan("Source SNMP Get Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final SnmpGetSource snmpGetSource) {

		final String hostname = telemetryManager.getHostConfiguration().getHostname();

		if (snmpGetSource == null) {
			log.error(SNMP_GET_SOURCE_NULL_ERROR_MESSAGE, hostname);
			return SourceTable.empty();
		}

		final SnmpConfiguration snmpConfiguration = (SnmpConfiguration) telemetryManager.getHostConfiguration()
			.getConfigurations().get(SnmpConfiguration.class);

		if (snmpConfiguration == null) {
			log.debug(SNMP_GET_CREDENTIALS_NOT_CONFIGURED_ERROR_MESSAGE, hostname, snmpGetSource);
			return SourceTable.empty();
		}

		try {

			final String result = matsyaClientsExecutor.executeSNMPGet(
				snmpGetSource.getOid(),
				snmpConfiguration,
				hostname,
				true);

			if (result != null) {

				return SourceTable
					.builder()
					.table(Stream.of(Stream.of(result).toList()).toList())
					.build();
			}

		} catch (Exception e) { // NOSONAR on interruption

			logSourceError(connectorName,
				snmpGetSource.getKey(), String.format(SNMP_GET_PERCENT_S, snmpGetSource.getOid()),
				hostname, e);
		}

		return SourceTable.empty();
	}

	@WithSpan("Source SNMP Table Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final SnmpTableSource snmpTableSource) {

		final String hostname = telemetryManager.getHostConfiguration().getHostname();

		if (snmpTableSource == null) {
			log.error(SNMP_GET_TABLE_SOURCE_NULL_ERROR_MESSAGE, hostname);
			return SourceTable.empty();
		}

		// run Matsya in order to execute the snmpTable
		// receives a List structure
		SourceTable sourceTable = new SourceTable();
		String selectedColumns = snmpTableSource.getSelectColumns();

		if (selectedColumns.isBlank()) {
			return SourceTable.empty();
		}

		// The selectedColumns String is like "column1, column2, column3" and we want to split it into ["column1", "column2", "column3"]
		final String[] selectedColumnArray = selectedColumns.split(SNMP_SELECTED_COLUMNS_SPLIT_REGEX);

		final SnmpConfiguration protocol = (SnmpConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(SnmpConfiguration.class);

		if (protocol == null) {
			log.debug(SNMP_GET_TABLE_CREDENTIALS_NOT_CONFIGURED_ERROR_MESSAGE, hostname, snmpTableSource);
			return SourceTable.empty();
		}

		try {

			final List<List<String>> result = matsyaClientsExecutor.executeSNMPTable(
				snmpTableSource.getOid(),
				selectedColumnArray,
				protocol,
				hostname,
				true
			);

			sourceTable.setHeaders(Arrays.asList(selectedColumnArray));
			sourceTable.setTable(result);

			return sourceTable;

		} catch (Exception e) { // NOSONAR on interruptino

			logSourceError(
				connectorName,
				snmpTableSource.getKey(),
				String.format(SNMP_TABLE_LOG, snmpTableSource.getOid()),
				hostname,
				e
			);

			return SourceTable.empty();
		}
	}

	@WithSpan("Source Static Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final StaticSource staticSource) {
		// TODO Auto-generated method stub
		return null;
	}

	@WithSpan("Source TableJoin Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final TableJoinSource tableJoinSource) {

		final String hostname = telemetryManager.getHostConfiguration().getHostname();

		if (tableJoinSource == null) {
			log.error("Hostname {} - Table Join Source cannot be null, the Table Join will return an empty result.", hostname);
			return SourceTable.empty();
		}

		final Map<String, SourceTable> sources = telemetryManager
			.getHostProperties()
			.getConnectorNamespace(connectorName)
			.getSourceTables();

		if (sources == null ) {
			log.warn("Hostname {} - Source Table Map cannot be null, the Table Join {} will return an empty result.", hostname, tableJoinSource);
			return SourceTable.empty();
		}

		final SourceTable leftTable = sources.get(tableJoinSource.getLeftTable());
		if (tableJoinSource.getLeftTable() == null || leftTable == null ||  leftTable.getTable() == null) {
			log.debug("Hostname {} - Left table cannot be null, the Join {} will return an empty result.", hostname, tableJoinSource);
			return SourceTable.empty();
		}

		final SourceTable rightTable = sources.get(tableJoinSource.getRightTable());
		if (tableJoinSource.getRightTable() == null || rightTable == null || rightTable.getTable() == null) {
			log.debug("Hostname {} - Right table cannot be null, the Join {} will return an empty result.", hostname, tableJoinSource);
			return SourceTable.empty();
		}

		if (tableJoinSource.getLeftKeyColumn() < 1 || tableJoinSource.getRightKeyColumn() < 1) {
			log.error("Hostname {} - Invalid key column number (leftKeyColumnNumber={}, rightKeyColumnNumber={}).",
				tableJoinSource.getLeftKeyColumn(),
				tableJoinSource.getDefaultRightLine(),
				hostname);
			return SourceTable.empty();
		}

		logTableJoin(tableJoinSource.getKey(), tableJoinSource.getLeftTable(), tableJoinSource.getRightTable(),
			leftTable, rightTable, hostname);

		String defaultRightLine = tableJoinSource.getDefaultRightLine();

		final List<List<String>> executeTableJoin = matsyaClientsExecutor.executeTableJoin(
			leftTable.getTable(),
			rightTable.getTable(),
			tableJoinSource.getLeftKeyColumn(),
			tableJoinSource.getRightKeyColumn(),
			defaultRightLine != null ? Arrays.asList(defaultRightLine.split(";")) : null,
				WBEM.equalsIgnoreCase(tableJoinSource.getKeyType()),
				true);

		SourceTable sourceTable = new SourceTable();
		if (executeTableJoin != null) {
			sourceTable.setTable(executeTableJoin);
		}

		return sourceTable;
	}

	/**
	 * Log the table join left and right tables
	 *
	 * @param sourceKey      the table join source key
	 * @param leftSourceKey  the source key referencing the left source
	 * @param rightSourceKey the source key referencing the right source
	 * @param leftTable      the left table
	 * @param rightTable     the right table
	 */
	private static void logTableJoin(final String sourceKey, final String leftSourceKey, final String rightSourceKey,
			final SourceTable leftTable, final SourceTable rightTable, final String hostname) {

		if (!log.isDebugEnabled()) {
			return;
		}

		log.debug("Hostname {} - Table Join Source [{}]:\nLeft table [{}]:\n{}\nRight table [{}]:\n{}\n",
			hostname,
			sourceKey,
			leftSourceKey,
			TextTableHelper.generateTextTable(leftTable.getHeaders(), leftTable.getTable()),
			rightSourceKey,
			TextTableHelper.generateTextTable(rightTable.getHeaders(), rightTable.getTable()));

	}

	@WithSpan("Source TableUnion Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final TableUnionSource tableUnionSource) {
		// TODO Auto-generated method stub
		return null;
	}

	@WithSpan("Source WBEM HTTP Exec")
	public SourceTable process(@SpanAttribute("source.definition") final WbemSource wbemSource) {
		// TODO Auto-generated method stub
		return null;
	}

	@WithSpan("Source WMI Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final WmiSource wmiSource) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Log the given throwable
	 * 
	 * @param connectorName  The name of the connector
	 * @param sourceKey      The key of the source
	 * @param hostname       The host's hostname
	 * @param context        Additional information about the operation
	 * @param throwable      The catched throwable to log
	 */
	private static void logSourceError(
		final String connectorName,
		final String sourceKey,
		final String context,
		final String hostname,
		final Throwable throwable) {

		if (log.isErrorEnabled()) {
			log.error(
				"Hostname {} - Source [{}] was unsuccessful due to an exception." // NOSONAR on concatenation text block
				+ " Context [{}]. Connector: [{}]. Returning an empty table. Errors:\n{}\n", 
				hostname, sourceKey, context, connectorName, StringHelper.getStackMessages(throwable));
		}

		if (log.isDebugEnabled()) {
			log.debug(String.format(
				"Hostname %s - Source [%s] was unsuccessful due to an exception. Context [%s]. Connector: [%s]. Returning an empty table. Stack trace:",
				hostname, sourceKey, context, connectorName), throwable);
		}
	}
}
