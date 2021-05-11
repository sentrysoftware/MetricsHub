package com.sentrysoftware.matrix.connector.parser.state.compute.add;

import com.sentrysoftware.matrix.connector.model.Connector;
import com.sentrysoftware.matrix.connector.model.monitor.HardwareMonitor;
import com.sentrysoftware.matrix.connector.model.monitor.MonitorType;
import com.sentrysoftware.matrix.connector.model.monitor.job.collect.Collect;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.Source;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Add;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Compute;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.DuplicateColumn;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.type.snmp.SNMPGetTableSource;
import com.sentrysoftware.matrix.connector.parser.state.compute.AbstractComputeParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddProcessorTest {

	private AbstractComputeParser typeProcessor;
	private AbstractComputeParser columnProcessor;

	private Connector connector;

	private static final String FOO = "FOO";
	private static final String ADD_COLLECT_TYPE_KEY = "enclosure.collect.source(1).compute(1).type";
	private static final String ADD_DISCOVERY_TYPE_KEY = "enclosure.discovery.source(1).compute(1).type";
	private static final String ADD_TYPE_VALUE = "Add";
	private static final String ADD_COLUMN_KEY = "enclosure.collect.source(1).compute(1).column";

	@BeforeEach
	void setUp() {

		typeProcessor = new TypeProcessor();
		columnProcessor = new ColumnProcessor();
		connector = new Connector();
	}

	@Test
	void testDetect() {

		assertFalse(typeProcessor.detect(null, null, null));
		assertFalse(typeProcessor.detect(null, FOO, null));
		assertFalse(typeProcessor.detect(FOO, FOO, null));
		assertFalse(typeProcessor.detect(ADD_COLLECT_TYPE_KEY, FOO, null));
		assertTrue(typeProcessor.detect(ADD_COLLECT_TYPE_KEY, ADD_TYPE_VALUE, null));

		assertThrows(IllegalArgumentException.class, () -> columnProcessor.detect(ADD_COLUMN_KEY, FOO, null));
		assertFalse(columnProcessor.detect(ADD_COLUMN_KEY, FOO, connector));

		// Source not null
		Source source = SNMPGetTableSource
			.builder()
			.index(1)
			.build();

		connector
			.getHardwareMonitors()
			.add(HardwareMonitor
				.builder()
				.type(MonitorType.ENCLOSURE)
				.collect(Collect
					.builder()
					.sources(Collections.singletonList(source))
					.build())
				.build());

		assertFalse(columnProcessor.detect(ADD_COLUMN_KEY, FOO, connector));

		// Source not null, source.getComputes() null
		source.setComputes(null);
		assertFalse(columnProcessor.detect(ADD_COLUMN_KEY, FOO, connector));

		// Source not null, source.getComputes() not null, Add not found, wrong Compute index
		Compute duplicateColumn = DuplicateColumn.builder().index(2).build();
		source.setComputes(Collections.singletonList(duplicateColumn));
		assertFalse(columnProcessor.detect(ADD_COLUMN_KEY, FOO, connector));

		// Source not null, source.getComputes() not null, Add found, wrong Compute index
		Add add = Add.builder().index(2).build();
		source.setComputes(Collections.singletonList(add));
		assertFalse(columnProcessor.detect(ADD_COLUMN_KEY, FOO, connector));

		// Source not null, source.getComputes() not null, Add found, correct Compute index
		add.setIndex(1);
		assertTrue(columnProcessor.detect(ADD_COLUMN_KEY, FOO, connector));
	}

	@Test
	void testParse() {

		assertThrows(IllegalArgumentException.class, () -> typeProcessor.parse(null, null, null));
		assertThrows(IllegalArgumentException.class, () -> typeProcessor.parse(FOO, null, null));
		assertThrows(IllegalArgumentException.class, () -> typeProcessor.parse(FOO, FOO, null));
		assertDoesNotThrow(() -> typeProcessor.parse(ADD_COLLECT_TYPE_KEY, ADD_TYPE_VALUE, connector));
	}

	@Test
	void testGetCompute() {

		// No Source found
		Matcher matcher = typeProcessor.getMatcher(ADD_COLLECT_TYPE_KEY);
		assertTrue(matcher.matches());
		assertNull(typeProcessor.getCompute(typeProcessor.getSource(matcher, connector),
			typeProcessor.getComputeIndex(matcher)));

		// Source found
		connector
			.getHardwareMonitors()
			.add(HardwareMonitor
				.builder()
				.type(MonitorType.ENCLOSURE)
				.collect(Collect
					.builder()
					.sources(Collections.singletonList(SNMPGetTableSource
						.builder()
						.index(1)
						.build()))
					.build())
				.build());

		assertNull(typeProcessor.getCompute(typeProcessor.getSource(matcher, connector),
			typeProcessor.getComputeIndex(matcher)));
	}

	@Test
	void testGetSource() {

		Matcher matcher = typeProcessor.getMatcher(ADD_DISCOVERY_TYPE_KEY);
		assertTrue(matcher.matches());

		// HardwareMonitor found, job is discovery, HardwareMonitor.getDiscovery() is null
		connector
			.getHardwareMonitors()
			.add(HardwareMonitor
				.builder()
				.type(MonitorType.ENCLOSURE)
				.build());

		assertNull(typeProcessor.getSource(matcher, connector));

		// HardwareMonitor found, job is collect, HardwareMonitor.getCollect() is not null,
		// HardwareMonitor.getCollect().getSources() is null
		matcher = typeProcessor.getMatcher(ADD_COLLECT_TYPE_KEY);
		assertTrue(matcher.matches());

		Collect collect = Collect.builder().build();
		collect.setSources(null);

		connector
			.getHardwareMonitors()
			.get(0)
			.setCollect(collect);

		assertNull(typeProcessor.getSource(matcher, connector));

		// HardwareMonitor found, job is collect, HardwareMonitor.getCollect() is not null,
		// HardwareMonitor.getCollect().getSources() is not null, wrong source index
		SNMPGetTableSource source = SNMPGetTableSource
			.builder()
			.index(2)
			.build();

		connector
			.getHardwareMonitors()
			.get(0)
			.getCollect()
			.setSources(Collections.singletonList(source));

		assertNull(typeProcessor.getSource(matcher, connector));
	}
}