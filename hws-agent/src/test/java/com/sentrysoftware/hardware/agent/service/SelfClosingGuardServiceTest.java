package com.sentrysoftware.hardware.agent.service;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SelfClosingGuardServiceTest {

	@Test
	void testRunStdinClosed() throws Exception {

		// End of the stream is detected
		final InputStream is = new InputStream() {

			@Override
			public int read() throws IOException {
				return -1;
			}
		};

		final InputStream origin = System.in;
		try {
			// Change the stdin temporarily for the test
			System.setIn(is);

			// Mock the service as we want to disable the System.exit
			final SelfClosingGuardService service = Mockito.spy(SelfClosingGuardService.class);

			// Disable System.exit
			doNothing().when(service).exit();

			// Continuously reads the stdin and stops the program if the end of the stream is reached
			service.start();

			// Make sure the thread generated by the SelfClosingGuardService terminates
			// correctly
			TimeUnit.MILLISECONDS.sleep(500);

			// Check that the exit method has been called
			verify(service, times(1)).exit();

		} finally {
			// Back to origin
			System.setIn(origin);
		}
	}

	@Test
	void testRunStdinNotClosedAndCharSent() throws Exception {

		// Stream character 'a'
		final InputStream is = new InputStream() {

			@Override
			public int read() throws IOException {
				return 'a';
			}
		};

		final InputStream origin = System.in;
		try {
			// Change the stdin temporarily for the test
			System.setIn(is);

			// Mock the service to be able to check the exit is never called
			final SelfClosingGuardService service = Mockito.spy(SelfClosingGuardService.class);

			// Continuously reads the stdin and stops the program if the end of the stream is reached
			service.start();

			// Wait a bit
			TimeUnit.MILLISECONDS.sleep(500);

			// Check that the exit method is never called means the program is alive ;)
			verify(service, never()).exit();

		} finally {
			// Back to origin
			System.setIn(origin);
		}
	}

}
