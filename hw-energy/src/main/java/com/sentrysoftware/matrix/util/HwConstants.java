package com.sentrysoftware.matrix.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HwConstants {

	// Hardware Metrics

	public static final String HW_ENERGY_DISK_CONTROLLER_METRIC = "hw.energy{hw.type=\"disk_controller\"}";
	public static final String HW_POWER_DISK_CONTROLLER_METRIC = "hw.power{hw.type=\"disk_controller\"}";

	public static final String HW_ENERGY_FAN_METRIC = "hw.energy{hw.type=\"fan\"}";
	public static final String HW_POWER_FAN_METRIC = "hw.power{hw.type=\"fan\"}";

	public static final String HW_ENERGY_ROBOTICS_METRIC = "hw.energy{hw.type=\"robotics\"}";
	public static final String HW_POWER_ROBOTICS_METRIC = "hw.power{hw.type=\"robotics\"}";

	public static final String HW_ENERGY_TAPE_DRIVE_METRIC = "hw.energy{hw.type=\"tape_drive\"}";
	public static final String HW_POWER_TAPE_DRIVE_METRIC = "hw.power{hw.type=\"tape_drive\"}";
	public static final String HW_ENERGY_PHYSICAL_DISK_METRIC = "hw.energy{hw.type=\"physical_disk\"}";
	public static final String HW_POWER_PHYSICAL_DISK_METRIC = "hw.power{hw.type=\"physical_disk\"}";
}
