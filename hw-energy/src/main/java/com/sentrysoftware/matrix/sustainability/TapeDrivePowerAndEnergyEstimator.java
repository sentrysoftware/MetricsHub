package com.sentrysoftware.matrix.sustainability;

import com.sentrysoftware.matrix.telemetry.Monitor;
import com.sentrysoftware.matrix.telemetry.TelemetryManager;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TapeDrivePowerAndEnergyEstimator extends HardwarePowerAndEnergyEstimator {

	public TapeDrivePowerAndEnergyEstimator(final Monitor monitor, final TelemetryManager telemetryManager) {
		super(monitor, telemetryManager);
	}

	/**
	 * Estimates the power consumption of TapeDrive monitor
	 * @return Double
	 */
	@Override
	public Double estimatePower() {
		// TODO
		return null;
	}

	/**
	 * Estimates the energy consumption of TapeDrive monitor
	 * @return Double
	 */
	@Override
	public Double estimateEnergy() {
		// TODO
		return null;
	}
}
