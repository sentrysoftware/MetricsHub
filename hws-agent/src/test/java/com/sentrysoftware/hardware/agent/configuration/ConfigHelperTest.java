package com.sentrysoftware.hardware.agent.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.sentrysoftware.hardware.agent.dto.HostConfigurationDTO;
import com.sentrysoftware.hardware.agent.dto.MultiHostsConfigurationDTO;
import com.sentrysoftware.hardware.agent.exception.BusinessException;
import com.sentrysoftware.matrix.engine.EngineConfiguration;
import com.sentrysoftware.matrix.engine.protocol.IProtocolConfiguration;
import com.sentrysoftware.matrix.engine.protocol.SNMPProtocol;
import com.sentrysoftware.matrix.engine.target.HardwareTarget;
import com.sentrysoftware.matrix.engine.target.TargetType;
import com.sentrysoftware.matrix.model.monitoring.IHostMonitoring;

@SpringBootTest
class ConfigHelperTest {

	private static final String DELL_OPEN_MANAGE_CONNECTOR = "DellOpenManage";
	private static final String SUN_F15K = "SunF15K";

	@Autowired
	private File configFile;

	@Test
	void testValidateAndGetConnectors() throws BusinessException {
		final Set<String> connectors = Set.of(DELL_OPEN_MANAGE_CONNECTOR, SUN_F15K);

		{
			assertTrue(ConfigHelper.validateAndGetConnectors(connectors, null, "hostname").isEmpty());
			assertTrue(ConfigHelper.validateAndGetConnectors(connectors, Collections.emptySet(), "hostname").isEmpty());
		}

		{
			assertEquals(
					Set.of(DELL_OPEN_MANAGE_CONNECTOR, SUN_F15K),
					ConfigHelper.validateAndGetConnectors(
						connectors,
						Set.of(DELL_OPEN_MANAGE_CONNECTOR, SUN_F15K),
						"hostname"
					)
			);

			assertEquals(Set.of(SUN_F15K),
					ConfigHelper.validateAndGetConnectors(connectors, Set.of(SUN_F15K), "hostname"));
		}

		{
			assertThrows(BusinessException.class, () -> 
				ConfigHelper.validateAndGetConnectors(
					connectors,
					Set.of("BadConnector", DELL_OPEN_MANAGE_CONNECTOR),
					"hostname"
				)
			);
			assertThrows(Exception.class,
					() -> ConfigHelper.validateAndGetConnectors(null, Collections.emptySet(), "hostname"));
		}

	}

	@Test
	void testBuildEngineConfiguration() throws BusinessException {

		final MultiHostsConfigurationDTO hostsConfigurations = ConfigHelper
				.readConfigurationSafe(configFile);

		final Set<String> selectedConnectors = Collections.singleton(DELL_OPEN_MANAGE_CONNECTOR);

		for (HostConfigurationDTO hostConfigurationDTO : hostsConfigurations.getTargets()) {

			EngineConfiguration actual = ConfigHelper.buildEngineConfiguration(hostConfigurationDTO, selectedConnectors,
					Collections.emptySet());

			Map<Class<? extends IProtocolConfiguration>, IProtocolConfiguration> protocolConfigurations = Map
					.of(SNMPProtocol.class, hostConfigurationDTO.getSnmp().toProtocol());

			HardwareTarget target = hostConfigurationDTO.getTarget().toHardwareTarget();
			if ("357306c9-07e9-431b-bc71-b7712daabbbf-1".equals(target.getId())) {
				target.setId("357306c9-07e9-431b-bc71-b7712daabbbf-1");
			} else {
				target.setId(target.getHostname());
			}

			EngineConfiguration expected = EngineConfiguration
					.builder()
					.operationTimeout(hostConfigurationDTO.getOperationTimeout())
					.protocolConfigurations(protocolConfigurations).selectedConnectors(selectedConnectors)
					.target(target)
					.build();

			assertEquals(expected, actual);
		}
	}

	@Test
	void testReadConfigurationSafeUnknownFile() {
		assertTrue(ConfigHelper.readConfigurationSafe(new File("unknownFile.yml")).isEmpty());
	}

	@Test
	void testBuildHostMonitoringMap() throws IOException {
		final MultiHostsConfigurationDTO multiHostsConfigurationDTO = ConfigHelper.deserializeYamlFile(configFile,
				MultiHostsConfigurationDTO.class);
		final Map<String, IHostMonitoring> hostMonitoringMap = ConfigHelper
				.buildHostMonitoringMap(multiHostsConfigurationDTO, Collections.singleton(DELL_OPEN_MANAGE_CONNECTOR));

		assertFalse(hostMonitoringMap.isEmpty());
	}

	@Test
	void testBuildHostMonitoringMapBadConfig() throws IOException {
		final MultiHostsConfigurationDTO multiHostsConfigurationDTO = ConfigHelper.deserializeYamlFile(configFile,
				MultiHostsConfigurationDTO.class);
		final Map<String, IHostMonitoring> hostMonitoringMap = ConfigHelper.buildHostMonitoringMap(
				multiHostsConfigurationDTO, Collections.singleton("StoreAcceptsOnlyThisConnector"));

		assertTrue(hostMonitoringMap.isEmpty());
	}

	@Test
	void testDeserializeYamlFile() throws IOException {
		assertNotNull(
				ConfigHelper.deserializeYamlFile(configFile, MultiHostsConfigurationDTO.class));
	}

	@Test
	void testValidateTarget() {
		assertThrows(BusinessException.class, () -> ConfigHelper.validateTarget(null, "hostname"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateTarget(TargetType.LINUX, ""));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateTarget(TargetType.LINUX, null));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateTarget(TargetType.LINUX, " 	"));
		assertDoesNotThrow(() -> ConfigHelper.validateTarget(TargetType.LINUX, "hostname"));
	}
	
	@Test
	void testValidateSnmp() {
		final char[] community = new char[] { 'p', 'u', 'b', 'l', 'i', 'c' };
		final char[] emptyCommunity = new char[] {};
		
		assertThrows(BusinessException.class, () -> ConfigHelper.validateSnmp("hostname", emptyCommunity, 1234, 60L));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateSnmp("hostname", null, 1234, 60L));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateSnmp("hostname", community, -1, 60L));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateSnmp("hostname", community, null, 60L));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateSnmp("hostname", community, 66666, 60L));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateSnmp("hostname", community, 1234, -60L));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateSnmp("hostname", community, 1234, null));
		assertDoesNotThrow(() -> ConfigHelper.validateSnmp("hostname", community, 1234, 60L));
	}
	
	@Test
	void testValidateIpmi() {
		final char[] password = new char[] { 'p', 'w'};
		final char[] emptyPassword = new char[] {};
		final byte[] bmcKey = new byte[] { 1, 1, 1};
		final byte[] emptyBmcKey = new byte[] {};
		
		assertThrows(BusinessException.class, () -> ConfigHelper.validateIpmi("hostname", "", password, bmcKey, 60L));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateIpmi("hostname", null, password, bmcKey, 60L));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateIpmi("hostname", "username", emptyPassword, bmcKey, 60L));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateIpmi("hostname", "username", null, bmcKey, 60L));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateIpmi("hostname", "username", password, emptyBmcKey, 60L));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateIpmi("hostname", "username", password, null, 60L));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateIpmi("hostname", "username", password, bmcKey, -60L));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateIpmi("hostname", "username", password, bmcKey, null));
		assertDoesNotThrow(() -> ConfigHelper.validateIpmi("hostname", "username", password, bmcKey, 60L));
	}
	
	@Test
	void testValidateSsh() {
		final char[] password = new char[] { 'p', 'w'};
		final char[] emptyPassword = new char[] {};
		
		assertThrows(BusinessException.class, () -> ConfigHelper.validateSsh("hostname", "", password, 60L, "sudo"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateSsh("hostname", null, password, 60L, "sudo"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateSsh("hostname", "username", emptyPassword, 60L, "sudo"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateSsh("hostname", "username", null, 60L, "sudo"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateSsh("hostname", "username", password, -60L, "sudo"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateSsh("hostname", "username", password, null, "sudo"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateSsh("hostname", "username", password, 60L, ""));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateSsh("hostname", "username", password, 60L, null));
		assertDoesNotThrow(() -> ConfigHelper.validateSsh("hostname", "username", password, 60L, "sudo"));
	}
	
	@Test
	void testValidateWbem() {
		final char[] password = new char[] { 'p', 'w'};
		final char[] emptyPassword = new char[] {};
		
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWbem("hostname", "", password, 60L, 1234, "root\\cimv2"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWbem("hostname", null, password, 60L, 1234, "root\\cimv2"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWbem("hostname", "username", emptyPassword, 60L, 1234, "root\\cimv2"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWbem("hostname", "username", null, 60L, 1234, "root\\cimv2"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWbem("hostname", "username", password, -60L, 1234, "root\\cimv2"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWbem("hostname", "username", password, null, 1234, "root\\cimv2"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWbem("hostname", "username", password, 60L, -1, "root\\cimv2"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWbem("hostname", "username", password, 60L, null, "root\\cimv2"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWbem("hostname", "username", password, 60L, 66666, "root\\cimv2"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWbem("hostname", "username", password, 60L, 1234, ""));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWbem("hostname", "username", password, 60L, 1234, null));
		assertDoesNotThrow(() -> ConfigHelper.validateWbem("hostname", "username", password, 60L, 1234, "root\\cimv2"));
	}
	
	@Test
	void testValidateWmi() {
		final char[] password = new char[] { 'p', 'w'};
		final char[] emptyPassword = new char[] {};
		
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWmi("hostname", "", password, 60L, "root\\cimv2"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWmi("hostname", null, password, 60L, "root\\cimv2"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWmi("hostname", "username", emptyPassword, 60L, "root\\cimv2"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWmi("hostname", "username", null, 60L, "root\\cimv2"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWmi("hostname", "username", password, -60L, "root\\cimv2"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWmi("hostname", "username", password, null, "root\\cimv2"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWmi("hostname", "username", password, 60L, ""));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateWmi("hostname", "username", password, 60L, null));
		assertDoesNotThrow(() -> ConfigHelper.validateWmi("hostname", "username", password, 60L, "root\\cimv2"));
	}
	
	@Test
	void testValidateHttp() {
		final char[] password = new char[] { 'p', 'w'};
		final char[] emptyPassword = new char[] {};
		
		assertThrows(BusinessException.class, () -> ConfigHelper.validateHttp("hostname", "", password, 60L, 1234));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateHttp("hostname", null, password, 60L, 1234));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateHttp("hostname", "username", emptyPassword, 60L, 1234));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateHttp("hostname", "username", null, 60L, 1234));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateHttp("hostname", "username", password, -60L, 1234));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateHttp("hostname", "username", password, null, 1234));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateHttp("hostname", "username", password, 60L, -1));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateHttp("hostname", "username", password, 60L, null));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateHttp("hostname", "username", password, 60L, 66666));
		assertDoesNotThrow(() -> ConfigHelper.validateHttp("hostname", "username", password, 60L, 1234));
	}
	
	@Test
	void testValidateOsCommand() {
		assertThrows(BusinessException.class, () -> ConfigHelper.validateOsCommand("hostname", -60L, "sudo"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateOsCommand("hostname", null, "sudo"));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateOsCommand("hostname", 60L, ""));
		assertThrows(BusinessException.class, () -> ConfigHelper.validateOsCommand("hostname", 60L, null));
		assertDoesNotThrow(() -> ConfigHelper.validateOsCommand("hostname", 60L, "sudo"));
	}
}
