package org.sentrysoftware.metricshub.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

class AwkCliTest {

	private final String SCRIPT_FILE_PATH = "src/test/resources/cli/script.awk";
	private final String INPUT_FILE_PATH = "src/test/resources/cli/input.txt";
	private final String WRONG_FILE_PATH = "wrong/path";
	private final String SCRIPT = "{ print \"Processed:\", $0 }";
	private final String INPUT =
		"""
			HELLO
			This
			is
			AWK
			CLI
		""";

	AwkCli awkCli;
	CommandLine commandLine;
	PrintWriter printWriter = new PrintWriter(new StringWriter());

	@BeforeEach
	void initCli() {
		awkCli = new AwkCli();
		commandLine = new CommandLine(awkCli);
	}

	@Test
	void testValidateScript() throws IOException {
		awkCli.setInput(INPUT);
		// Both script and script file specified
		awkCli.setScript(SCRIPT);
		awkCli.setScriptFile(SCRIPT_FILE_PATH);
		ParameterException parameterException = assertThrows(ParameterException.class, () -> awkCli.validateScript());
		assertTrue(parameterException.getMessage().contains("Conflict - Two scripts have been configured"));
		// No script
		awkCli.setScript(null);
		awkCli.setScriptFile(null);
		parameterException = assertThrows(ParameterException.class, () -> awkCli.validateScript());
		assertTrue(parameterException.getMessage().contains("At least one AWK script must be configured"));
		// Script specified
		awkCli.setScript(SCRIPT);
		assertDoesNotThrow(() -> awkCli.validateScript());
		// Script file path specified
		awkCli.setScript(null);
		awkCli.setScriptFile(SCRIPT_FILE_PATH);
		assertDoesNotThrow(() -> awkCli.validateScript());
		final String result;
		try (Stream<String> stream = Files.lines(Path.of(SCRIPT_FILE_PATH), StandardCharsets.UTF_8)) {
			result = stream.collect(Collectors.joining("\n"));
		}
		assertEquals(awkCli.getScriptContent(), result);
		// Wrong Script File path specified
		awkCli.setScript(null);
		awkCli.setScriptFile(WRONG_FILE_PATH);
		parameterException = assertThrows(ParameterException.class, () -> awkCli.validateScript());
		assertTrue(parameterException.getMessage().contains("Error while reading script file"));
	}

	@Test
	void testValidateInput() throws IOException {
		initCli();
		awkCli.setScript(SCRIPT);
		// Both input and input file specified
		awkCli.setInput(INPUT);
		awkCli.setInputFile(INPUT_FILE_PATH);
		ParameterException parameterException = assertThrows(ParameterException.class, () -> awkCli.validateInput());
		assertTrue(parameterException.getMessage().contains("Conflict - Two inputs have been configured"));
		// No input
		awkCli.setInput(null);
		awkCli.setInputFile(null);
		parameterException = assertThrows(ParameterException.class, () -> awkCli.validateInput());
		assertTrue(parameterException.getMessage().contains("At least one AWK input must be configured"));
		// Input specified
		awkCli.setInput(INPUT);
		assertDoesNotThrow(() -> awkCli.validateInput());
		// Input file path specified
		awkCli.setInput(null);
		awkCli.setInputFile(INPUT_FILE_PATH);
		assertDoesNotThrow(() -> awkCli.validateInput());
		final String result;
		try (Stream<String> stream = Files.lines(Path.of(INPUT_FILE_PATH), StandardCharsets.UTF_8)) {
			result = stream.collect(Collectors.joining("\n"));
		}
		assertEquals(awkCli.getInputContent(), result);
		// Wrong Input File path specified
		awkCli.setInput(null);
		awkCli.setInputFile(WRONG_FILE_PATH);
		parameterException = assertThrows(ParameterException.class, () -> awkCli.validateInput());
		assertTrue(parameterException.getMessage().contains("Error while reading input file"));
	}

	@Test
	void testPopulateScriptContent() throws IOException {
		awkCli.setScript(SCRIPT);
		awkCli.populateScriptContent();
		assertEquals(String.join("\n", SCRIPT), awkCli.getScriptContent());

		initCli();
		awkCli.setScriptFile(SCRIPT_FILE_PATH);
		awkCli.populateScriptContent();
		assertEquals(Files.readString(Path.of(SCRIPT_FILE_PATH)), awkCli.getScriptContent().concat("\n"));

		initCli();
		awkCli.setScriptFile(WRONG_FILE_PATH);
		assertThrows(IOException.class, () -> awkCli.populateScriptContent());
	}

	@Test
	void testPopulateInputContent() throws IOException {
		awkCli.setInput(INPUT);
		awkCli.populateInputContent();
		assertEquals(String.join("\n", INPUT), awkCli.getInputContent());

		initCli();
		awkCli.setInputFile(INPUT_FILE_PATH);
		awkCli.populateInputContent();
		assertEquals(Files.readString(Path.of(INPUT_FILE_PATH)), awkCli.getInputContent().concat("\n"));

		initCli();
		awkCli.setInputFile(WRONG_FILE_PATH);
		assertThrows(IOException.class, () -> awkCli.populateInputContent());
	}
}
