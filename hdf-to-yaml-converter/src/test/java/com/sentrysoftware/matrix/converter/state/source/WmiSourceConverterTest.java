package com.sentrysoftware.matrix.converter.state.source;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.sentrysoftware.matrix.converter.AbstractConnectorPropertyConverterTest;

class WmiSourceConverterTest extends AbstractConnectorPropertyConverterTest {

	@Override
	protected String getResourcePath() {
		return "src/test/resources/test-files/monitors/source/wmi";
	}

	@Test
	void test() throws IOException {
		testConversion("discovery");
		testConversion("collect");
		testConversion("executeForEachEntryOf");

		testAll();
	}
}
