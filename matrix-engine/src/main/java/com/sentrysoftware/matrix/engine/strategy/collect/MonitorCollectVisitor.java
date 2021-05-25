package com.sentrysoftware.matrix.engine.strategy.collect;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;

import org.springframework.util.Assert;

import com.sentrysoftware.matrix.common.helpers.HardwareConstants;
import com.sentrysoftware.matrix.common.meta.monitor.Battery;
import com.sentrysoftware.matrix.common.meta.monitor.Blade;
import com.sentrysoftware.matrix.common.meta.monitor.Cpu;
import com.sentrysoftware.matrix.common.meta.monitor.CpuCore;
import com.sentrysoftware.matrix.common.meta.monitor.DiskController;
import com.sentrysoftware.matrix.common.meta.monitor.Enclosure;
import com.sentrysoftware.matrix.common.meta.monitor.Fan;
import com.sentrysoftware.matrix.common.meta.monitor.IMetaMonitor;
import com.sentrysoftware.matrix.common.meta.monitor.Led;
import com.sentrysoftware.matrix.common.meta.monitor.LogicalDisk;
import com.sentrysoftware.matrix.common.meta.monitor.Lun;
import com.sentrysoftware.matrix.common.meta.monitor.Memory;
import com.sentrysoftware.matrix.common.meta.monitor.MetaConnector;
import com.sentrysoftware.matrix.common.meta.monitor.NetworkCard;
import com.sentrysoftware.matrix.common.meta.monitor.OtherDevice;
import com.sentrysoftware.matrix.common.meta.monitor.PhysicalDisk;
import com.sentrysoftware.matrix.common.meta.monitor.PowerSupply;
import com.sentrysoftware.matrix.common.meta.monitor.Robotic;
import com.sentrysoftware.matrix.common.meta.monitor.TapeDrive;
import com.sentrysoftware.matrix.common.meta.monitor.Target;
import com.sentrysoftware.matrix.common.meta.monitor.Temperature;
import com.sentrysoftware.matrix.common.meta.monitor.Voltage;
import com.sentrysoftware.matrix.common.meta.parameter.MetaParameter;
import com.sentrysoftware.matrix.common.meta.parameter.ParameterType;
import com.sentrysoftware.matrix.connector.model.monitor.MonitorType;
import com.sentrysoftware.matrix.engine.strategy.IMonitorVisitor;
import com.sentrysoftware.matrix.model.monitor.Monitor;
import com.sentrysoftware.matrix.model.parameter.IParameterValue;
import com.sentrysoftware.matrix.model.parameter.NumberParam;
import com.sentrysoftware.matrix.model.parameter.ParameterState;
import com.sentrysoftware.matrix.model.parameter.StatusParam;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MonitorCollectVisitor implements IMonitorVisitor {

	private static final String MONITOR_TYPE_CANNOT_BE_NULL = "monitorType cannot be null";
	private static final String VALUE_TABLE_CANNOT_BE_NULL = "valueTable cannot be null";
	private static final String DATA_CANNOT_BE_NULL = "row cannot be null.";
	private static final String MONITOR_COLLECT_INFO_CANNOT_BE_NULL = "monitorCollectInfo cannot be null.";
	private static final String CONNECTOR_NAME_CANNOT_BE_NULL = "connectorName cannot be null.";
	private static final String HOST_MONITORING_CANNOT_BE_NULL = "hostMonitoring cannot be null.";
	private static final String HOSTNAME_CANNOT_BE_NULL = "hostname cannot be null.";
	private static final String MAPPING_CANNOT_BE_NULL = "mapping cannot be null.";
	private static final String MONITOR_CANNOT_BE_NULL = "monitor cannot be null.";
	private static final String COLLECT_TIME_CANNOT_BE_NULL = "collectTime cannot be null.";
	private static final String UNKNOWN_STATUS_CANNOT_BE_NULL = "unknownStatus cannot be null.";

	private MonitorCollectInfo monitorCollectInfo;

	private static final Map<String, Function<ParameterState, String>> STATUS_INFORMATION_MAP;

	static {

		final Map<String, Function<ParameterState, String>> map = new HashMap<>();
		map.put(HardwareConstants.INTRUSION_STATUS_PARAMETER, MonitorCollectVisitor::getIntrusionStatusInformation);
		STATUS_INFORMATION_MAP = Collections.unmodifiableMap(map);
	}

	public MonitorCollectVisitor(MonitorCollectInfo monitorCollectInfo) {
		Assert.notNull(monitorCollectInfo, MONITOR_COLLECT_INFO_CANNOT_BE_NULL);
		checkCollectInfo(monitorCollectInfo);
		this.monitorCollectInfo = monitorCollectInfo;
	}
	

	private void checkCollectInfo(MonitorCollectInfo monitorCollectInfo) {
		Assert.notNull(monitorCollectInfo.getMonitor(), MONITOR_CANNOT_BE_NULL);
		Assert.notNull(monitorCollectInfo.getConnectorName(), CONNECTOR_NAME_CANNOT_BE_NULL);
		Assert.notNull(monitorCollectInfo.getRow(), DATA_CANNOT_BE_NULL);
		Assert.notNull(monitorCollectInfo.getHostMonitoring(), HOST_MONITORING_CANNOT_BE_NULL);
		Assert.notNull(monitorCollectInfo.getHostname(), HOSTNAME_CANNOT_BE_NULL);
		Assert.notNull(monitorCollectInfo.getMapping(), MAPPING_CANNOT_BE_NULL);
		Assert.notNull(monitorCollectInfo.getValueTable(), VALUE_TABLE_CANNOT_BE_NULL);
		Assert.notNull(monitorCollectInfo.getCollectTime(), COLLECT_TIME_CANNOT_BE_NULL);
		Assert.notNull(monitorCollectInfo.getUnknownStatus(), UNKNOWN_STATUS_CANNOT_BE_NULL);
	}

	@Override
	public void visit(MetaConnector metaConnector) {
		collectBasicParameters(metaConnector);

		appendValuesToStatusParameter(
				HardwareConstants.TEST_REPORT_PARAMETER
				);

	}

	@Override
	public void visit(Target target) {
		// Not implemented yet
	}

	@Override
	public void visit(Battery battery) {
		collectBasicParameters(battery);

		appendValuesToStatusParameter(
				HardwareConstants.PRESENT_PARAMETER,
				HardwareConstants.CHARGE_PARAMETER
				);
	}

	@Override
	public void visit(Blade blade) {
		collectBasicParameters(blade);

		appendValuesToStatusParameter(
				HardwareConstants.POWER_STATE_PARAMETER, 
				HardwareConstants.PRESENT_PARAMETER);
	}

	@Override
	public void visit(Cpu cpu) {
		collectBasicParameters(cpu);

		appendValuesToStatusParameter(
				HardwareConstants.CORRECTED_ERROR_COUNT_PARAMETER, 
				HardwareConstants.CURRENT_SPEED_PARAMETER,
				HardwareConstants.PREDICTED_FAILURE_PARAMETER,
				HardwareConstants.PRESENT_PARAMETER);
	}

	@Override
	public void visit(CpuCore cpuCore) {
		collectBasicParameters(cpuCore);

		appendValuesToStatusParameter(HardwareConstants.CURRENT_SPEED_PARAMETER, 
				HardwareConstants.USED_TIME_PERCENT_PARAMETER,
				HardwareConstants.PRESENT_PARAMETER);
	}

	@Override
	public void visit(DiskController diskController) {
		collectBasicParameters(diskController);

		appendValuesToStatusParameter(
				HardwareConstants.PRESENT_PARAMETER,
				HardwareConstants.BATTERY_STATUS_PARAMETER,
				HardwareConstants.CONTROLLER_STATUS_PARAMETER
				);
	}

	@Override
	public void visit(Enclosure enclosure) {
		collectBasicParameters(enclosure);

		collectPowerConsumption();

		appendValuesToStatusParameter(
				HardwareConstants.PRESENT_PARAMETER,
				HardwareConstants.INTRUSION_STATUS_PARAMETER,
				HardwareConstants.ENERGY_USAGE_PARAMETER,
				HardwareConstants.POWER_CONSUMPTION_PARAMETER);

	}

	@Override
	public void visit(Fan fan) {
		collectBasicParameters(fan);

		appendValuesToStatusParameter(HardwareConstants.SPEED_PARAMETER,
				HardwareConstants.PRESENT_PARAMETER,
				HardwareConstants.SPEED_PERCENT_PARAMETER);
	}

	@Override
	public void visit(Led led) {
		collectBasicParameters(led);

		appendValuesToStatusParameter(
				HardwareConstants.COLOR_PARAMETER,
				HardwareConstants.LED_INDICATOR_PARAMETER);
	}

	@Override
	public void visit(LogicalDisk logicalDisk) {
		collectBasicParameters(logicalDisk);

		appendValuesToStatusParameter(
				HardwareConstants.ERROR_COUNT_PARAMETER,
				HardwareConstants.UNALLOCATED_SPACE_PARAMETER);
	}

	@Override
	public void visit(Lun lun) {
		collectBasicParameters(lun);

		appendValuesToStatusParameter(
				HardwareConstants.AVAILABLE_PATH_COUNT_PARAMETER,
				HardwareConstants.AVAILABLE_PATH_INFORMATION_PARAMETER);
	}

	@Override
	public void visit(Memory memory) {
		collectBasicParameters(memory);
		
		appendValuesToStatusParameter(HardwareConstants.ERROR_COUNT_PARAMETER, HardwareConstants.ERROR_STATUS_PARAMETER,
				HardwareConstants.PREDICTED_FAILURE_PARAMETER, HardwareConstants.PRESENT_PARAMETER);
	}

	@Override
	public void visit(NetworkCard networkCard) {
		collectBasicParameters(networkCard);

		appendValuesToStatusParameter(
				HardwareConstants.PRESENT_PARAMETER, 
				HardwareConstants.BANDWIDTH_UTILIZATION_INFORMATION_PARAMETER, 
				HardwareConstants.DUPLEX_MODE_PARAMETER, 
				HardwareConstants.ERROR_PERCENT_PARAMETER, 
				HardwareConstants.LINK_SPEED_PARAMETER, 
				HardwareConstants.LINK_STATUS_PARAMETER, 
				HardwareConstants.RECEIVED_BYTES_RATE_PARAMETER, 
				HardwareConstants.RECEIVED_PACKETS_RATE_PARAMETER, 
				HardwareConstants.TRANSMITTED_BYTES_RATE_PARAMETER, 
				HardwareConstants.TRANSMITTED_PACKETS_RATE_PARAMETER, 
				HardwareConstants.ZERO_BUFFER_CREDIT_PERCENT_PARAMETER);

	}

	@Override
	public void visit(OtherDevice otherDevice) {
		collectBasicParameters(otherDevice);

		appendValuesToStatusParameter(
				HardwareConstants.PRESENT_PARAMETER, 
				HardwareConstants.USAGE_COUNT_PARAMETER, 
				HardwareConstants.VALUE_PARAMETER);
	}

	@Override
	public void visit(PhysicalDisk physicalDisk) {
		collectBasicParameters(physicalDisk);

		appendValuesToStatusParameter(
				HardwareConstants.PRESENT_PARAMETER, 
				HardwareConstants.USAGE_COUNT_PARAMETER, 
				HardwareConstants.INTRUSION_STATUS_PARAMETER,
				HardwareConstants.DEVICE_NOT_READY_ERROR_COUNT_PARAMETER,
				HardwareConstants.ENDURANCE_REMAINING_PARAMETER,
				HardwareConstants.ERROR_COUNT_PARAMETER, 
				HardwareConstants.HARD_ERROR_COUNT_PARAMETER, 
				HardwareConstants.ILLEGAL_REQUEST_ERROR_COUNT_PARAMETER,
				HardwareConstants.MEDIA_ERROR_COUNT_PARAMETER, 
				HardwareConstants.NO_DEVICE_ERROR_COUNT_PARAMETER, 
				HardwareConstants.PREDICTED_FAILURE_PARAMETER, 
				HardwareConstants.RECOVERABLE_ERROR_COUNT_PARAMETER, 
				HardwareConstants.TRANSPORT_ERROR_COUNT_PARAMETER);

	}

	@Override
	public void visit(PowerSupply powerSupply) {
		collectBasicParameters(powerSupply);

		appendValuesToStatusParameter(
				HardwareConstants.PRESENT_PARAMETER, 
				HardwareConstants.MOVE_COUNT_PARAMETER, 
				HardwareConstants.ERROR_COUNT_PARAMETER);
	}

	@Override
	public void visit(TapeDrive tapeDrive) {
		collectBasicParameters(tapeDrive);

		appendValuesToStatusParameter(
				HardwareConstants.PRESENT_PARAMETER, 
				HardwareConstants.ERROR_COUNT_PARAMETER, 
				HardwareConstants.MOUNT_COUNT_PARAMETER, 
				HardwareConstants.NEEDS_CLEANING_PARAMETER,
				HardwareConstants.UNMOUNT_COUNT_PARAMETER);

	}

	@Override
	public void visit(Temperature temperature) {
		collectBasicParameters(temperature);

		appendValuesToStatusParameter(HardwareConstants.TEMPERATURE_PARAMETER);
	}

	@Override
	public void visit(Voltage voltage) {
		collectBasicParameters(voltage);

		appendValuesToStatusParameter(HardwareConstants.VOLTAGE_PARAMETER);
	}

	@Override
	public void visit(Robotic robotic) {
		collectBasicParameters(robotic);
	}

	/**
	 * Collect the Status of the current {@link Monitor} instance
	 * 
	 * @param monitorType   The type of the monitor we currently collect
	 * @param parameterName The name of the status parameter to collect
	 * @param unit          The unit to set in the {@link IParameterValue} instance
	 */
	void collectStatusParameter(final MonitorType monitorType, final String parameterName, final String unit) {

		Assert.notNull(monitorType, MONITOR_TYPE_CANNOT_BE_NULL);

		checkCollectInfo(monitorCollectInfo);

		final Monitor monitor = monitorCollectInfo.getMonitor();
		final List<String> row = monitorCollectInfo.getRow();
		final Map<String, String> mapping = monitorCollectInfo.getMapping();
		final String hostname = monitorCollectInfo.getHostname();
		final String valueTable = monitorCollectInfo.getValueTable();
		final ParameterState unknownStatus = monitorCollectInfo.getUnknownStatus();
		final Long collectTime = monitorCollectInfo.getCollectTime();

		// Get the status raw value
		final String status = CollectHelper.getValueTableColumnValue(valueTable,
				parameterName,
				monitorType,
				row,
				mapping.get(parameterName));

		// Translate the status raw value
		final ParameterState state = CollectHelper.translateStatus(status,
				unknownStatus,
				monitor.getId(),
				hostname,
				parameterName);

		if (state == null) {
			log.warn("Could not collect {} for monitor id {}. Hostname {}", parameterName, monitor.getId(), hostname);
			return;
		}

		String statusInformation = null;

		// Get the status information
		if (HardwareConstants.STATUS_PARAMETER.equals(parameterName)) {
			statusInformation = CollectHelper.getValueTableColumnValue(valueTable,
					HardwareConstants.STATUS_INFORMATION_PARAMETER,
					monitorType,
					row,
					mapping.get(HardwareConstants.STATUS_INFORMATION_PARAMETER));
		}

		// Otherwise simply set the state name OK, WARN or ALARM
		if (statusInformation == null || statusInformation.trim().isEmpty()) {
			// Is there any specific implementation for the status information field
			if (STATUS_INFORMATION_MAP.containsKey(parameterName)) {
				statusInformation = STATUS_INFORMATION_MAP.get(parameterName).apply(state);
			} else {
				statusInformation = state.name();
			}
		}

		updateStatusParameter(monitor, parameterName, unit, collectTime, state, statusInformation);

	}

	/**
	 * Build the status information text value
	 * 
	 * @param parameterName The name of the parameter e.g. intrusionStatus, status
	 * @param ordinal       The numeric value of the status (0, 1, 2)
	 * @param value         The text value of the status information
	 * @return {@link String} value
	 */
	static String buildStatusInformation(final String parameterName, final int ordinal, final String value) {
		return new StringBuilder()
				.append(parameterName)
				.append(HardwareConstants.COLON)
				.append(HardwareConstants.WHITE_SPACE)
				.append(ordinal)
				.append(HardwareConstants.WHITE_SPACE)
				.append(HardwareConstants.OPENING_PARENTHESIS)
				.append(value)
				.append(HardwareConstants.CLOSING_PARENTHESIS)
				.toString();
	}

	/**
	 * Append the given parameter information to the status information
	 * 
	 * @param statusParam The {@link StatusParam} we wish to update its statusInformation field value
	 * @param parameter   The parameter we wish to append its value
	 */
	static void appendToStatusInformation(final StatusParam statusParam, final IParameterValue parameter) {
		if (statusParam == null || parameter == null) {
			return;
		}

		final String value = parameter.formatValueAsString();

		if (value == null) {
			return;
		}

		String existingStatusInformation = statusParam.getStatusInformation();

		if (existingStatusInformation == null) {
			existingStatusInformation = HardwareConstants.EMPTY;
		} else {
			existingStatusInformation += HardwareConstants.NEW_LINE;
		}

		final StringBuilder builder = new StringBuilder(existingStatusInformation)
				.append(value);

		statusParam.setStatusInformation(builder.toString());
	}

	/**
	 * Get the parameter identified by the given name from the current monitor then append the values to the StatusInformation fiend of the
	 * Status parameter
	 * 
	 * @param parameterNames The name of the parameters we wish to append in the StatusInformation of the Status parameter
	 */
	void appendValuesToStatusParameter(final String... parameterNames) {

		final Monitor monitor = monitorCollectInfo.getMonitor();
		Assert.notNull(monitor, MONITOR_CANNOT_BE_NULL);

		// Cannot be null
		final Map<String, IParameterValue> parameters = monitor.getParameters();

		final StatusParam statusParam = (StatusParam) parameters.get(HardwareConstants.STATUS_PARAMETER);
		if (statusParam == null) {
			// Nothing to append
			return;
		}

		for (String parameterName : parameterNames) {
			appendToStatusInformation(statusParam, parameters.get(parameterName));
		}
	}

	/**
	 * Collect a number parameter
	 * 
	 * @param monitorType   The type of the monitor we currently collect
	 * @param parameterName The name of the status parameter to collect
	 * @param unit          The unit to set in the {@link IParameterValue} instance
	 */
	void collectNumberParameter(final MonitorType monitorType, final String parameterName, final String unit) {

		Assert.notNull(monitorType, MONITOR_TYPE_CANNOT_BE_NULL);

		checkCollectInfo(monitorCollectInfo);

		final Monitor monitor = monitorCollectInfo.getMonitor();
		final Long collectTime = monitorCollectInfo.getCollectTime();


		final OptionalDouble valueOpt = extractParameterValue(monitorType, parameterName);
		if (valueOpt.isPresent()) {
			updateNumberParameter(monitor, parameterName, unit, collectTime, valueOpt.getAsDouble(), valueOpt.getAsDouble());
		}

	}

	/**
	 * Extract the parameter value from the current row
	 * 
	 * @param monitorType   The type of the monitor
	 * @param parameterName The unique name of the parameter
	 * @return {@link OptionalDouble} value
	 */
	OptionalDouble extractParameterValue(final MonitorType monitorType, final String parameterName) {
		Assert.notNull(monitorType, MONITOR_TYPE_CANNOT_BE_NULL);

		checkCollectInfo(monitorCollectInfo);

		final Monitor monitor = monitorCollectInfo.getMonitor();
		final List<String> row = monitorCollectInfo.getRow();
		final Map<String, String> mapping = monitorCollectInfo.getMapping();
		final String hostname = monitorCollectInfo.getHostname();
		final String valueTable = monitorCollectInfo.getValueTable();

		// Get the number value as string from the current row
		final String stringValue = CollectHelper.getValueTableColumnValue(valueTable,
				parameterName,
				monitorType,
				row,
				mapping.get(parameterName));


		if (stringValue == null) {
			log.debug("No {} to collect for monitor id {}. Hostname {}", parameterName, monitor.getId(), hostname);
			return OptionalDouble.empty();
		}

		try {
			return OptionalDouble.of(Double.parseDouble(stringValue));
		} catch(NumberFormatException e) {
			log.warn("Cannot parse the {} value '{}' for monitor id {}. {} won't be collected",
					parameterName, stringValue, monitor.getId(), parameterName);
		}

		return OptionalDouble.empty();
	}

	/**
	 * Update the number parameter value identified by <code>parameterName</code> in the given {@link Monitor} instance
	 * 
	 * @param monitor       The monitor we wish to collect the number parameter value
	 * @param parameterName The unique name of the parameter
	 * @param unit          The unit of the parameter
	 * @param collectTime   The collect time for this parameter
	 * @param value         The value to set on the {@link NumberParam} instance
	 * @param rawValue      The raw value to set as it is needed when computing delta and rates
	 */
	static void updateNumberParameter(final Monitor monitor, final String parameterName, final String unit, final Long collectTime,
			final Double value, final Double rawValue) {

		// GET the existing number parameter and update the value and the collect time
		NumberParam numberParam = monitor.getParameter(parameterName, NumberParam.class);

		// The parameter is not present then create it
		if (numberParam == null) {
			numberParam = NumberParam
					.builder()
					.name(parameterName)
					.unit(unit)
					.build();

		}

		numberParam.setValue(value);
		numberParam.setCollectTime(collectTime);
		numberParam.setRawValue(rawValue);

		monitor.addParameter(numberParam);
	}

	/**
	 * Update the status parameter value identified by <code>parameterName</code> in the given {@link Monitor} instance
	 * 
	 * @param monitor           The monitor we wish to collect the status parameter value
	 * @param parameterName     The unique name of the parameter
	 * @param unit              The unit of the parameter
	 * @param collectTime       The collect time for this parameter
	 * @param state             The {@link ParameterState} (OK, WARN, ALARM) used to build the {@link StatusParam}
	 * @param statusInformation The status information
	 */
	static void updateStatusParameter(final Monitor monitor, final String parameterName, final String unit, final Long collectTime,
			final ParameterState state, final String statusInformation) {

		StatusParam statusParam = monitor.getParameter(parameterName, StatusParam.class);

		if (statusParam == null) {
			statusParam = StatusParam
					.builder()
					.name(parameterName)
					.unit(unit)
					.build();
		}

		statusParam.setState(state);
		statusParam.setStatus(state.ordinal());
		statusParam.setStatusInformation(buildStatusInformation(
				parameterName,
				state.ordinal(),
				statusInformation));
		statusParam.setCollectTime(collectTime);

		monitor.addParameter(statusParam);
	}

	/**
	 * @param paramerState {@link ParameterState#OK}, {@link ParameterState#WARN} or {@link ParameterState#ALARM}
	 * @return a phrase for the intrusion status value
	 */
	static String getIntrusionStatusInformation(final ParameterState paramerState) {
		switch (paramerState) {
		case OK:
			return "No Intrusion Detected";
		case ALARM:
			return "Intrusion Detected";
		default: 
			return "Unexpected Intrusion Status";
		}
	}

	/**
	 * Collect the basic parameters as defined by the given {@link IMetaMonitor}
	 * 
	 * @param metaMonitor Defines all the meta information of the parameters to collect (name, type, unit and basic or not)
	 */
	private void collectBasicParameters(final IMetaMonitor metaMonitor) {

		metaMonitor.getMetaParameters()
		.values()
		.stream()
		.filter(metaParam -> metaParam.isBasicCollect() && ParameterType.STATUS.equals(metaParam.getType()))
		.sorted(new StatusParamFirstComparator())
		.forEach(metaParam -> collectStatusParameter(metaMonitor.getMonitorType(), metaParam.getName(), metaParam.getUnit()));

		metaMonitor.getMetaParameters()
		.values()
		.stream()
		.filter(metaParam -> metaParam.isBasicCollect() && ParameterType.NUMBER.equals(metaParam.getType()))
		.forEach(metaParam -> collectNumberParameter(metaMonitor.getMonitorType(), metaParam.getName(), metaParam.getUnit()));
	}


	/**
	 * Collect the power consumption. <br>
	 * <ol>
	 * <li>If the energyUsage is collected by the connector, we compute the delta energyUsage (Joules) and then the powerConsumption (Watts) based on the
	 * collected delta energyUsage and the collect time.</li>
	 * <li>If the connector collects the powerConsumption, we directly collect the power consumption (Watts) then we compute the energy usage based on the
	 * collected power consumption and the delta collect time</li>
	 * </ol>
	 */
	void collectPowerConsumption() {
		checkCollectInfo(monitorCollectInfo);

		final Monitor monitor = monitorCollectInfo.getMonitor();
		final Long collectTime = monitorCollectInfo.getCollectTime();
		final String hostname = monitorCollectInfo.getHostname();

		// When the connector collects the energy usage,
		// the power consumption will be computed based on the collected energy usage value
		final OptionalDouble energyUsageRawOpt = extractParameterValue(monitor.getMonitorType(), HardwareConstants.ENERGY_USAGE_PARAMETER);
		if (energyUsageRawOpt.isPresent() && energyUsageRawOpt.getAsDouble() >= 0) {

			collectPowerWithEnergyUsage(monitor, collectTime, energyUsageRawOpt, hostname);
			return;
		}

		// based on the power consumption compute the energy usage
		final OptionalDouble powerConsumptionOpt = extractParameterValue(monitor.getMonitorType(),
				HardwareConstants.POWER_CONSUMPTION_PARAMETER);
		if (powerConsumptionOpt.isPresent() && powerConsumptionOpt.getAsDouble() >= 0) {
			collectEnergyUsageWithPower(monitor, collectTime, powerConsumptionOpt, hostname);
		}

	}

	/**
	 * Collect the energy usage based on the power consumption
	 * 
	 * @param monitor             The monitor instance we wish to collect
	 * @param collectTime         The current collect time
	 * @param powerConsumptionOpt The power consumption as optional. Never empty
	 * @param hostname            The system host name used for debug purpose
	 */
	static void collectEnergyUsageWithPower(final Monitor monitor, final Long collectTime, final OptionalDouble powerConsumptionOpt, String hostname) {
		updateNumberParameter(monitor,
				HardwareConstants.POWER_CONSUMPTION_PARAMETER,
				HardwareConstants.POWER_CONSUMPTION_PARAMETER_UNIT,
				collectTime,
				powerConsumptionOpt.getAsDouble(),
				powerConsumptionOpt.getAsDouble());

		final OptionalDouble collectTimeOpt = OptionalDouble.of(collectTime.doubleValue());
		final OptionalDouble collectTimePreviousOpt = CollectHelper.getNumberParamCollectTime(monitor, HardwareConstants.POWER_CONSUMPTION_PARAMETER, true);

		final OptionalDouble deltaTimeOpt = CollectHelper.subtract(HardwareConstants.POWER_CONSUMPTION_PARAMETER,
				collectTimeOpt, collectTimePreviousOpt);
		final OptionalDouble energyUsageOpt = CollectHelper.multiply(HardwareConstants.POWER_CONSUMPTION_PARAMETER,
				powerConsumptionOpt, deltaTimeOpt);

		if (energyUsageOpt.isPresent()) {
			final double energyUsage =  energyUsageOpt.getAsDouble() / 1000D / (1000D / 3600D);

			updateNumberParameter(monitor,
					HardwareConstants.ENERGY_USAGE_PARAMETER,
					HardwareConstants.ENERGY_USAGE_PARAMETER_UNIT,
					collectTime,
					energyUsage,
					energyUsage);
		} else {
			log.debug("Cannot compute energy usage for monitor {} on system {}. Current power consumption {}, current time {}, previous time {}",
					monitor.getId(), hostname, powerConsumptionOpt, collectTimeOpt, collectTimePreviousOpt);
		}
	}

	/**
	 * Collect the power consumption based on the energy usage Power Consumption = Delta(energyUsageRaw) - Delta(CollectTime)
	 * @param monitor           The monitor instance we wish to collect
	 * @param collectTime       The current collect time
	 * @param energyUsageRawOpt The energyUsage as optional. Never empty
	 * @param hostname          The system host name used for debug purpose
	 */
	static void collectPowerWithEnergyUsage(final Monitor monitor, final Long collectTime, final OptionalDouble energyUsageRawOpt, final String hostname) {

		updateNumberParameter(monitor,
				HardwareConstants.ENERGY_USAGE_PARAMETER,
				HardwareConstants.ENERGY_USAGE_PARAMETER_UNIT,
				collectTime,
				null,
				energyUsageRawOpt.getAsDouble());

		// Previous raw value
		final OptionalDouble energyUsageRawPreviousOpt = CollectHelper.getNumberParamRawValue(monitor, HardwareConstants.ENERGY_USAGE_PARAMETER, true);

		// Time
		final OptionalDouble collectTimeOpt = OptionalDouble.of(collectTime.doubleValue());
		final OptionalDouble collectTimePreviousOpt = CollectHelper.getNumberParamCollectTime(monitor, HardwareConstants.ENERGY_USAGE_PARAMETER, true);

		// Compute the rate value: delta(raw energy usage) / delta (time)
		final OptionalDouble powerConsumptionOptional = CollectHelper.rate(HardwareConstants.POWER_CONSUMPTION_PARAMETER,
				energyUsageRawOpt, energyUsageRawPreviousOpt,
				collectTimeOpt, collectTimePreviousOpt);

		// Compute the delta to get the energy usage value
		final OptionalDouble energyUsage = CollectHelper.subtract(HardwareConstants.ENERGY_USAGE_PARAMETER, energyUsageRawOpt, energyUsageRawPreviousOpt);

		if (energyUsage.isPresent()) {
			updateNumberParameter(monitor,
					HardwareConstants.ENERGY_USAGE_PARAMETER,
					HardwareConstants.ENERGY_USAGE_PARAMETER_UNIT,
					collectTime,
					energyUsage.getAsDouble() * 1000 * 3600, // kW-hours to Joules
					energyUsageRawOpt.getAsDouble());
		} else {
			log.debug("Cannot compute energy usage for monitor {} on system {}. Current raw energy usage {}, previous raw energy usage {}",
					monitor.getId(), hostname, energyUsageRawOpt, energyUsageRawPreviousOpt);
		}

		if (powerConsumptionOptional.isPresent()) {
			// powerConsumptionOptional = (delta kwatt-hours) / delta (time in milliseconds)
			// powerConsumption = rate * 1000 (1Kw = 1000 Watts) * (1000 * 3600  To milliseconds convert to hours) 
			final double powerConsumption = powerConsumptionOptional.getAsDouble() * 1000 * (1000 * 3600);

			updateNumberParameter(monitor,
					HardwareConstants.POWER_CONSUMPTION_PARAMETER,
					HardwareConstants.POWER_CONSUMPTION_PARAMETER_UNIT,
					collectTime,
					powerConsumption,
					powerConsumption);
		} else {
			log.debug("Cannot compute power consumption for monitor {} on system {}.\n"
					+ "Current raw energy usage {}, previous raw energy usage {}, current time {}, previous time {}",
					monitor.getId(), hostname, energyUsageRawOpt, energyUsageRawPreviousOpt, collectTimeOpt, collectTimePreviousOpt);
		}
	}

	public static class StatusParamFirstComparator implements Comparator<MetaParameter> {

		@Override
		public int compare(final MetaParameter metaParam1, final MetaParameter metaParam2) {
			// Status first
			if (HardwareConstants.STATUS_PARAMETER.equalsIgnoreCase(metaParam1.getName())) {
				return -1;
			}

			return metaParam1.getName().compareTo(metaParam2.getName());
		}
	}

}
