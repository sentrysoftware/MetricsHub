BEGIN {
	FS = ": "
	gpuID = ""
	model = ""
	serialNumber = ""
	gpuUuid = ""
	vBios = ""
	driverVersion = ""
	cudaVersion = ""
	firmwareVersion = ""
	transferredBytes = ""
	receivedBytes = ""
	gpuUtilization = ""
	memoryUtilization = ""
	encoderUtilization = ""
	decoderUtilization = ""
	fanSpeed = ""
	voltage = ""
	temperature = ""
	warnTemperature = ""
	critTemperature = ""
	powerConsumption = ""
	minPowerLimit = ""
	maxPowerLimit = ""
	correctable = ""
	uncorrectable = ""
	totalMemory = ""
}

#Discovery related values and informative fields.
/GPU 0/ {
	split($0, outputarray, ":")
	gpuID = outputarray[2]
}

/Product Name/ {
	model = $2
}

/Serial Number/ && /[0-9]/ {
	serialNumber = $2
}

/GPU UUID/ {
	gpuUuid = "GPU UUID: " $2
}

/VBIOS Version/ {
	vBios = " VBIOS Version: " $2
}

/Driver Version/ {
	driverVersion = $2
}

/CUDA Version/ {
	cudaVersion = "CUDA Version: " $2
}

/Firmware Version/ && /[0-9]/ {
	firmwareVersion = $2
}

/FB Memory Usage/ {
	getline
	totalMemory = $2
	gsub(/ MiB/, "", totalMemory)
}

#Collection related values and removal of units of measurement.
/Tx Throughput/ && /[0-9]/ {
	transferredBytes = $2
	gsub(/ KB\/s/, "", transferredBytes)
}

/Rx Throughput/ && /[0-9]/ {
	receivedBytes = $2
	gsub(/ KB\/s/, "", receivedBytes)
}

/Gpu/ && /%/ {
	gpuUtilization = $2
	gsub(/ %/, "", gpuUtilization)
}

/Memory/ && /%/ {
	memoryUtilization = $2
	gsub(/ %/, "", memoryUtilization)
}

/Encoder/ && /%/ {
	encoderUtilization = $2
	gsub(/ %/, "", encoderUtilization)
}

/Decoder/ && /%/ {
	decoderUtilization = $2
	gsub(/ %/, "", decoderUtilization)
}

#We will now extract and do the sum of the ecc errors.
#/Correctable/ && /[0-9]/  { correctable+=$2; }
#/Uncorrectable/ && /[0-9]/  { uncorrectable+=$2; }

#We will now extract the power consumption.
/Power Draw/ && /[0-9]/ {
	powerConsumption = $2
	gsub(/ W/, "", powerConsumption)
}

/Min Power Limit/ && /[0-9]/ {
	minPowerLimit = $2
	gsub(/ W/, "", minPowerLimit)
}

/Max Power Limit/ && /[0-9]/ {
	maxPowerLimit = $2
	gsub(/ W/, "", maxPowerLimit)
}

#TBD IF REQUIRED.
#Parsing through the file to gather the clock speeds. As there are several iterations of these strings, we had to isolate them.

#/^    Clocks$/ { for(i=1; i<=4; i++);
#  IF (/Graphics/) getline; graphicsClock = $2; gsub(/ MHz/,"",graphicsClock);
#  IF (/SM/) getline; smClock = $2; gsub(/ MHz/,"",smClock);
#  IF (/Memory/) getline; memoryClock = $2; gsub(/ MHz/,"",memoryClock);
#  IF (/Video/) getline; videoClock = $2; gsub(/ MHz/,"",videoClock); }

#Collection of fan class values and removal of units of measurement.
/Fan Speed/ && /[0-9]/ {
	fanSpeed = $2
	gsub(/ %/, "", fanSpeed)
}

#Collection of voltage class values and removal of units of measurement.
/Voltage/ {
	getline
	if (/[0-9]/) {
		voltage = $2
	}
	gsub(/ mV/, "", voltage)
}

#We will now extract the temperature along with its thresholds.
/GPU Current Temp/ && /[0-9]/ {
	temperature = $2
	gsub(/ C/, "", temperature)
}

/GPU Target Temperature/ && /[0-9]/ {
	warnTemperature = $2
	gsub(/ C/, "", warnTemperature)
}

/GPU Slowdown Temp/ && /[0-9]/ {
	critTemperature = $2
	gsub(/ C/, "", critTemperature)
}

#We will now print all the relevant information, separated by classes. Processes being the last category, this will allow us to print these at the end of each card.
#We then reset all variables to empty values for the next card.
/Processes/ {
	print "MSHW_GPU;" "GPU" gpuID ";" (transferredBytes * 1024) ";" (receivedBytes * 1024) ";" gpuUtilization ";" memoryUtilization ";" encoderUtilization ";" decoderUtilization ";" powerConsumption ";" minPowerLimit ";" maxPowerLimit ";" correctable ";" uncorrectable ";" model ";" serialNumber ";" gpuUuid vBios ";" driverVersion ";" cudaVersion ";" firmwareVersion ";" totalMemory
	print "MSHW_TEMP;" "GPU" gpuID ";" temperature ";" warnTemperature ";" critTemperature
	print "MSHW_VOLTAGE;" "GPU" gpuID ";" voltage
	print "MSHW_FAN;" "GPU" gpuID ";" fanSpeed
	gpuID = ""
	model = ""
	serialNumber = ""
	gpuUuid = ""
	vBios = ""
	driverVersion = ""
	cudaVersion = ""
	firmwareVersion = ""
	transferredBytes = ""
	receivedBytes = ""
	gpuUtilization = ""
	memoryUtilization = ""
	encoderUtilization = ""
	decoderUtilization = ""
	fanSpeed = ""
	voltage = ""
	temperature = ""
	warnTemperature = ""
	critTemperature = ""
	powerConsumption = ""
	minPowerLimit = ""
	maxPowerLimit = ""
	correctable = ""
	uncorrectable = ""
	totalMemory = ""
}

