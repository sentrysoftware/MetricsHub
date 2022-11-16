package com.sentrysoftware.hardware.cli.component.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.sentrysoftware.hardware.cli.component.cli.protocols.HttpConfigCli;
import com.sentrysoftware.hardware.cli.component.cli.protocols.SnmpConfigCli;
import com.sentrysoftware.hardware.cli.component.cli.protocols.WbemConfigCli;
import com.sentrysoftware.hardware.cli.component.cli.protocols.WmiConfigCli;
import com.sentrysoftware.matrix.engine.host.HostType;
import com.sentrysoftware.matrix.engine.protocol.HttpProtocol;
import com.sentrysoftware.matrix.engine.protocol.IpmiOverLanProtocol;
import com.sentrysoftware.matrix.engine.protocol.SnmpProtocol;
import com.sentrysoftware.matrix.engine.protocol.SshProtocol;
import com.sentrysoftware.matrix.engine.protocol.TransportProtocols;
import com.sentrysoftware.matrix.engine.protocol.WbemProtocol;
import com.sentrysoftware.matrix.engine.protocol.WinRmProtocol;
import com.sentrysoftware.matrix.engine.protocol.WmiProtocol;
import com.sentrysoftware.matsya.winrm.service.client.auth.AuthenticationEnum;

import picocli.CommandLine;
import picocli.CommandLine.MissingParameterException;

class HardwareSentryCliTest {

	private static final String DEVICE_TYPE_OPTION = "--type";

	private static final String HTTP_OPTION = "--http";
	private static final String HTTP_PORT_OPTION = "--http-port";
	private static final String HTTP_TIMEOUT_OPTION = "--http-timeout";
	private static final String HTTP_USERNAME_OPTION = "--http-username";
	private static final String HTTP_PASSWORD_OPTION = "--http-password";

	/**
	 * Executes the CLI with the specified arguments and return its output.
	 * <p>
	 * @param args Arguments as in main(String [])
	 * @return the output of the command
	 * @throws IOException when something goes wrong with the gathering of the output
	 */
	private static String executeCli(final String[]... args) throws IOException {

		String[] allArgs = Stream.of(args)
				.flatMap(Stream::of)
				.toArray(String[]::new);

		// Setup the command line execution to gather its output
		try(StringWriter resultWriter = new StringWriter();
				PrintWriter printWriter = new PrintWriter(resultWriter)) {

			// Execute
			new CommandLine(new HardwareSentryCli())
					.setOut(printWriter)
					.setErr(printWriter)
					.execute(allArgs);

			// Gather the result
			return resultWriter.toString();
		}
	}

	@Test
	void snmpArgumentsTest() {
		String[] args_hdfs = { "hostaa",
				"-t", "hpux",
				"--snmp", "v2c",
				"--snmp-port", "200",
				"--snmp-community", "private",
				"--snmp-timeout", "60",
				"-f", "hdfs1,hdfs2,hdfs3" };
		HardwareSentryCli sentryCli = new HardwareSentryCli();
		new CommandLine(sentryCli).parseArgs(args_hdfs);

		assertEquals("hostaa", sentryCli.getHostname()) ;
		assertEquals(HostType.HP_UX, sentryCli.getDeviceType());
		assertEquals(SnmpProtocol.SnmpVersion.V2C, sentryCli.getSnmpConfigCli().getSnmpVersion());
		assertEquals(200, sentryCli.getSnmpConfigCli().getPort());
		assertEquals("private", sentryCli.getSnmpConfigCli().getCommunity());
		assertEquals(60, sentryCli.getSnmpConfigCli().getTimeout());
		assertEquals(sentryCli.getConnectors(), new HashSet<>(Arrays.asList("hdfs1", "hdfs2", "hdfs3")));
		assertNull(sentryCli.getExcludedConnectors());


		String[] args_exclud_hdfs = { "hosta",
				"-t", "hp-ux",
				"--snmp", "2",
				"--snmp-port", "200",
				"--snmp-community", "private",
				"--snmp-timeout", "60",
				"-x", "hdfs1,hdfs2" };

		sentryCli = new HardwareSentryCli();
		new CommandLine(sentryCli).parseArgs(args_exclud_hdfs);
		assertEquals("hosta", sentryCli.getHostname()) ;
		assertEquals(HostType.HP_UX, sentryCli.getDeviceType());
		assertEquals(SnmpProtocol.SnmpVersion.V2C, sentryCli.getSnmpConfigCli().getSnmpVersion());
		assertEquals(200, sentryCli.getSnmpConfigCli().getPort());
		assertEquals("private", sentryCli.getSnmpConfigCli().getCommunity());
		assertEquals(60, sentryCli.getSnmpConfigCli().getTimeout());
		assertEquals(sentryCli.getExcludedConnectors(), new HashSet<>(Arrays.asList("hdfs1", "hdfs2")));
		assertNull(sentryCli.getConnectors());

		String[] args_default_snmp = { "hostaa",
				"-t", "HPUX",
				"--snmp", "1",
				"-f", "hdfs1,hdfs2" };

		sentryCli = new HardwareSentryCli();
		new CommandLine(sentryCli).parseArgs(args_default_snmp);
		assertEquals("hostaa", sentryCli.getHostname()) ;
		assertEquals(HostType.HP_UX, sentryCli.getDeviceType());
		assertEquals(SnmpProtocol.SnmpVersion.V1, sentryCli.getSnmpConfigCli().getSnmpVersion());
		assertEquals(161, sentryCli.getSnmpConfigCli().getPort());
		assertEquals("public", sentryCli.getSnmpConfigCli().getCommunity());
		assertEquals(SnmpConfigCli.DEFAULT_TIMEOUT, sentryCli.getSnmpConfigCli().getTimeout());
		assertEquals(sentryCli.getConnectors(), new HashSet<>(Arrays.asList("hdfs1", "hdfs2")));
		assertNull(sentryCli.getExcludedConnectors());

		String[] args_required_host = {
				"-t", "hpux",
				"-f", "hdfs1,hdfs2" };

		final CommandLine commandLine = new CommandLine(new HardwareSentryCli());

		assertThrows(MissingParameterException.class, () -> commandLine.parseArgs(args_required_host));

	}

	@Test
	void wbemArgumentsTest() {
		String[] args_hdfs = { "hostaa",
				"-t", "hpux",
				"--wbem-transport", "HTTP",
				"--wbem-port", "5989",
				"--wbem-force-namespace", "root/emc",
				"--wbem-timeout", "120",
				"--wbem-username", "admin",
				"--wbem-password", "#1Password",
				"-f", "hdfs1,hdfs2,hdfs3" };
		HardwareSentryCli sentryCli = new HardwareSentryCli();
		new CommandLine(sentryCli).parseArgs(args_hdfs);

		assertEquals("hostaa", sentryCli.getHostname());
		assertEquals(HostType.HP_UX, sentryCli.getDeviceType());
		assertEquals(TransportProtocols.HTTP, sentryCli.getWbemConfigCli().getProtocol());
		assertEquals(5989, sentryCli.getWbemConfigCli().getPort());
		assertEquals("root/emc", sentryCli.getWbemConfigCli().getNamespace());
		assertEquals(120, sentryCli.getWbemConfigCli().getTimeout());
		assertEquals("admin", sentryCli.getWbemConfigCli().getUsername());
		assertArrayEquals("#1Password".toCharArray(), sentryCli.getWbemConfigCli().getPassword());
		assertEquals(sentryCli.getConnectors(), new HashSet<>(Arrays.asList("hdfs1", "hdfs2", "hdfs3")));
		assertNull(sentryCli.getExcludedConnectors());

		String[] args_hdfs2 = { "dev-hv-01",
				"-t", "win",
				"--wbem-transport", "https",
				"--wbem-port", "5989",
				"--wbem-force-namespace", "root/emc",
				"--wbem-username", "admin",
				"--wbem-password", "#1Password",
				"-f", "hdfs1,hdfs2,hdfs3" };

		new CommandLine(sentryCli).parseArgs(args_hdfs2);

		assertEquals("dev-hv-01", sentryCli.getHostname());
		assertEquals(HostType.MS_WINDOWS, sentryCli.getDeviceType());
		assertEquals(TransportProtocols.HTTPS, sentryCli.getWbemConfigCli().getProtocol());
		assertEquals(5989, sentryCli.getWbemConfigCli().getPort());
		assertEquals("root/emc", sentryCli.getWbemConfigCli().getNamespace());
		assertEquals(WbemConfigCli.DEFAULT_TIMEOUT, sentryCli.getWbemConfigCli().getTimeout());
		assertEquals("admin", sentryCli.getWbemConfigCli().getUsername());
		assertArrayEquals("#1Password".toCharArray(), sentryCli.getWbemConfigCli().getPassword());
		assertEquals(sentryCli.getConnectors(), new HashSet<>(Arrays.asList("hdfs1", "hdfs2", "hdfs3")));
		assertNull(sentryCli.getExcludedConnectors());

	}

	@Test
	void httpCredentialsTest() {

		final String HOST = "host";
		final String USER = "user";
		final String PASS = "pass";
		final String PORT = "443";
		final String TIMEOUT = "60";

		// No HTTP credentials
		String[] arguments = {HOST, DEVICE_TYPE_OPTION, HostType.LINUX.toString()};
		HardwareSentryCli hardwareSentryCli = new HardwareSentryCli();
		CommandLine commandLine = new CommandLine(hardwareSentryCli);
		commandLine.parseArgs(arguments);
		assertEquals(HOST, hardwareSentryCli.getHostname());
		assertEquals(HostType.LINUX, hardwareSentryCli.getDeviceType());
		assertNull(hardwareSentryCli.getHttpConfigCli());

		// Default values
		arguments = new String[]{HOST, DEVICE_TYPE_OPTION, HostType.LINUX.toString(),
			HTTP_USERNAME_OPTION, USER, HTTP_PASSWORD_OPTION, PASS};
		commandLine.parseArgs(arguments);
		assertEquals(HOST, hardwareSentryCli.getHostname());
		assertEquals(HostType.LINUX, hardwareSentryCli.getDeviceType());
		HttpConfigCli httpConfigCli = hardwareSentryCli.getHttpConfigCli();
		assertNotNull(httpConfigCli);
		assertNull(httpConfigCli.getHttpOrHttps());
		assertNull(httpConfigCli.getPort());
		assertEquals(HttpConfigCli.DEFAULT_TIMEOUT, httpConfigCli.getTimeout());
		assertEquals(USER, httpConfigCli.getUsername());
		assertEquals(PASS, new String(httpConfigCli.getPassword()));

		// Explicit values
		arguments = new String[]{HOST, DEVICE_TYPE_OPTION, HostType.LINUX.toString(),
			HTTP_USERNAME_OPTION, USER, HTTP_PASSWORD_OPTION, PASS, HTTP_OPTION,
			HTTP_PORT_OPTION, PORT, HTTP_TIMEOUT_OPTION, TIMEOUT};
		commandLine.parseArgs(arguments);
		assertEquals(HOST, hardwareSentryCli.getHostname());
		assertEquals(HostType.LINUX, hardwareSentryCli.getDeviceType());
		httpConfigCli = hardwareSentryCli.getHttpConfigCli();
		assertNotNull(httpConfigCli);
		assertTrue(httpConfigCli.getHttpOrHttps().isHttp());
		assertEquals(Integer.parseInt(PORT), httpConfigCli.getPort());
		assertEquals(Long.parseLong(TIMEOUT), httpConfigCli.getTimeout());
		assertEquals(USER, httpConfigCli.getUsername());
		assertEquals(PASS, new String(httpConfigCli.getPassword()));
	}

	@Test
	void wmiArgumentsTest() {
		{
			String[] args_hdfs = { "hostaa",
					"-t", "win",
					"--wmi-force-namespace", "root\\ibmsd",
					"--wmi-username", "admin",
					"--wmi-password", "#1Password",
					"-f", "hdfs1,hdfs2,hdfs3" };
			HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(args_hdfs);

			assertEquals("hostaa", sentryCli.getHostname());
			assertEquals(HostType.MS_WINDOWS, sentryCli.getDeviceType());
			assertEquals("root\\ibmsd", sentryCli.getWmiConfigCli().getNamespace());
			assertEquals(WmiConfigCli.DEFAULT_TIMEOUT, sentryCli.getWmiConfigCli().getTimeout());
			assertEquals("admin", sentryCli.getWmiConfigCli().getUsername());
			assertArrayEquals("#1Password".toCharArray(), sentryCli.getWmiConfigCli().getPassword());
			assertEquals(sentryCli.getConnectors(), new HashSet<>(Arrays.asList("hdfs1", "hdfs2", "hdfs3")));
		}

		{
			String[] args_hdfs = {
					"localhost",
					"-t", "windows",
					"--wmi"
			};
			HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(args_hdfs);

			assertEquals("localhost", sentryCli.getHostname());
			assertEquals(HostType.MS_WINDOWS, sentryCli.getDeviceType());
			assertNotNull(sentryCli.getWmiConfigCli());
		}

	}

	@Test
	void winRmArgumentsTest() {
		{
			String[] args_hdfs = { "hostaa",
					"-t", "win",
					"--winrm-force-namespace", "root\\cimv2",
					"--winrm-username", "admin",
					"--winrm-password", "password",
					"--winrm-timeout", "160",
					"--winrm-port", "1234",
					"--winrm-transport", "HTTPS",
					"--winrm-auth", "kerberos"};
			HardwareSentryCli sentryCli = new HardwareSentryCli();
			CommandLine commandLine = new CommandLine(sentryCli);
			commandLine.setCaseInsensitiveEnumValuesAllowed(true);
			commandLine.parseArgs(args_hdfs);

			assertEquals("hostaa", sentryCli.getHostname());
			assertEquals(HostType.MS_WINDOWS, sentryCli.getDeviceType());
			assertEquals("root\\cimv2", sentryCli.getWinRmConfigCli().getNamespace());
			assertEquals("admin", sentryCli.getWinRmConfigCli().getUsername());
			assertArrayEquals("password".toCharArray(), sentryCli.getWinRmConfigCli().getPassword());
			assertEquals(160, sentryCli.getWinRmConfigCli().getTimeout());
			assertEquals(1234, sentryCli.getWinRmConfigCli().getPort());
			assertEquals(TransportProtocols.HTTPS, sentryCli.getWinRmConfigCli().getProtocol());
			assertEquals(1, sentryCli.getWinRmConfigCli().getAuthentications().size());
			assertEquals(AuthenticationEnum.KERBEROS, sentryCli.getWinRmConfigCli().getAuthentications().get(0));
		}

		{
			String[] args_hdfs = { "hostaa",
					"-t", "win",
					"--winrm-auth", "ntlm"};
			HardwareSentryCli sentryCli = new HardwareSentryCli();
			CommandLine commandLine = new CommandLine(sentryCli);
			commandLine.setCaseInsensitiveEnumValuesAllowed(true);
			commandLine.parseArgs(args_hdfs);

			assertEquals(1, sentryCli.getWinRmConfigCli().getAuthentications().size());
			assertEquals(AuthenticationEnum.NTLM, sentryCli.getWinRmConfigCli().getAuthentications().get(0));
		}

	}

	@Test
	void helpTest() throws Exception {
		{
			String result = executeCli(new String[] { "localhost", "-t", "win", "--wmi", "--help" });
			assertTrue(result.contains("Usage"));
		}
		{
			String result = executeCli(new String[] {});
			assertTrue(result.contains("Usage"));
		}
	}

	@Test
	void versionTest() throws Exception {
		String result = executeCli(new String[] { "--version" });
		assertTrue(result.contains("Copyright"));
		assertTrue(result.contains("Java version"));
		assertTrue(result.contains("Hardware Connector Library"));
	}

	@Test
	void listTest() throws Exception {
		String result = executeCli(new String[] { "--list" });
		assertTrue(result.contains("IBM"));
		assertTrue(result.split("\n").length > 100);
	}

	@Test
	void addSimpleTestConnector() throws Exception {
		String result = executeCli(new String[] { "localhost", "-t", "lin", "--snmp", "1", "--add", "src/test/resources/hdf/SimpleTest.hdfs", "-f", "SimpleTest" });
		assertTrue(result.contains("Simple Test"));
		assertTrue(result.contains("SimpleTest"));
		assertTrue(result.contains("CPU"));
		assertTrue(result.contains("Watercooling"));
		assertTrue(result.contains("500"));
		assertTrue(result.contains("status"));
		assertTrue(result.contains("Degraded"));
		assertTrue(result.contains("OK"));
		assertTrue(result.contains("speed"));
		assertTrue(result.contains("400"));
		assertTrue(result.contains("1500"));
	}

	@Test
	void addInvalidConnector() throws Exception {
		String result = executeCli(new String[] { "localhost", "-t", "lin", "--snmp", "1", "--add", "src/test/resources/hdf/Invalid.hdfs", "-f", "Invalid" });
		assertTrue(result.toLowerCase().contains("enclosure.discovery.invalidproperty"));
	}

	@Test
	void invalidSnmp() throws Exception {
		final String[] commonOptions = { "localhost", "-t", "lin", "--add", "src/test/resources/hdf/SimpleTest.hdfs", "-f", "SimpleTest" };
		{
			String result = executeCli(commonOptions, new String[] { "--snmp", "1", "--snmp-username", "test" });
			assertTrue(result.contains("Usage"));
		}
		{
			String result = executeCli(commonOptions, new String[] { "--snmp", "1", "--snmp-community", "" });
			assertTrue(result.contains("Usage"));
		}
		{
			String result = executeCli(commonOptions, new String[] { "--snmp", "1", "--snmp-community", "test" });
			assertFalse(result.contains("Usage"));
		}
		{
			String result = executeCli(commonOptions, new String[] { "--snmp", "2", "--snmp-username", "test" });
			assertTrue(result.contains("Usage"));
		}
		{
			String result = executeCli(commonOptions, new String[] { "--snmp", "2", "--snmp-community", "" });
			assertTrue(result.contains("Usage"));
		}
		{
			String result = executeCli(commonOptions, new String[] { "--snmp", "2", "--snmp-community", "test" });
			assertFalse(result.contains("Usage"));
		}
		{
			String result = executeCli(commonOptions, new String[] { "--snmp", "3", "--snmp-community", "public" });
			assertTrue(result.contains("Usage"));
		}
		{
			String result = executeCli(commonOptions, new String[] { "--snmp", "3md5" });
			assertTrue(result.contains("Usage"));
		}
		{
			String result = executeCli(commonOptions, new String[] { "--snmp", "3sha" });
			assertTrue(result.contains("Usage"));
		}

		{
			String result = executeCli(commonOptions, new String[] { "--snmp", "3sha", "--username", "user", "--password", "pass" });
			assertFalse(result.contains("Usage"));
		}
		{
			String result = executeCli(commonOptions, new String[] { "--snmp", "3sha", "--username", "user", "--password", "pass", "--snmp-privacy", "aes" });
			assertTrue(result.contains("Usage"));
		}

	}

	@Test
	void testTryInteractivePasswordsSnmp() {
		{
			// Not interactive (snmp-privacy-password)
			final String[] arguments = { "hostaa",
					"-t", "hpux",
					"--snmp", "v3",
					"--snmp-port", "200",
					"--snmp-community", "private",
					"--snmp-timeout", "60",
					"--snmp-username", "user",
					"--snmp-password", "*****",
					"--snmp-privacy", "AES", 
					"--snmp-privacy-password", "****"};
			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "*******".toCharArray());
			assertEquals("****", new String(sentryCli.getSnmpConfigCli().toProtocol(sentryCli.username, sentryCli.password).getPrivacyPassword()));

		}

		{
			// Interactive (snmp-privacy-password)
			final String[] arguments = { "hostaa",
					"-t", "hpux",
					"--snmp", "v3",
					"--snmp-port", "200",
					"--snmp-community", "private",
					"--snmp-timeout", "60",
					"--snmp-username", "user",
					"--snmp-password", "*****",
					"--snmp-privacy", "DES"};
			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "****".toCharArray());
			assertEquals("****", new String(sentryCli.getSnmpConfigCli().toProtocol(sentryCli.username, sentryCli.password).getPrivacyPassword()));

		}

		{
			// Not interactive (snmp-password)
			final String[] arguments = { "hostaa",
					"-t", "hpux",
					"--snmp", "v3",
					"--snmp-port", "200",
					"--snmp-community", "private",
					"--snmp-timeout", "60",
					"--snmp-username", "user",
					"--snmp-password", "*****",
					"--snmp-privacy", "AES", 
					"--snmp-privacy-password", "****"};
			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "*******".toCharArray());
			assertEquals("*****", new String(sentryCli.getSnmpConfigCli().toProtocol(sentryCli.username, sentryCli.password).getPassword()));

		}

		{
			// Interactive (snmp-password)
			final String[] arguments = { "hostaa",
					"-t", "hpux",
					"--snmp", "v3",
					"--snmp-port", "200",
					"--snmp-community", "private",
					"--snmp-timeout", "60",
					"--snmp-username", "user",
					"--snmp-privacy", "AES", 
					"--snmp-privacy-password", "****"};
			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "****".toCharArray());
			assertEquals("****", new String(sentryCli.getSnmpConfigCli().toProtocol(sentryCli.username, sentryCli.password).getPassword()));

		}

		{
			// Snmp v2c (no passwords)
			final String[] arguments = { "hostaa",
					"-t", "hpux",
					"--snmp", "v2c",
					"--snmp-port", "200",
					"--snmp-community", "private",
					"--snmp-timeout", "60"};
			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "****".toCharArray());
			assertNull(sentryCli.getSnmpConfigCli().toProtocol(sentryCli.username, sentryCli.password).getPassword());

		}
	}

	@Test
	void testTryInteractivePasswordsGlobal() {
		{
			// Interactive for the global password
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--username", "admin",
					"--winrm-force-namespace", "root\\cimv2",
					"--winrm-timeout", "160",
					"--winrm-port", "1234",
					"--winrm-transport", "https",
					"--winrm-auth", "kerberos"};

			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			final CommandLine commandLine = new CommandLine(sentryCli);
			commandLine.setCaseInsensitiveEnumValuesAllowed(true);
			commandLine.parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "****".toCharArray());
			final WinRmProtocol protocol = sentryCli.getWinRmConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertEquals("****", new String(protocol.getPassword()));
			assertEquals("admin", new String(protocol.getUsername()));
		}

		{
			// Not interactive as the password is provided
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--username", "admin",
					"--password", "****",
					"--winrm-force-namespace", "root\\cimv2",
					"--winrm-timeout", "160",
					"--winrm-port", "1234",
					"--winrm-transport", "https",
					"--winrm-auth", "kerberos"};

			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			final CommandLine commandLine = new CommandLine(sentryCli);
			commandLine.setCaseInsensitiveEnumValuesAllowed(true);
			commandLine.parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "***********".toCharArray());
			final WinRmProtocol protocol = sentryCli.getWinRmConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertEquals("****", new String(protocol.getPassword()));
			assertEquals("admin", new String(protocol.getUsername()));
		}
	}

	@Test
	void testTryInteractivePasswordsHttp() {
		{
			// Interactive
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--http",
					"--http-username", "admin",
					"--http-port", "4433",
					"--http-timeout", "160"};

			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "****".toCharArray());
			final HttpProtocol protocol = sentryCli.getHttpConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertEquals("****", new String(protocol.getPassword()));
		}

		{
			// Not interactive
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--http",
					"--http-username", "admin",
					"--http-password", "****",
					"--http-port", "4433",
					"--http-timeout", "160"};

			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "***********".toCharArray());
			final HttpProtocol protocol = sentryCli.getHttpConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertEquals("****", new String(protocol.getPassword()));
		}

		{
			// Not interactive because the username is not configured
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--http",
					"--http-port", "4433",
					"--http-timeout", "160"};

			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "***********".toCharArray());
			final HttpProtocol protocol = sentryCli.getHttpConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertNull(protocol.getPassword());
		}
	}

	@Test
	void testTryInteractivePasswordsWbem() {
		{
			// Interactive
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--wbem",
					"--wbem-username", "admin",
					"--wbem-port", "4433",
					"--wbem-timeout", "160"};

			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "****".toCharArray());
			final WbemProtocol protocol = sentryCli.getWbemConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertEquals("****", new String(protocol.getPassword()));
		}

		{
			// Not interactive
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--wbem",
					"--wbem-username", "admin",
					"--wbem-password", "****",
					"--wbem-port", "4433",
					"--wbem-timeout", "160"};

			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "*********".toCharArray());
			final WbemProtocol protocol = sentryCli.getWbemConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertEquals("****", new String(protocol.getPassword()));
		}

		{
			// Not interactive because the username is not configured
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--wbem",
					"--wbem-port", "4433",
					"--wbem-timeout", "160"};

			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "***********".toCharArray());
			final WbemProtocol protocol = sentryCli.getWbemConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertNull(protocol.getPassword());
		}
	}

	@Test
	void testTryInteractivePasswordsWmi() {
		{
			// Interactive
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--wmi",
					"--wmi-username", "admin",
					"--wmi-timeout", "160"};

			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "****".toCharArray());
			final WmiProtocol protocol = sentryCli.getWmiConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertEquals("****", new String(protocol.getPassword()));
		}

		{
			// Not interactive
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--wmi",
					"--wmi-username", "admin",
					"--wmi-password", "****",
					"--wmi-timeout", "160"};

			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "*********".toCharArray());
			final WmiProtocol protocol = sentryCli.getWmiConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertEquals("****", new String(protocol.getPassword()));
		}

		{
			// Not interactive because the username is not configured
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--wmi",
					"--wmi-timeout", "160"};

			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "*********".toCharArray());
			final WmiProtocol protocol = sentryCli.getWmiConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertNull(protocol.getPassword());
		}
	}

	@Test
	void testTryInteractivePasswordsWinRm() {
		{
			// Interactive
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--winrm",
					"--winrm-username", "admin",
					"--winrm-timeout", "160"};

			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "****".toCharArray());
			final WinRmProtocol protocol = sentryCli.getWinRmConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertEquals("****", new String(protocol.getPassword()));
		}

		{
			// Not interactive
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--winrm",
					"--winrm-username", "admin",
					"--winrm-password", "****",
					"--winrm-timeout", "160"};

			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "********".toCharArray());
			final WinRmProtocol protocol = sentryCli.getWinRmConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertEquals("****", new String(protocol.getPassword()));
		}

		{
			// Not interactive because the username is not configured
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--winrm",
					"--winrm-timeout", "160"};

			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "********".toCharArray());
			final WinRmProtocol protocol = sentryCli.getWinRmConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertNull(protocol.getPassword());
		}
	}

	@Test
	void testTryInteractivePasswordsSsh() {
		{
			// Interactive
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--ssh",
					"--ssh-username", "admin",
					"--ssh-timeout", "160"};

			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "****".toCharArray());
			final SshProtocol protocol = sentryCli.getSshConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertEquals("****", new String(protocol.getPassword()));
		}

		{
			// Not interactive
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--ssh",
					"--ssh-username", "admin",
					"--ssh-password", "****",
					"--ssh-timeout", "160"};

			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "**********".toCharArray());
			final SshProtocol protocol = sentryCli.getSshConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertEquals("****", new String(protocol.getPassword()));
		}

		{
			// Not interactive because the username is not configured
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--ssh",
					"--ssh-timeout", "160"};

			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "****".toCharArray());
			final SshProtocol protocol = sentryCli.getSshConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertNull(protocol.getPassword());
		}
	}

	@Test
	void testTryInteractivePasswordsIpmi() {
		{
			// Interactive
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--ipmi",
					"--ipmi-username", "admin",
					"--ipmi-timeout", "160"};
	
			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "****".toCharArray());
			final IpmiOverLanProtocol protocol = sentryCli.getIpmiConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertEquals("****", new String(protocol.getPassword()));
		}
	
		{
			// Not interactive
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--ipmi",
					"--ipmi-username", "admin",
					"--ipmi-password", "****",
					"--ipmi-timeout", "160"};
	
			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "**********".toCharArray());
			final IpmiOverLanProtocol protocol = sentryCli.getIpmiConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertEquals("****", new String(protocol.getPassword()));
		}
	
		{
			// Not interactive because the username is not configured
			final String[] arguments = { "hostaa",
					"-t", "win",
					"--ipmi",
					"--ipmi-timeout", "160"};
	
			final HardwareSentryCli sentryCli = new HardwareSentryCli();
			new CommandLine(sentryCli).parseArgs(arguments);
			sentryCli.tryInteractivePasswords((fmt, args) -> "**********".toCharArray());
			final IpmiOverLanProtocol protocol = sentryCli.getIpmiConfigCli().toProtocol(sentryCli.username, sentryCli.password);
			assertNull(protocol.getPassword());
		}
	}
}
