package com.sentrysoftware.metricshub.cli.service.protocol;

import com.sentrysoftware.metricshub.cli.service.converter.SnmpPrivacyConverter;
import com.sentrysoftware.metricshub.cli.service.converter.SnmpVersionConverter;
import com.sentrysoftware.metricshub.engine.configuration.SnmpConfiguration;
import lombok.Data;
import picocli.CommandLine.Option;

@Data
public class SnmpConfigCli implements IProtocolConfigCli {

	public static final int DEFAULT_TIMEOUT = 30;

	@Option(
		names = "--snmp",
		order = 1,
		defaultValue = "1",
		paramLabel = "VERSION",
		description = "Enables SNMP protocol version: 1, 2, 3-md5, 3-sha or 3-noauth",
		converter = SnmpVersionConverter.class
	)
	SnmpConfiguration.SnmpVersion snmpVersion;

	@Option(
		names = { "--snmp-community", "--community" },
		order = 2,
		paramLabel = "COMMUNITY",
		defaultValue = "public",
		description = "Community string for SNMP version 1 and 2 (default: ${DEFAULT-VALUE})"
	)
	String community;

	@Option(
		names = "--snmp-username",
		order = 3,
		paramLabel = "USER",
		description = "Username for SNMP version 3 with MD5 or SHA"
	)
	String username;

	@Option(
		names = "--snmp-password",
		order = 4,
		paramLabel = "P4SSW0RD",
		description = "Password for SNMP version 3 with MD5 or SHA",
		interactive = true,
		arity = "0..1"
	)
	char[] password;

	@Option(
		names = "--snmp-privacy",
		order = 5,
		paramLabel = "DES|AES",
		description = "Privacy (encryption type) for SNMP version 3 (DES, AES, or none)",
		converter = SnmpPrivacyConverter.class
	)
	SnmpConfiguration.Privacy privacy;

	@Option(
		names = "--snmp-privacy-password",
		order = 6,
		paramLabel = "P4SSW0RD",
		description = "Privacy (encryption) password",
		interactive = true,
		arity = "0..1"
	)
	char[] privacyPassword;

	@Option(
		names = "--snmp-port",
		order = 7,
		paramLabel = "PORT",
		defaultValue = "161",
		description = "Port of the SNMP agent (default: ${DEFAULT-VALUE})"
	)
	int port;

	@Option(
		names = "--snmp-timeout",
		order = 8,
		paramLabel = "TIMEOUT",
		defaultValue = "" + DEFAULT_TIMEOUT,
		description = "Timeout in seconds for SNMP operations (default: ${DEFAULT-VALUE} s)"
	)
	long timeout;

	/**
	 * This method creates an {@link SnmpConfiguration} for a given username and a given password
	 *
	 * @param defaultUsername Username specified at the top level of the CLI (with the --username option)
	 * @param defaultPassword Password specified at the top level of the CLI (with the --password option)
	 * @return a SNMPProtocol instance corresponding to the options specified by the user in the CLI
	 */
	@Override
	public SnmpConfiguration toProtocol(String defaultUsername, char[] defaultPassword) {
		return SnmpConfiguration
			.builder()
			.version(snmpVersion)
			.community(community)
			.username(username == null ? defaultUsername : username)
			.password(username == null ? defaultPassword : password)
			.privacy(privacy)
			.privacyPassword(privacyPassword)
			.port(port)
			.timeout(timeout)
			.build();
	}
}
