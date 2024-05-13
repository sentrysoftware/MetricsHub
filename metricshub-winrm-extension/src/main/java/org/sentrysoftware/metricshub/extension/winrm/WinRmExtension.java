package org.sentrysoftware.metricshub.extension.winrm;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub WinRm Extension
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.CommandLineCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.IpmiCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.ServiceCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.WmiCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.Source;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.sentrysoftware.metricshub.engine.extension.IProtocolExtension;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceTable;
import org.sentrysoftware.metricshub.engine.telemetry.MetricFactory;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import org.sentrysoftware.metricshub.extension.win.IWinConfiguration;
import org.sentrysoftware.metricshub.extension.win.WinCommandService;
import org.sentrysoftware.metricshub.extension.win.detection.WinCommandLineCriterionProcessor;
import org.sentrysoftware.metricshub.extension.win.detection.WinIpmiCriterionProcessor;
import org.sentrysoftware.metricshub.extension.win.detection.WinServiceCriterionProcessor;
import org.sentrysoftware.metricshub.extension.win.detection.WmiCriterionProcessor;
import org.sentrysoftware.metricshub.extension.win.detection.WmiDetectionService;
import org.sentrysoftware.metricshub.extension.win.source.WinCommandLineSourceProcessor;
import org.sentrysoftware.metricshub.extension.win.source.WinIpmiSourceProcessor;
import org.sentrysoftware.metricshub.extension.win.source.WmiSourceProcessor;

/**
 * This class implements the {@link IProtocolExtension} contract, reports the supported features,
 * processes WMI sources and criteria through WinRm.
 */
@Slf4j
public class WinRmExtension implements IProtocolExtension {

	/**
	 * Protocol up status value '1.0'
	 */
	public static final Double UP = 1.0;

	/**
	 * Protocol down status value '0.0'
	 */
	public static final Double DOWN = 0.0;

	/**
	 * WinRm Up metric name format that will be saved by the metric factory
	 */
	public static final String WINRM_UP_METRIC = "metricshub.host.up{protocol=\"winrm\"}";

	/**
	 * WinRm Test Query
	 */
	public static final String WINRM_TEST_QUERY = "Select Name FROM Win32_ComputerSystem";

	/**
	 * WinRm namespace
	 */
	public static final String WINRM_TEST_NAMESPACE = "root\\cimv2";

	private WinRmRequestExecutor winRmRequestExecutor;
	private WmiDetectionService wmiDetectionService;
	private WinCommandService winCommandService;

	/**
	 * Creates a new instance of the {@link WinRmExtension} implementation.
	 */
	public WinRmExtension() {
		winRmRequestExecutor = new WinRmRequestExecutor();
		wmiDetectionService = new WmiDetectionService(winRmRequestExecutor);
		winCommandService = new WinCommandService(winRmRequestExecutor);
	}

	@Override
	public boolean isValidConfiguration(IConfiguration configuration) {
		return configuration instanceof WinRmConfiguration;
	}

	@Override
	public Set<Class<? extends Source>> getSupportedSources() {
		return Set.of(WmiSource.class, CommandLineSource.class, IpmiSource.class);
	}

	@Override
	public Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> getConfigurationToSourceMapping() {
		return Map.of(WinRmConfiguration.class, Set.of(WmiSource.class, IpmiSource.class, CommandLineSource.class));
	}

	@Override
	public Set<Class<? extends Criterion>> getSupportedCriteria() {
		return Set.of(WmiCriterion.class, ServiceCriterion.class, CommandLineCriterion.class, IpmiCriterion.class);
	}

	@Override
	public void checkProtocol(TelemetryManager telemetryManager) {
		// Create and set the WinRM result to null
		List<List<String>> winRmResult = null;

		final String hostname = telemetryManager.getHostname();

		// Retrieve WinRM Configuration from the telemetry manager host configuration
		final WinRmConfiguration winRmConfiguration = (WinRmConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(WinRmConfiguration.class);

		// Stop the health check if there is not an WinRM configuration
		if (winRmConfiguration == null) {
			return;
		}

		log.info(
			"Hostname {} - Checking WinRM protocol status. Sending a WQL SELECT request on {} namespace.",
			hostname,
			WINRM_TEST_NAMESPACE
		);

		// Retrieve the host endpoint monitor
		final Monitor hostMonitor = telemetryManager.getEndpointHostMonitor();

		// Create the MetricFactory in order to collect the up metric
		final MetricFactory metricFactory = new MetricFactory();

		// Get the strategy time which represents the collect time of the up metric
		final Long strategyTime = telemetryManager.getStrategyTime();

		try {
			winRmResult =
				winRmRequestExecutor.executeWmi(hostname, winRmConfiguration, WINRM_TEST_QUERY, WINRM_TEST_NAMESPACE);
		} catch (Exception e) {
			if (winRmRequestExecutor.isAcceptableException(e)) {
				// Generate a metric from the WinRM result
				metricFactory.collectNumberMetric(hostMonitor, WINRM_UP_METRIC, UP, strategyTime);
				return;
			}
			log.debug(
				"Hostname {} - Checking WinRM protocol status. WinRM exception when performing a WQL SELECT request on {} namespace: ",
				hostname,
				WINRM_TEST_NAMESPACE,
				e
			);
		}

		// Generate a metric from the WINRM result
		metricFactory.collectNumberMetric(hostMonitor, WINRM_UP_METRIC, winRmResult != null ? UP : DOWN, strategyTime);
	}

	@Override
	public CriterionTestResult processCriterion(
		Criterion criterion,
		String connectorId,
		TelemetryManager telemetryManager
	) {
		final Function<TelemetryManager, IWinConfiguration> configurationRetriever = manager ->
			(IWinConfiguration) manager.getHostConfiguration().getConfigurations().get(WinRmConfiguration.class);

		if (criterion instanceof WmiCriterion wmiCriterion) {
			return new WmiCriterionProcessor(wmiDetectionService, configurationRetriever, connectorId)
				.process(wmiCriterion, telemetryManager);
		} else if (criterion instanceof ServiceCriterion serviceCriterion) {
			return new WinServiceCriterionProcessor(wmiDetectionService, configurationRetriever)
				.process(serviceCriterion, telemetryManager);
		} else if (criterion instanceof CommandLineCriterion commandLineCriterion) {
			return new WinCommandLineCriterionProcessor(winCommandService, configurationRetriever)
				.process(commandLineCriterion, telemetryManager);
		} else if (criterion instanceof IpmiCriterion ipmiCriterion) {
			return new WinIpmiCriterionProcessor(wmiDetectionService, configurationRetriever)
				.process(ipmiCriterion, telemetryManager);
		}

		throw new IllegalArgumentException(
			String.format(
				"Hostname %s - Cannot process criterion %s.",
				telemetryManager.getHostname(),
				criterion != null ? criterion.getClass().getSimpleName() : "<null>"
			)
		);
	}

	@Override
	public SourceTable processSource(Source source, String connectorId, TelemetryManager telemetryManager) {
		final Function<TelemetryManager, IWinConfiguration> configurationRetriever = manager ->
			(IWinConfiguration) manager.getHostConfiguration().getConfigurations().get(WinRmConfiguration.class);

		if (source instanceof WmiSource wmiSource) {
			return new WmiSourceProcessor(winRmRequestExecutor, configurationRetriever, connectorId)
				.process(wmiSource, telemetryManager);
		} else if (source instanceof IpmiSource ipmiSource) {
			return new WinIpmiSourceProcessor(winRmRequestExecutor, configurationRetriever, connectorId)
				.process(ipmiSource, telemetryManager);
		} else if (source instanceof CommandLineSource commandLineSource) {
			return new WinCommandLineSourceProcessor(winCommandService, configurationRetriever, connectorId)
				.process(commandLineSource, telemetryManager);
		}

		throw new IllegalArgumentException(
			String.format(
				"Hostname %s - Cannot process source %s.",
				telemetryManager.getHostname(),
				source != null ? source.getClass().getSimpleName() : "<null>"
			)
		);
	}

	@Override
	public boolean isSupportedConfigurationType(String configurationType) {
		return "winrm".equalsIgnoreCase(configurationType);
	}

	@Override
	public IConfiguration buildConfiguration(
		@NonNull String configurationType,
		@NonNull JsonNode jsonNode,
		@NonNull UnaryOperator<char[]> decrypt
	) throws InvalidConfigurationException {
		try {
			final WinRmConfiguration winRmConfiguration = newObjectMapper().treeToValue(jsonNode, WinRmConfiguration.class);

			if (decrypt != null) {
				final char[] password = winRmConfiguration.getPassword();
				if (password != null) {
					// Decrypt the password
					winRmConfiguration.setPassword(decrypt.apply(password));
				}
			}

			return winRmConfiguration;
		} catch (Exception e) {
			final String errorMessage = String.format(
				"Error while reading WinRm Configuration: %s. Error: %s",
				jsonNode,
				e.getMessage()
			);
			log.error(errorMessage);
			log.debug("Error while reading WinRm Configuration: {}. Stack trace:", jsonNode, e);
			throw new InvalidConfigurationException(errorMessage, e);
		}
	}

	/**
	 * Creates and configures a new instance of the Jackson ObjectMapper for handling YAML data.
	 *
	 * @return A configured ObjectMapper instance.
	 */
	public static JsonMapper newObjectMapper() {
		return JsonMapper
			.builder(new YAMLFactory())
			.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
			.enable(SerializationFeature.INDENT_OUTPUT)
			.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
			.build();
	}
}
