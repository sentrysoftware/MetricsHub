package com.sentrysoftware.matrix.connector.deserializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.sentrysoftware.matrix.connector.model.Connector;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.IpmiSource;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.Source;

class PreDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/pre/";
	}

	@Test
	void testDeserializePre() throws IOException {
		final Connector connector = getConnector("pre");

		assertNotNull(connector);

		var pre = connector.getPre();

		assertTrue(
			pre instanceof LinkedHashMap,
			"pre are expected to be a LinkedHashMap."
		);

		final Map<String, Source> expected = new LinkedHashMap<String, Source>(
			Map.of("ipmiSource", new IpmiSource("ipmi", Collections.emptyList(), false, "${source::pre.ipmiSource}", null))
		);

		assertEquals(expected, pre);
	}

	@Test
	void testPreBlankSource() throws IOException {
		try {
			getConnector("preBlankSource");
			Assert.fail(IO_EXCEPTION_MSG);
		} catch (InvalidFormatException e) {
			final String message = "The source key referenced by 'pre' cannot be empty.";
			checkMessage(e, message);
		}
	}

	@Test
	void testPreNull() throws IOException {
		assertEquals(Collections.emptyMap(), getConnector("preNull").getPre());
	}
}
