package com.sentrysoftware.matrix.converter.state.detection.wbem;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.sentrysoftware.matrix.converter.AbstractConnectorPropertyConverterTest;

public class ConnectorWbemCriteriaTest extends AbstractConnectorPropertyConverterTest {

    @Override
    protected String getResourcePath() {
        return "src/test/resources/test-files/connector/detection/criteria/wbem";
    }
	
	@Test
	void test() throws IOException {
		testConversion("test");
		testConversion("testMany");

		testAll();
	}
}
