package org.sentrysoftware.metricshub.cli;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import org.sentrysoftware.metricshub.cli.service.MetricsHubCliService;
import org.sentrysoftware.metricshub.cli.service.PrintExceptionMessageHandlerService;
import org.sentrysoftware.metricshub.engine.awk.AwkExecutor;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * CLI for executing AWK scripts with validation and support for various operations.
 */
@Data
@Command(name = "awkcli", description = "\nList of valid options: \n", footer = AwkCli.FOOTER, usageHelpWidth = 180)
public class AwkCli implements Callable<Integer> {

	/**
	 * Footer regrouping AWK CLI examples
	 */
	public static final String FOOTER =
		"""

		Examples:

		awkcli [--script <SCRIPT> or --script-file <SCRIPT FILE PATH>] [--input <INPUT> or --input-file <INPUT FILE PATH>]

		@|green # AWK request with an inline script and input.|@
		awkcli --script "{ print }" --input "AWK Input"

		@|green # AWK request with an script file and an input file.|@
		awkcli --script-file path/script.awk --input-file path/input.txt

		""";

	@Spec
	CommandSpec spec;

	@Option(names = "--script", order = 1, paramLabel = "SCRIPT", description = "AWK script to process and interpret.")
	private String script;

	@Option(
		names = { "--script-file", "--scriptfile" },
		order = 2,
		paramLabel = "SCRIPTFILE",
		description = "Path of the awk script file."
	)
	private String scriptFile;

	@Option(names = "--input", order = 3, paramLabel = "INPUT", description = "The input to modify via the AWK script.")
	private String input;

	@Option(
		names = { "--input-file", "--inputfile" },
		order = 4,
		paramLabel = "INPUTFILE",
		description = "Path of the awk input file."
	)
	private String inputFile;

	@Option(
		names = { "-h", "-?", "--help" },
		order = 10,
		usageHelp = true,
		description = "Shows this help message and exits"
	)
	boolean usageHelpRequested;

	@Option(names = "-v", order = 11, description = "Verbose mode (repeat the option to increase verbosity)")
	boolean[] verbose;

	PrintWriter printWriter;

	String scriptContent;

	String inputContent;

	/**
	 * Validates the AWK script configuration.
	 * Ensures that either `--script` or `--script-file` is provided, but not both.
	 *
	 * @throws ParameterException if no script or multiple scripts are specified.
	 */
	void validateScript() {
		if (script == null && scriptFile == null) {
			throw new ParameterException(
				spec.commandLine(),
				"At least one AWK script must be configured: --script or --script-file."
			);
		} else if (script != null && scriptFile != null) {
			throw new ParameterException(
				spec.commandLine(),
				"Conflict - Two scripts have been configured: --script and --script-file."
			);
		} else {
			try {
				populateScriptContent();
			} catch (IOException e) {
				throw new ParameterException(
					spec.commandLine(),
					String.format("Error while reading script file %s : %s", scriptFile, e)
				);
			}
		}
	}

	/**
	 * Validates the AWK input configuration.
	 * Ensures that either `--input` or `--input-file` is provided, but not both.
	 *
	 * @throws ParameterException if no input or multiple inputs are specified.
	 */
	void validateInput() {
		if (input == null && inputFile == null) {
			throw new ParameterException(
				spec.commandLine(),
				"At least one AWK input must be configured: --input or --input-file."
			);
		} else if (input != null && inputFile != null) {
			throw new ParameterException(
				spec.commandLine(),
				"Conflict - Two inputs have been configured: --input and --input-file."
			);
		} else {
			try {
				populateInputContent();
			} catch (IOException e) {
				throw new ParameterException(
					spec.commandLine(),
					String.format("Error while reading input file %s : %s", inputFile, e)
				);
			}
		}
	}

	/**
	 * Retrieves the script content to be executed.
	 * If the script is specified directly, it is assigned to the awkScriptContent.
	 * If a script file is specified, its contents are read and set to the awkScriptContent.
	 *
	 * @throws IOException if an error occurs while reading the awkScriptFile.
	 */
	void populateScriptContent() throws IOException {
		if (script != null) {
			scriptContent = script;
		} else if (scriptFile != null) {
			try (Stream<String> stream = Files.lines(Path.of(scriptFile), StandardCharsets.UTF_8)) {
				scriptContent = stream.collect(Collectors.joining("\n"));
			}
		}
	}

	/**
	 * Retrieves the input to modify using the AWK script.
	 * If the input is specified directly, it is assigned to the awkInputContent.
	 * If an input file is specified, its contents are read and set to the awkInputContent.
	 *
	 * @throws IOException if an error occurs while reading the awkInputFile.
	 */
	void populateInputContent() throws IOException {
		if (input != null) {
			inputContent = input;
		} else if (inputFile != null) {
			try (Stream<String> stream = Files.lines(Path.of(inputFile), StandardCharsets.UTF_8)) {
				inputContent = stream.collect(Collectors.joining("\n"));
			}
		}
	}

	/**
	 * Entry point for the AWK CLI application. Initializes necessary configurations,
	 * processes command line arguments, and executes the CLI.
	 *
	 * @param args The command line arguments passed to the application.
	 */
	public static void main(String[] args) {
		System.setProperty("log4j2.configurationFile", "log4j2-cli.xml");

		// Enable colors on Windows terminal
		AnsiConsole.systemInstall();

		final CommandLine cli = new CommandLine(new AwkCli());

		// Keep the below line commented for future reference
		// Using JAnsi on Windows breaks the output of Unicode (UTF-8) chars
		// It can be fixed using the below line... when running in Windows Terminal
		// and not CMD.EXE.
		// As this is poorly documented, we keep this for future improvement.
		// cli.setOut(new PrintWriter(AnsiConsole.out(), true, StandardCharsets.UTF_8)); // NOSONAR on commented code

		// Set the exception handler
		cli.setExecutionExceptionHandler(new PrintExceptionMessageHandlerService());

		// Allow case insensitive enum values
		cli.setCaseInsensitiveEnumValuesAllowed(true);

		// Allow case insensitive options
		cli.setOptionsCaseInsensitive(true);

		// Execute the command
		final int exitCode = cli.execute(args);

		// Cleanup Windows terminal settings
		AnsiConsole.systemUninstall();

		System.exit(exitCode);
	}

	@Override
	public Integer call() throws Exception {
		validateScript();

		validateInput();

		// Gets the output writer from the command line spec.
		printWriter = spec.commandLine().getOut();
		// Set the logger level
		MetricsHubCliService.setLogLevel(verbose);

		displayRequest();

		final long beginTime = System.currentTimeMillis();
		final String result = AwkExecutor.executeAwk(scriptContent, inputContent);
		final long executionTime = (System.currentTimeMillis() - beginTime);

		displayResult(result, executionTime);

		return CommandLine.ExitCode.OK;
	}

	/**
	 * Displays the details of an AWK execution request in a formatted manner.
	 */
	void displayRequest() {
		printWriter.println("Executing AWK:");

		printWriter.println(
			Ansi.ansi().fgBlue().bold().a("Script: \n").fgBrightBlack().a(scriptContent).reset().toString()
		);

		printWriter.println(Ansi.ansi().fgBlue().bold().a("Input:\n").fgBrightBlack().a(inputContent).reset().toString());

		printWriter.flush();
	}

	/**
	 * Prints the query result.
	 *
	 * @param result the query result
	 */
	void displayResult(final String result, final long executionTime) {
		printWriter.println(Ansi.ansi().fgBlue().bold().a("Result:\n").reset().a(result).toString());
		printWriter.println(Ansi.ansi().fgBlue().bold().a("Execution Time: ").reset().a(executionTime).a("ms").toString());
		printWriter.flush();
	}
}
