package org.sentrysoftware.metricshub.extension.win;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sentrysoftware.metricshub.engine.strategy.utils.OsCommandResult;

@ExtendWith(MockitoExtension.class)
class WinCommandServiceTest {

	@Mock
	IWinRequestExecutor winRequestExecutorMock;

	@InjectMocks
	WinCommandService winCommandService;

	private static final String HOST_NAME = "test-host" + UUID.randomUUID().toString();
	private static final String COMMAND_LINE =
		"systeminfo | findstr /C:\"Total Physical Memory\" /C:\"Available Physical Memory\"";
	private static final char[] PASSWORD = "pwd".toCharArray();
	private static final String USERNAME = "user";

	@Test
	void testGetUsername() {
		final IWinConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(120L)
			.build();
		assertEquals(Optional.empty(), winCommandService.getUsername(null));
		assertEquals(Optional.of(USERNAME), winCommandService.getUsername(wmiConfiguration));
		assertEquals(Optional.empty(), winCommandService.getUsername(WmiTestConfiguration.builder().build()));
	}

	@Test
	void testGetPassword() {
		final IWinConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(120L)
			.build();
		assertEquals(Optional.empty(), winCommandService.getPassword(null));
		assertEquals(Optional.of(PASSWORD), winCommandService.getPassword(wmiConfiguration));
		assertEquals(Optional.empty(), winCommandService.getPassword(WmiTestConfiguration.builder().build()));
	}

	@Test
	void testRunOsCommand() throws Exception {
		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(HOST_NAME + "\\" + USERNAME)
			.password(PASSWORD)
			.build();
		final String expectedToBeReturned = "value";
		doReturn(expectedToBeReturned)
			.when(winRequestExecutorMock)
			.executeWinRemoteCommand(HOST_NAME, wmiConfiguration, COMMAND_LINE, Collections.emptyList());

		final OsCommandResult result = winCommandService.runOsCommand(COMMAND_LINE, HOST_NAME, wmiConfiguration);
		assertEquals(new OsCommandResult(expectedToBeReturned, COMMAND_LINE), result);
	}
}
