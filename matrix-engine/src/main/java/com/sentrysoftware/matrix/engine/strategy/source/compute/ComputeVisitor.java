package com.sentrysoftware.matrix.engine.strategy.source.compute;

import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.COLUMN_REGEXP;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.DEFAULT;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.NEW_LINE;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.TABLE_SEP;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.sentrysoftware.matrix.common.helpers.HardwareConstants;
import com.sentrysoftware.matrix.common.helpers.StringHelper;
import com.sentrysoftware.matrix.common.meta.parameter.state.IState;
import com.sentrysoftware.matrix.connector.model.Connector;
import com.sentrysoftware.matrix.connector.model.common.ConversionType;
import com.sentrysoftware.matrix.connector.model.common.EmbeddedFile;
import com.sentrysoftware.matrix.connector.model.common.TranslationTable;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.AbstractConcat;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.AbstractMatchingLines;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Add;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.And;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.ArrayTranslate;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Awk;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Compute;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Convert;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Divide;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.DuplicateColumn;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.ExcludeMatchingLines;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Extract;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.ExtractPropertyFromWbemPath;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Json2Csv;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.KeepColumns;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.KeepOnlyMatchingLines;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.LeftConcat;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Multiply;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.PerBitTranslation;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Replace;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.RightConcat;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Substract;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Substring;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Translate;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Xml2Csv;
import com.sentrysoftware.matrix.engine.strategy.matsya.MatsyaClientsExecutor;
import com.sentrysoftware.matrix.engine.strategy.source.SourceTable;
import com.sentrysoftware.matrix.engine.strategy.utils.FilterResultHelper;
import com.sentrysoftware.matrix.engine.strategy.utils.PslUtils;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class ComputeVisitor implements IComputeVisitor {

	private static final Pattern HEXA_PATTERN = Pattern.compile("^[0-9A-Fa-f]+$");

	private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

	private static final Pattern DOUBLE_PATTERN = Pattern.compile("\\d+(\\.\\d+)?");

	private static final Pattern COLUMN_PATTERN =  Pattern.compile(COLUMN_REGEXP, Pattern.CASE_INSENSITIVE);

	private static final String LOG_COMPUTE_KEY_SUFFIX_TEMPLATE = "%s.compute(%d)";

	private String sourceKey;

	@Getter
	@Setter
	private SourceTable sourceTable;

	@Setter
	private Connector connector;

	@Setter
	private String hostname;

	@Setter
	private MatsyaClientsExecutor matsyaClientsExecutor;

	private static final Function<ComputeValue, String> GET_VALUE_FROM_ROW = computeValue -> {
		if (computeValue.getColumnIndex() < computeValue.getRow().size()) {
			return computeValue.getRow().get(computeValue.getColumnIndex());
		}
		log.warn("Cannot get value at index {} from the row {}.", computeValue.getColumnIndex(), computeValue.getRow());
		return null;
	};

	private static final Function<ComputeValue, String> GET_VALUE = ComputeValue::getValue;

	private static final Map<Class<? extends Compute>, BiFunction<String, String, String>> MATH_FUNCTIONS_MAP;

	static {
		MATH_FUNCTIONS_MAP = Map.of(
			Add.class, (op1, op2) -> Double.toString(Double.parseDouble(op1) + Double.parseDouble(op2)),
			Substract.class, (op1, op2) -> Double.toString(Double.parseDouble(op1) - Double.parseDouble(op2)),
			Multiply.class, (op1, op2) -> Double.toString(Double.parseDouble(op1) * Double.parseDouble(op2)),
			Divide.class, (op1, op2) -> {
				Double op2Value = Double.parseDouble(op2);
				if (op2Value != 0) {
					return Double.toString(Double.parseDouble(op1) / op2Value);
				}
				return null;
			});
	}

	@Override
	@WithSpan("Compute Add Exec")
	public void visit(@SpanAttribute("compute.definition") final Add add) {

		if (add == null) {
			log.warn("Hostname {} - Compute Operation (Add) is null, the table remains unchanged.", hostname);
			return;
		}

		Integer columnIndex = add.getColumn();
		String operand2 = add.getAdd();

		if (columnIndex == null || operand2 == null ) {
			log.warn("Hostname {} - Arguments in Compute Operation (Add) : {} are wrong, the table remains unchanged.", hostname, add);
			return;
		}

		if (columnIndex < 1 ) {
			log.warn("Hostname {} - The index of the column to add cannot be < 1, the addition computation cannot be performed.", hostname);
			return;
		}

		performMathematicalOperation(add, columnIndex, operand2);
	}

	@Override
	@WithSpan("Compute ArrayTranslate Exec")
	public void visit(@SpanAttribute("compute.definition") final ArrayTranslate arrayTranslate) {

		if (arrayTranslate == null) {
			log.warn("Hostname {} - The Source (Array Translate) to visit is null, the array translate computation cannot be performed.", hostname);
			return;
		}

		TranslationTable translationTable = arrayTranslate.getTranslationTable();
		if (translationTable == null) {
			log.warn("Hostname {} - Translation Table is null, the array translate computation cannot be performed.", hostname);
			return;
		}

		Map<String, String> translations = translationTable.getTranslations();
		if (translations == null) {

			log.warn("Hostname {} - The Translation Map {} is null, the array translate computation cannot be performed.",
					hostname, translationTable.getName());

			return;
		}

		int column = arrayTranslate.getColumn();
		if (column < 1) {
			log.warn("Hostname {} - The column number to translate cannot be < 1, the translate computation cannot be performed.", 
					hostname);
			return;
		}

		int columnIndex = column - 1;

		String arraySeparator = arrayTranslate.getArraySeparator();
		if (arraySeparator == null || "|".equals(arraySeparator)) {
			arraySeparator = "\\|";
		}

		String resultSeparator = arrayTranslate.getResultSeparator();
		if (resultSeparator == null) {
			resultSeparator = "|";
		}

		final String defaultTranslation = translations.get(DEFAULT);

		List<List<String>> resultTable = new ArrayList<>();
		List<String> resultRow;
		for (List<String> row : sourceTable.getTable()) {

			if (columnIndex >= row.size()) {

				log.warn("Hostname {} - The index of the column is {} but the row size is {}, the translate computation cannot be performed.",
						hostname, column, row.size());

				return;
			}

			resultRow = new ArrayList<>(row);

			String arrayValue = row.get(columnIndex);
			if (arrayValue != null) {

				String[] splitArrayValue = arrayValue.split(arraySeparator);

				String translatedArrayValue = Arrays
					.stream(splitArrayValue)
					.map(value -> translations.getOrDefault(value.toLowerCase(), defaultTranslation))
					.filter(value -> value != null && !value.isBlank())
					.collect(Collectors.joining(resultSeparator));

				resultRow.set(columnIndex, translatedArrayValue);
			}

			resultTable.add(resultRow);
		}

		sourceTable.setTable(resultTable);
		sourceTable.setRawData(SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false));

	}

	@Override
	@WithSpan("Compute And Exec")
	public void visit(@SpanAttribute("compute.definition") final And and) {

		if (and == null) {
			log.warn("Hostname {} - Compute Operation (And) is null, the table remains unchanged.", hostname);
			return;
		}

		String operand2 = and.getAnd();

		if (and.getColumn() == null || operand2 == null) {
			log.warn("Hostname {} - Arguments in Compute Operation (And) : {} are wrong, the table remains unchanged.", 
					hostname, and);
			return;
		}

		int columnIndex = and.getColumn() - 1;

		if (columnIndex < 0) {
			log.warn("Hostname {} - The index of the column to which apply the And operation cannot be < 1, the And computation cannot be performed.", 
					hostname);
			return;
		}

		int colOperand2 = getColumnIndex(operand2);

		for (List<String> line : sourceTable.getTable()) {
			try {
				if (columnIndex < line.size()) {
					line.set(columnIndex, String.valueOf((long) Double.parseDouble(line.get(columnIndex))
							& (colOperand2 == -1 ? (long) Double.parseDouble(operand2) : (long) Double.parseDouble(line.get(colOperand2)))
							));
				}
			} catch (NumberFormatException e) {
				log.warn("Hostname {} - Data is not correctly formatted.", hostname);
			}
		}

		sourceTable.setRawData(SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false));
	}

	@Override
	@WithSpan("Compute Awk Exec")
	public void visit(@SpanAttribute("compute.definition") final Awk awk) {

		if (awk == null) {
			log.warn("Hostname {} - Compute Operation (Awk) is null, the table remains unchanged.", hostname);
			return;
		}

		EmbeddedFile awkScript = awk.getAwkScript();
		if (awkScript == null) {
			log.warn("Hostname {} - Compute Operation (Awk) script {} has not been set correctly, the table remains unchanged.", 
					hostname, awk);
			return;
		}

		String input = (sourceTable.getRawData() == null || sourceTable.getRawData().isEmpty())
				? SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, true)
				: sourceTable.getRawData();

		String computeKey = String.format(LOG_COMPUTE_KEY_SUFFIX_TEMPLATE, sourceKey, awk.getIndex());

		log.debug("Hostname {} - Compute Operation [{}]. AWK Script:\n{}\n", hostname, computeKey, awkScript.getContent());

		try {

			String awkResult = matsyaClientsExecutor.executeAwkScript(awkScript.getContent(), input);

			if (awkResult == null || awkResult.isEmpty()) {
				log.warn("Hostname {} - {} Compute Operation (Awk) result is {}, the table will be empty.", 
						hostname, computeKey, (awkResult == null ? "null" : "empty"));
				sourceTable.setTable(Collections.emptyList());
				return;
			}

			final List<String> lines = SourceTable.lineToList(awkResult, HardwareConstants.NEW_LINE);

			final List<String> filterLines = FilterResultHelper.filterLines(
					lines,
					null,
					null,
					awk.getExcludeRegExp(),
					awk.getKeepOnlyRegExp());

			if (awk.getSeparators() == null || awk.getSeparators().isEmpty()) {
				log.info("Hostname {} - No separators indicated in Awk operation, the result remains unchanged.", 
						hostname);
			}

			final List<String> awkResultLines = FilterResultHelper.selectedColumns(
					filterLines,
					awk.getSeparators(),
					awk.getSelectColumns());
			awkResult = awkResultLines.stream()
					// add the TABLE_SEP at the end of each lines.
					.map(line -> line.endsWith(TABLE_SEP) ? line : line + TABLE_SEP)
					.collect(Collectors.joining(NEW_LINE));

			sourceTable.setRawData(awkResult);
			sourceTable.setTable(SourceTable.csvToTable(awkResult, TABLE_SEP));

		} catch (Exception e) {
			logComputeError(connector.getCompiledFilename(), computeKey, "AWK: " + awkScript.description(), e, hostname);
		}
	}

	@Override
	@WithSpan("Compute Convert Exec")
	public void visit(@SpanAttribute("compute.definition") final Convert convert) {

		if (!checkConvert(convert)) {
			log.warn("Hostname {} - The convert {} is not valid, the table remains unchanged.",
					hostname, convert);
			return;
		}

		final Integer columnIndex = convert.getColumn() - 1;
		final ConversionType conversionType = convert.getConversionType();

		if (ConversionType.HEX_2_DEC.equals(conversionType)) {
			convertHex2Dec(columnIndex);
		} else if (ConversionType.ARRAY_2_SIMPLE_STATUS.equals(conversionType)) {
			convertArray2SimpleStatus(columnIndex);
		}

	}

	/**
	 * Covert the array located in the cell indexed by columnIndex to a simple status OK, WARN, ALARM or UNKNOWN
	 *
	 * @param columnIndex The column number
	 */
	void convertArray2SimpleStatus(final Integer columnIndex) {
		sourceTable.getTable().forEach(row ->
			{
				if (columnIndex < row.size()) {
					final String value = PslUtils.nthArg(row.get(columnIndex), "1-", "|", "\n");
					row.set(columnIndex, getWorstStatus(value.split("\n")));
				}

				log.warn("Hostname {} - Could not perform Array2SimpleStatus conversion compute on row {} at index {}.", 
						hostname, row, columnIndex);
			});
		sourceTable.setRawData(SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false));
	}

	/**
	 * Get the worst status of the given values. Changing this method requires an update on the {@link IState}
	 * implementations
	 *
	 * @param values The array of string statuses to check, expected values are 'OK', 'WARN', 'ALARM'
	 *
	 * @return String value: OK, WARN, ALARM or UNKNOWN
	 */
	static String getWorstStatus(final String[] values) {
		String status = "UNKNOWN";
		for (final String value : values) {
			if ("ALARM".equalsIgnoreCase(value)) {
				return "ALARM";
			} else if ("WARN".equalsIgnoreCase(value)) {
				status = "WARN";
			} else if ("OK".equalsIgnoreCase(value) && "UNKNOWN".equals(status)) {
				status ="OK";
			}
		}

		return status;
	}

	/**
	 * Convert the column value at the given columnIndex from hexadecimal to decimal
	 *
	 * @param columnIndex The column number
	 */
	void convertHex2Dec(final Integer columnIndex) {
		sourceTable.getTable().forEach(row ->
			{
				if (columnIndex < row.size()) {
					final String value = row.get(columnIndex).replace("0x", "")
							.replace(":", "")
							.replaceAll("\\s*", "");
					if (HEXA_PATTERN.matcher(value).matches()) {
						row.set(columnIndex, String.valueOf(Long.parseLong(value, 16)));
						return;
					}
				}

				log.warn("Hostname {} - Could not perform Hex2Dec conversion compute on row {} at index {}.", hostname, row, columnIndex);
			});
		sourceTable.setRawData(SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false));
	}

	/**
	 * Check the given {@link Convert} instance
	 *
	 * @param convert The instance we wish to check
	 * @return <code>true</code> if the {@link Convert} instance is valid
	 */
	static boolean checkConvert(final Convert convert) {
		return convert != null
				&& convert.getColumn() != null
				&& convert.getColumn() >= 1
				&& convert.getConversionType() != null;
	}

	@Override
	@WithSpan("Compute Divide Exec")
	public void visit(@SpanAttribute("compute.definition") final Divide divide) {
		
		if (divide == null) {
			log.warn("Hostname {} - Compute Operation (Divide) is null, the table remains unchanged.", hostname);
			return;
		}

		if (divide.getColumn() == null || divide.getDivideBy() == null) {
			log.warn("Hostname {} - Arguments in Compute Operation (Divide) : {} are wrong, the table remains unchanged.", 
					hostname, divide);
			return;
		}

		Integer columnIndex = divide.getColumn();
		String divideBy = divide.getDivideBy();

		if (columnIndex < 1) {
			log.warn("Hostname {} - The index of the column to divide cannot be < 1, the division computation cannot be performed.",
					hostname);
			return;
		}

		performMathematicalOperation(divide, columnIndex, divideBy);

	}

	@Override
	@WithSpan("Compute DuplicateColumn Exec")
	public void visit(@SpanAttribute("compute.definition") final DuplicateColumn duplicateColumn) {

		if (duplicateColumn == null) {
			log.warn("Hostname {} - Duplicate Column object is null, the table remains unchanged.", hostname);
			return;
		}

		if (duplicateColumn.getColumn() == null || duplicateColumn.getColumn() == 0) {
			log.warn("Hostname {} - The column index in DuplicateColumn cannot be null or 0, the table remains unchanged.", hostname);
			return;
		}

		// for each list in the list, duplicate the column of the given index
		int columnIndex = duplicateColumn.getColumn() -1;

		for (List<String> elementList : sourceTable.getTable()) {
			if (columnIndex >= 0 && columnIndex < elementList.size()) {
				elementList.add(columnIndex, elementList.get(columnIndex));
			}
		}
		sourceTable.setRawData(SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false));
	}

	@Override
	@WithSpan("Compute ExcludeMatchingLines Exec")
	public void visit(@SpanAttribute("compute.definition") final ExcludeMatchingLines excludeMatchingLines) {

		processAbstractMatchingLines(excludeMatchingLines);
	}

	@Override
	@WithSpan("Compute Extract Exec")
	public void visit(@SpanAttribute("compute.definition") final Extract extract) {

		if (extract == null) {
			log.warn("Hostname {} - Extract object is null, the table remains unchanged.", hostname);
			return;
		}

		Integer column = extract.getColumn();
		if (column == null || column < 1) {
			log.warn("Hostname {} - The column number in Extract cannot be {}, the table remains unchanged.", hostname, column);
			return;
		}

		Integer subColumn = extract.getSubColumn();
		if (subColumn == null || subColumn < 1) {
			log.warn("Hostname {} - The sub-column number in Extract cannot be {}, the table remains unchanged.", hostname, subColumn);
			return;
		}

		String subSeparators = extract.getSubSeparators();
		if (subSeparators == null || subSeparators.isEmpty()) {
			log.warn("Hostname {} - The sub-columns separators in Extract cannot be null or empty, the table remains unchanged.",
					hostname);
			return;
		}

		int columnIndex = column - 1;

		String text;
		List<List<String>> resultTable = new ArrayList<>();
		List<String> resultRow;
		for (List<String> row : sourceTable.getTable()) {

			if (columnIndex >= row.size()) {
				log.warn("Hostname {} - Invalid column index: {}. The table remains unchanged.", hostname, column);
				return;
			}

			text = row.get(columnIndex);
			if (text == null) {
				log.warn("Hostname {} - Value at column {} cannot be null, the table remains unchanged.", hostname, column);
				return;
			}

			String extractedValue = PslUtils.nthArgf(text, String.valueOf(subColumn), subSeparators, null);

			resultRow = new ArrayList<>(row);
			resultRow.set(columnIndex, extractedValue);

			resultTable.add(resultRow);
		}

		sourceTable.setTable(resultTable);
		sourceTable.setRawData(SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false));

	}

	@Override
	@WithSpan("Compute ExtractPropertyFromWbemPath Exec")
	public void visit(@SpanAttribute("compute.definition") final ExtractPropertyFromWbemPath extractPropertyFromWbemPath) {

		if (extractPropertyFromWbemPath == null) {
			log.warn("Hostname {} - Compute Operation (ExtractPropertyFromWbemPath) is null, the table remains unchanged.", hostname);
			return;
		}

		Integer columnIndex = extractPropertyFromWbemPath.getColumn();
		String propertyName = extractPropertyFromWbemPath.getPropertyName();

		if (columnIndex == null || columnIndex < 1 || propertyName == null) {

			log.warn("Hostname {} - Arguments in Compute Operation (ExtractPropertyFromWbemPath) : {} are wrong, the table remains unchanged.",
				hostname, extractPropertyFromWbemPath);

			return;
		}

		columnIndex--;

		for (List<String> line : sourceTable.getTable()) {

			if (columnIndex < line.size()) {
				String columnValue = line.get(columnIndex);

				for (String objectPath : columnValue.split(",")) {
					// objectPath here should look like "<key>=<value>".
					String[] splitedValue = objectPath.split("=", 2);

					String key = splitedValue[0];
					/*
					 * The key we are looking for can either be like "<propertyName>" or "<systemName>.<propertyName>".
					 * So we add a dot at the beginning of the key and see if it ends with ".<propertyName>".
					 */
					if (key.length() >= propertyName.length()
							&& ".".concat(key).toLowerCase().endsWith(".".concat(propertyName).toLowerCase())) {
						line.set(columnIndex, splitedValue[1].replace("\"", "").trim());
						break;
					}
				}
			}
		}
		sourceTable.setRawData(SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false));
	}

	@Override
	@WithSpan("Compute Json2Csv Exec")
	public void visit(@SpanAttribute("compute.definition") final Json2Csv json2csv) {

		if (json2csv == null) {
			log.warn("Hostname {} - Compute Operation (Json2CSV) is null, the table remains unchanged.", hostname);
			return;
		}

		try {
			String json2csvResult = matsyaClientsExecutor.executeJson2Csv(
					sourceTable.getRawData(), json2csv.getEntryKey(), json2csv.getProperties(), json2csv.getSeparator());

			if (json2csvResult != null && !json2csvResult.isEmpty()) {
				sourceTable.setRawData(json2csvResult);
				sourceTable.setTable(SourceTable.csvToTable(json2csvResult, json2csv.getSeparator()));
			}
		} catch (Exception e) {
			logComputeError(connector.getCompiledFilename(),
					String.format(LOG_COMPUTE_KEY_SUFFIX_TEMPLATE, sourceKey, json2csv.getIndex()),
					"Json2CSV",
					e,
					hostname
			);
		}

	}

	@Override
	@WithSpan("Compute KeepColumns Exec")
	public void visit(@SpanAttribute("compute.definition") final KeepColumns keepColumns) {

		if (keepColumns == null) {
			log.warn("Hostname {} - KeepColumns object is null, the table remains unchanged.", hostname);
			return;
		}

		List<Integer> columnNumbers = keepColumns.getColumnNumbers();
		if (columnNumbers == null || columnNumbers.isEmpty()) {
			log.warn("Hostname {} - The column number list in KeepColumns cannot be null or empty. The table remains unchanged.",
					hostname);
			return;
		}

		List<List<String>> resultTable = new ArrayList<>();
		List<String> resultRow;
		columnNumbers = columnNumbers.stream().filter(Objects::nonNull).sorted().collect(Collectors.toList());
		for (List<String> row : sourceTable.getTable()) {

			resultRow = new ArrayList<>();
			for (Integer columnIndex : columnNumbers) {

				if (columnIndex < 1 || columnIndex > row.size()) {

					log.warn("Hostname {} - Invalid index for a {}-sized row: {}. The table remains unchanged.",
						hostname, row.size(), columnIndex);

					return;
				}

				resultRow.add(row.get(columnIndex - 1));
			}

			resultTable.add(resultRow);
		}

		sourceTable.setTable(resultTable);
		sourceTable.setRawData(SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false));
	}

	@Override
	@WithSpan("Compute KeepOnlyMatchingLines Exec")
	public void visit(@SpanAttribute("compute.definition") final KeepOnlyMatchingLines keepOnlyMatchingLines) {

		processAbstractMatchingLines(keepOnlyMatchingLines);
	}

	/**
	 * Updates the {@link SourceTable}
	 * by keeping or removing lines
	 * according to the definition of the given {@link AbstractMatchingLines}.
	 *
	 * @param abstractMatchingLines	The {@link AbstractMatchingLines}
	 *                              describing the rules
	 *                              regarding which lines should be kept or removed in/from the {@link SourceTable}.
	 */
	private void processAbstractMatchingLines(AbstractMatchingLines abstractMatchingLines) {

		if (abstractMatchingLines != null
				&& abstractMatchingLines.getColumn() != null
				&& abstractMatchingLines.getColumn() > 0
				&& sourceTable != null
				&& sourceTable.getTable() != null
				&& !sourceTable.getTable().isEmpty()) {

			int columnIndex = abstractMatchingLines.getColumn() - 1;

			String pslRegexp = abstractMatchingLines.getRegExp();
			Set<String> valueSet = abstractMatchingLines.getValueSet();

			final Predicate<String> pslPredicate = pslRegexp != null && !pslRegexp.isEmpty() ?
					getPredicate(pslRegexp, abstractMatchingLines) : null;

			final Predicate<String> valuePredicate = valueSet != null && !valueSet.isEmpty() ?
					getPredicate(valueSet, abstractMatchingLines) : null;

			// If there are both a regex and a valueList, both are applied, one after the other.
			final List<List<String>> filteredTable = sourceTable.getTable().stream()
					.filter(line -> columnIndex < line.size() &&
							(pslPredicate == null || pslPredicate.test(line.get(columnIndex))) &&
							(valuePredicate == null || valuePredicate.test(line.get(columnIndex))))
					.collect(Collectors.toList());

			sourceTable.setTable(filteredTable);
			sourceTable.setRawData(SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false));
		}
	}

	/**
	 * @param pslRegexp				The PSL regular expression used to filter the lines in the {@link SourceTable}.
	 * @param abstractMatchingLines	The {@link AbstractMatchingLines}
	 *                              describing the rules
	 *                              regarding which lines should be kept or removed in/from the {@link SourceTable}.
	 *
	 * @return						A {@link Predicate},
	 * 								based on the given regular expression
	 * 								and the concrete type of the given {@link AbstractMatchingLines},
	 * 								that can be used to filter the lines in the {@link SourceTable}.
	 */
	private Predicate<String> getPredicate(String pslRegexp, AbstractMatchingLines abstractMatchingLines) {

		Pattern pattern = Pattern.compile(PslUtils.psl2JavaRegex(pslRegexp), Pattern.CASE_INSENSITIVE);

		return abstractMatchingLines instanceof KeepOnlyMatchingLines
			? value -> pattern.matcher(value).find()
			: value -> !pattern.matcher(value).find();
	}

	/**
	 * @param valueSet				The set of values used to filter the lines in the {@link SourceTable}.
	 * @param abstractMatchingLines	The {@link AbstractMatchingLines}
	 *                              describing the rules
	 *                              regarding which lines should be kept or removed in/from the {@link SourceTable}.
	 *
	 * @return						A {@link Predicate},
	 * 								based on the given list of values
	 * 								and the concrete type of the given {@link AbstractMatchingLines},
	 * 								that can be used to filter the lines in the {@link SourceTable}.
	 */
	private Predicate<String> getPredicate(Set<String> valueSet, AbstractMatchingLines abstractMatchingLines) {

		return abstractMatchingLines instanceof KeepOnlyMatchingLines ?
				value -> value != null && valueSet.contains(value) :
					value -> value != null && !valueSet.contains(value);
	}

	@Override
	@WithSpan("Compute LeftConcat Exec")
	public void visit(@SpanAttribute("compute.definition") final LeftConcat leftConcat) {

		processAbstractConcat(leftConcat);
	}

	private void processAbstractConcat(AbstractConcat abstractConcat) {

		boolean firstChecks = abstractConcat != null && abstractConcat.getString() != null
				&& abstractConcat.getColumn() != null && abstractConcat.getColumn() > 0 && sourceTable != null
				&& sourceTable.getTable() != null && !sourceTable.getTable().isEmpty();

		if (firstChecks) {

			// Case 1 : concatenation with an exiting column
			if (abstractConcat.getColumn() <= sourceTable.getTable().get(0).size()) {

				int columnIndex = abstractConcat.getColumn() - 1;
				String concatString = abstractConcat.getString();

				// If abstractConcat.getString() is like "Column(n)",
				// we concat the column n instead of abstractConcat.getString()
				Matcher matcher = COLUMN_PATTERN.matcher(concatString);
				if (matcher.matches()) {

					int concatColumnIndex = Integer.parseInt(matcher.group(1)) - 1;
					if (concatColumnIndex < sourceTable.getTable().get(0).size()) {

						sourceTable.getTable()
								.forEach(line -> concatColumns(line, columnIndex, concatColumnIndex, abstractConcat));
					}

				} else {

					sourceTable.getTable().forEach(line -> concatString(line, columnIndex, abstractConcat));

					// Serialize and deserialize
					// in case the String to concat contains a ';'
					// so that a new column is created.
					if (concatString.contains(TABLE_SEP)) {

						sourceTable.setTable(SourceTable.csvToTable(
								SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false), TABLE_SEP));
					}
				}
			}

			// Case 2 : concatenation with non existing column
			else if (abstractConcat.getColumn() == sourceTable.getTable().get(0).size() + 1) {
				// add at the end of the list (or at the beginning if the list is empty)
				sourceTable.getTable()
				.forEach(line -> line.add(abstractConcat.getString()));
			}
			sourceTable.setRawData(SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false));
		}
	}

	/**
	 * Concatenates the values at <i>columnIndex</i> and <i>concatColumnIndex</i> on the given line,
	 * and stores the result at <i>columnIndex</i>.<br>
	 *
	 * Whether the value at <i>concatColumnIndex</i> goes to the left or to the right of the value at <i>columnIndex</i>
	 * depends on the type of the given {@link AbstractConcat}.
	 *
	 * @param line				The line on which the concatenation will be performed.
	 * @param columnIndex		The index of the column
	 *                          holding the value that should be concatenated to the value at <i>concatColumnIndex</i>.
	 *                          The result will be stored at <i>columnIndex</i>.
	 * @param concatColumnIndex	The index of the column
	 *                          holding the value that should be concatenated to the value at <i>columnIndex</i>.
	 * @param abstractConcat	The {@link AbstractConcat} used to determine
	 *                          whether the concatenation should be a left concatenation or a right concatenation.
	 */
	private void concatColumns(List<String> line, int columnIndex, int concatColumnIndex,
							   AbstractConcat abstractConcat) {

		String result = abstractConcat instanceof LeftConcat
			? line.get(concatColumnIndex).concat(line.get(columnIndex))
			: line.get(columnIndex).concat(line.get(concatColumnIndex));

		line.set(columnIndex, result);
	}

	/**
	 * Concatenates the value at <i>columnIndex</i> on the given line
	 * with the given {@link AbstractConcat}'s <i>getString()</i> value,
	 * and stores the result at <i>columnIndex</i>.<br>
	 *
	 * Whether {@link AbstractConcat#getString()} goes to the left or to the right of the value at <i>columnIndex</i>
	 * depends on the type of {@link AbstractConcat}.
	 *
	 * @param line				The line on which the concatenation will be performed.
	 * @param columnIndex		The index of the column
	 *                          holding the value that should be concatenated to {@link AbstractConcat#getString()}.
	 *                          The result will be stored at <i>columnIndex</i>.
	 * @param abstractConcat	The {@link AbstractConcat} used to determine
	 *                          whether the concatenation should be a left concatenation or a right concatenation.
	 */
	private void concatString(List<String> line, int columnIndex, AbstractConcat abstractConcat) {

		String result = abstractConcat instanceof LeftConcat
				? abstractConcat.getString().concat(line.get(columnIndex))
				: line.get(columnIndex).concat(abstractConcat.getString());

		line.set(columnIndex, result);
	}

	@Override
	@WithSpan("Compute Multiply Exec")
	public void visit(@SpanAttribute("compute.definition") final Multiply multiply) {
		
		if (multiply == null) {
			log.warn("Hostname {} - Compute Operation (Multiply) is null, the table remains unchanged.", hostname);
			return;
		}

		Integer columnIndex = multiply.getColumn();
		String operand2 = multiply.getMultiplyBy();

		if (columnIndex == null || operand2 == null ) {
			log.warn("Hostname {} - Arguments in Compute Operation (Multiply) : {} are wrong, the table remains unchanged.", 
					hostname, multiply);
			return;
		}

		if (columnIndex < 1 ) {
			log.warn("Hostname {} - The index of the column to multiply cannot be < 1, the multiplication computation cannot be performed.",
					hostname);
			return;
		}

		performMathematicalOperation(multiply, columnIndex, operand2);
	}

	@Override
	@WithSpan("Compute PerBitTranslation Exec")
	public void visit(@SpanAttribute("compute.definition") final PerBitTranslation perBitTranslation) {

		if (!perBitTranslationCheck(perBitTranslation)) {
			return;
		}

		Map<String, String> translations = perBitTranslation.getBitTranslationTable().getTranslations();
		int columnIndex = perBitTranslation.getColumn() - 1;
		List<Integer> bitList = perBitTranslation.getBitList();

		for (List<String> line : sourceTable.getTable()) {

			if (columnIndex < line.size()) {

				long valueToBeReplacedLong;

				try {
					// The integer value could be modified and converted to a double by a prior compute
					valueToBeReplacedLong = (long) Double.parseDouble(line.get(columnIndex));
				} catch (NumberFormatException e) {
					log.warn("Hostname {} - Data is not correctly formatted.", hostname);
					return;
				}

				final String columnResult = bitList.stream()
						.map(bit -> String.format("%d,%d", bit, ((int) Math.pow(2, bit) & valueToBeReplacedLong) != 0 ? 1 : 0))
						.map(translations::get)
						.filter(value -> value != null && !value.isBlank())
						.collect(Collectors.joining(" - "));

				line.set(columnIndex, columnResult);
			}
		}
		sourceTable.setRawData(SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false));
	}


	/**
	 * PerBitTranslation visit check.
	 *
	 * @param perBitTranslation	The {@link PerBitTranslation} being checked.
	 *
	 * @return					<b>true</b> if the given {@link PerBitTranslation} is well-formed.<br>
	 * 							<b>false</b> otherwise.
	 */
	private boolean perBitTranslationCheck(final PerBitTranslation perBitTranslation) {

		if (perBitTranslation == null) {
			log.warn("Hostname {} - The Source (Per Bit Translation) to visit is null, the Per Bit Translation computation cannot be performed.",
					hostname);
			return false;
		}

		TranslationTable bitTranslationTable = perBitTranslation.getBitTranslationTable();
		if (bitTranslationTable == null) {
			log.warn("Hostname {} - Translation Table is null, the Per Bit Translation computation cannot be performed.", hostname);
			return false;
		}

		Map<String, String> translations = bitTranslationTable.getTranslations();
		if (translations == null) {
			log.warn("Hostname {} - The Translation Map {} is null, the Per Bit Translation computation cannot be performed.",
					hostname, bitTranslationTable.getName());
			return false;
		}

		int columnIndex = perBitTranslation.getColumn() - 1;
		if (columnIndex < 0) {
			log.warn("Hostname {} - The index of the column to translate cannot be < 1, the Per Bit Translation computation cannot be performed.",
					hostname);
			return false;
		}

		List<Integer> bitList = perBitTranslation.getBitList();
		if (bitList == null) {
			log.warn("Hostname {} - Bit List is null, the Per Bit Translation computation cannot be performed.", hostname);
			return false;
		}

		return true;
	}

	@Override
	@WithSpan("Compute Replace Exec")
	public void visit(@SpanAttribute("compute.definition") final Replace replace) {

		if (replace == null) {
			log.warn("Hostname {} - Compute Operation (Replace) is null, the table remains unchanged.", hostname);
			return;
		}

		Integer columnToReplace = replace.getColumn();
		String strToReplace = replace.getReplace();
		String replacement = replace.getReplaceBy();

		if (columnToReplace == null || strToReplace == null || replacement == null) {
			log.warn("Hostname {} - Arguments in Compute Operation (Replace): {} are wrong, the table remains unchanged.", 
					hostname, replace);
			return;
		}

		if (columnToReplace < 1) {
			log.warn("Hostname {} - The index of the column to replace cannot be < 1, the replacement computation cannot be performed.",
					hostname);
			return;
		}

		int columnIndex = columnToReplace - 1;

		// If replacement is like "Column(n)", we replace the strToReplace by the content of the column n.
		if (COLUMN_PATTERN.matcher(replacement).matches()) {
			int replacementColumnIndex = getColumnIndex(replacement);

			if (!sourceTable.getTable().isEmpty() && replacementColumnIndex < sourceTable.getTable().get(0).size()) {

				// If strToReplace is like "Column(n)", the strToReplace is actually the content of the column n.
				if (COLUMN_PATTERN.matcher(strToReplace).matches()) {
					int strToReplaceColumnIndex = getColumnIndex(strToReplace);
					if (strToReplaceColumnIndex < sourceTable.getTable().get(0).size()) {
						sourceTable.getTable()
						.forEach(column -> column.set(
								columnIndex,
								column.get(columnIndex).replace(column.get(strToReplaceColumnIndex), column.get(replacementColumnIndex)))
								);
					}
				} else {
					sourceTable.getTable()
					.forEach(column -> column.set(
							columnIndex,
							column.get(columnIndex).replace(strToReplace, column.get(replacementColumnIndex)))
							);
				}
			}
		} else {
			// If strToReplace is like "Column(n)", the strToReplace is actually the content of the column n.
			if (COLUMN_PATTERN.matcher(strToReplace).matches()) {
				int strToReplaceColumnIndex = getColumnIndex(strToReplace);
				if (!sourceTable.getTable().isEmpty() && strToReplaceColumnIndex < sourceTable.getTable().get(0).size()) {
					sourceTable.getTable()
					.forEach(column -> column.set(
							columnIndex,
							column.get(columnIndex).replace(column.get(strToReplaceColumnIndex), replacement))
							);
				}
			} else {
				sourceTable.getTable()
				.forEach(column -> column.set(columnIndex, column.get(columnIndex).replace(strToReplace, replacement)));
			}
		}

		sourceTable.setTable(SourceTable.csvToTable(
				SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false),
				TABLE_SEP));
		sourceTable.setRawData(SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false));
	}

	@Override
	@WithSpan("Compute RightConcat Exec")
	public void visit(@SpanAttribute("compute.definition") final RightConcat rightConcat) {

		processAbstractConcat(rightConcat);
	}

	@Override
	@WithSpan("Compute Subtract Exec")
	public void visit(@SpanAttribute("compute.definition") final Substract substract) {

		if (substract == null) {
			log.warn("Hostname {} - Compute Operation (Substract) is null, the table remains unchanged.", hostname);
			return;
		}

		Integer columnIndex = substract.getColumn();
		String operand2 = substract.getSubstract();

		if (columnIndex == null || operand2 == null ) {

			log.warn("Hostname {} - Arguments in Compute Operation (Substract) : {} are wrong, the table remains unchanged.",
				hostname, substract);

			return;
		}

		if (columnIndex < 1 ) {
			log.warn("Hostname {} - The index of the column to add cannot be < 1, the addition computation cannot be performed.",
					hostname);
			return;
		}

		performMathematicalOperation(substract, columnIndex, operand2);
	}

	@Override
	@WithSpan("Compute Substring Exec")
	public void visit(@SpanAttribute("compute.definition") final Substring substring) {

		if (!checkSubstring(substring)) {
			log.warn("Hostname {} - The substring {} is not valid, the table remains unchanged.", hostname, substring);
			return;
		}

		final String start = substring.getStart();
		final String length = substring.getLength();

		final Integer startColumnIndex = getColumnIndex(start);
		if (!checkValueAndColumnIndexConsistency(start, startColumnIndex)) {
			log.warn("Hostname {} - Inconsistent substring start value {}, the table remains unchanged.", hostname, start);
			return;
		}

		final Integer lengthColumnIndex = getColumnIndex(length);
		if (!checkValueAndColumnIndexConsistency(length, lengthColumnIndex)) {
			log.warn("Hostname {} - Inconsistent substring length value {}, the table remains unchanged.", hostname, length);
			return;
		}

		performSubstring(substring.getColumn() - 1, start, startColumnIndex, length, lengthColumnIndex);

	}

	/**
	 * Check the given {@link Substring} instance
	 *
	 * @param substring The substring instance we wish to check
	 * @return true if the substring is valid
	 */
	static boolean checkSubstring(final Substring substring) {
		return substring != null
				&& substring.getColumn() != null
				&& substring.getColumn() >= 1
				&& substring.getStart() != null
				&& substring.getLength() != null;
	}

	/**
	 * Perform a substring operation on the column identified by the given <code>columnIndex</code>
	 *
	 * @param columnIndex      The column number in the current {@link SourceTable}
	 * @param start            The begin index, inclusive and starts at 1
	 * @param startColumnIndex The column index, so that we extract the start index. If equals -1 then it is not used
	 * @param end              The ending index, exclusive
	 * @param endColumnIndex   The column index, so that we extract the length index. If equals -1 then it is not used
	 */
	void performSubstring(final int columnIndex, final String start, final int startColumnIndex,
			final String end, final int endColumnIndex) {

		final Function<ComputeValue, String> startFunction = getValueFunction(startColumnIndex);
		final Function<ComputeValue, String> endFunction = getValueFunction(endColumnIndex);

		sourceTable.getTable()
		.forEach(row ->  {
			if (columnIndex < row.size()) {
				final String columnValue = row.get(columnIndex);

				final Integer beginIndex = transformToIntegerValue(startFunction.apply(
						new ComputeValue(row, startColumnIndex, start)));

				final Integer endIndex = transformToIntegerValue(
						endFunction.apply(new ComputeValue(row, endColumnIndex, end)));


				if (checkSubstringArguments(beginIndex, endIndex, columnValue.length())) {
					// No need to put endIndex -1 as the String substring end index is exclusive
					// PSL substr(1,3) is equivalent to Java String substring(0, 3)
					row.set(columnIndex, columnValue.substring(beginIndex -1, endIndex));
					return;
				}
				log.warn("Hostname {} - Substring arguments are not valid: start={}, end={},"
						+ " startColumnIndex={}, endColumnIndex={},"
						+ " computed beginIndex={}, computed endIndex={},"
						+ " row={}, columnValue={}",
						hostname,
						start, end,
						startColumnIndex, endColumnIndex,
						beginIndex, endIndex,
						row, columnValue);
			}

			log.warn("Hostname {} - Cannot perform substring on row {} on column index {}", hostname, row, columnIndex);
		});

		sourceTable.setRawData(SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false));
	}

	/**
	 * Check the substring argument to avoid the {@link StringIndexOutOfBoundsException}
	 *
	 * @param begin  Starts from 1
	 * @param end    The end index of the string
	 * @param length The length of the {@link String}
	 * @return <code>true</code> if a {@link String} substring can be performed
	 */
	static boolean checkSubstringArguments(final Integer begin, final Integer end, final int length) {
		return begin != null
				&& end != null
				&& (begin - 1) >= 0
				&& (begin - 1) <= end
				&& end <= length;
	}

	/**
	 * Transform the given {@link String} value to an {@link Integer} value
	 *
	 * @param value The value we wish to parse
	 * @return {@link Integer} value
	 */
	static Integer transformToIntegerValue(final String value) {
		if (value != null && DOUBLE_PATTERN.matcher(value).matches()) {
			return (int) Double.parseDouble(value);
		}
		return null;
	}

	/**
	 * Return the right {@link Function} based on the <code>foreignColumnIndex</code>
	 *
	 * @param foreignColumnIndex The index of the column we wish to check so that we choose the right function to return
	 * @return {@link Function} used to get the value
	 */
	static Function<ComputeValue, String> getValueFunction(final int foreignColumnIndex) {
		return (foreignColumnIndex >= 0) ? GET_VALUE_FROM_ROW : GET_VALUE;
	}

	/**
	 * Check value and column index consistency. At least we need one data available
	 *
	 * @param value              The string value as a number
	 * @param foreignColumnIndex The index of the column already extracted from a value expected as <em>Column($index)</em>
	 * @return <code>true</code> if data is consistent
	 */
	static boolean checkValueAndColumnIndexConsistency(final String value, final Integer foreignColumnIndex) {
		return foreignColumnIndex >= 0 || NUMBER_PATTERN.matcher(value).matches();
	}

	/**
	 * Get the column index for the given value
	 *
	 * @param value The value we wish to parse
	 * @return {@link Integer} value or -1 if value is not in the column pattern format
	 */
	static Integer getColumnIndex(final String value) {
		final Matcher matcher = COLUMN_PATTERN.matcher(value);
		return matcher.matches() ? Integer.parseInt(matcher.group(1)) - 1 : -1;
	}

	@Override
	@WithSpan("Compute Translate Exec")
	public void visit(@SpanAttribute("compute.definition") final Translate translate) {

		if (translate == null) {
			log.warn("Hostname {} - The Source (Translate) to visit is null, the translate computation cannot be performed.", hostname);
			return;
		}

		TranslationTable translationTable = translate.getTranslationTable();
		if (translationTable == null) {
			log.warn("Hostname {} - TranslationTable is null, the translate computation cannot be performed.", hostname);
			return;
		}

		Map<String, String> translations = translationTable.getTranslations();
		if (translations == null) {
			log.warn("Hostname {} - The Translation Map {} is null, the translate computation cannot be performed.",
					hostname, translationTable.getName());
			return;
		}

		int columnIndex = translate.getColumn() - 1;
		if (columnIndex < 0) {
			log.warn("Hostname {} - The index of the column to translate cannot be < 1, the translate computation cannot be performed.",
					hostname);
			return;
		}

		boolean needSerialization = false;

		final String defaultTranslation = translations.get(DEFAULT);

		for (List<String> line : sourceTable.getTable()) {

			if (columnIndex < line.size()) {
				final String valueToBeReplaced = line.get(columnIndex).toLowerCase();
				final String newValue = translations.getOrDefault(valueToBeReplaced, defaultTranslation);

				if (newValue != null) {
					line.set(columnIndex, newValue);

					needSerialization = needSerialization || newValue.contains(TABLE_SEP);
				} else {
					log.warn("Hostname {} - The Translation Map {} does not contain the following value {} or default.",
							hostname, translationTable.getName(), valueToBeReplaced);
				}
			}
		}

		if (needSerialization) {
			sourceTable.setTable(
					SourceTable.csvToTable(
							SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false),
							TABLE_SEP));
		}

		sourceTable.setRawData(SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false));
	}

	@Override
	@WithSpan("Compute Xml2Csv Exec")
	public void visit(@SpanAttribute("compute.definition") final Xml2Csv xml2csv) {
		
		if (xml2csv == null) {
			log.warn("Hostname {} - Compute Operation (Xml2Csv) is null, the table remains unchanged.", hostname);
			return;
		}

		try {
			final List<List<String>> xmlResult = matsyaClientsExecutor.executeXmlParsing(
					sourceTable.getRawData(),
					xml2csv.getProperties(),
					xml2csv.getRecordTag());

			if (xmlResult != null && !xmlResult.isEmpty()) {
				sourceTable.setTable(xmlResult);
				sourceTable.setRawData(SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false));
			}
		} catch (Exception e) {
			logComputeError(connector.getCompiledFilename(),
					String.format(LOG_COMPUTE_KEY_SUFFIX_TEMPLATE, sourceKey, xml2csv.getIndex()),
					"Xml2Csv",
					e,
					hostname
			);
		}

	}


	/**
	 * Perform a mathematical computation (add, subtract, multiply or divide) on a given column in the sourceTable
	 * Check if the operand2 is a reference to a column or a raw value
	 * @param computeOperation The compute operation must be one of : Add, Substract, Multiply, Divide.
	 * @param column column to be changed
	 * @param operand2 can be a reference to another column or a raw value
	 */
	private void performMathematicalOperation(final Compute computeOperation, Integer column, String operand2) {

		if (!MATH_FUNCTIONS_MAP.containsKey(computeOperation.getClass())) {
			log.warn("Hostname {} - The compute operation must be one of : Add, Substract, Multiply, Divide.", hostname);
			return;
		}

		Integer columnIndex = column - 1;
		int operandByIndex = -1;

		Matcher matcher = COLUMN_PATTERN.matcher(operand2);
		if (matcher.matches()) {
			try {
				operandByIndex = Integer.parseInt(matcher.group(1)) - 1;
				if (operandByIndex < 0) {
					log.warn("Hostname {} - The operand2 column index cannot be < 1, the {} computation cannot be performed, the table remains unchanged.", 
							hostname, computeOperation.getClass());
					return;
				}
			} catch (NumberFormatException e) {
				log.warn("Hostname {} - NumberFormatException: {} is not a correct operand2 for {}, the table remains unchanged.", 
						hostname, operand2, computeOperation);
				log.debug("Hostname {} - Stack trace:", hostname, e);
				return;
			}

		} else if (!DOUBLE_PATTERN.matcher(operand2).matches()) {
			log.warn("Hostname {} - operand2 is not a number: {}, the table remains unchanged.", hostname, operand2);
			return;
		}

		performMathComputeOnTable(computeOperation, columnIndex, operand2, operandByIndex);
	}

	/**
	 * Execute the computational operation (Add, Substract, Divide or Multiply) on each row of the tableSource.
	 *
	 * @param computeOperation	The {@link Compute} operation that should be performed.
	 * @param columnIndex		The index of the column on which the operation should be performed.
	 * @param op2               The second operand of the operation.
	 * @param op2Index			The column holding the value of the second operand in the {@link SourceTable}.
	 */
	private void performMathComputeOnTable(final Compute computeOperation, Integer columnIndex, String op2, int op2Index) {

		for (List<String> line : sourceTable.getTable()) {

			performMathComputeOnLine(computeOperation, columnIndex, op2, op2Index, line);
		}

		sourceTable.setRawData(SourceTable.tableToCsv(sourceTable.getTable(), TABLE_SEP, false));
	}

	/**
	 * @param computeOperation	The {@link Compute} operation that should be performed.
	 * @param columnIndex		The index of the column on which the operation should be performed.
	 * @param op2               The second operand of the operation.
	 * @param op2Index			The column holding the value of the second operand in the {@link SourceTable}.
	 * @param line				The line that is being processed.
	 */
	private void performMathComputeOnLine(Compute computeOperation, Integer columnIndex, String op2, int op2Index,
										  List<String> line) {

		if (columnIndex < line.size()) {

			String op1 = line.get(columnIndex);
			if (!op1.isBlank()) {

				if (op2Index != -1) {

					if (op2Index < line.size()) {
						performMathComputeOnLine(computeOperation.getClass(), columnIndex, line, op1, line.get(op2Index));
					}

				} else {

					performMathComputeOnLine(computeOperation.getClass(), columnIndex, line, op1, op2);
				}
			}
		}
	}

	/**
	 * Given two operands, perform an addition, subtraction, multiplication or division
	 * and modify the given line on the given columnIndex.
	 *
	 * @param computeOperation	The {@link Compute} operation that should be performed.
	 * @param columnIndex		The index of the column on which the operation should be performed.
	 * @param line				The row of the {@link SourceTable} that is being operated on.
	 * @param op1				The first operand of the operation.
	 * @param op2				The second operand of the operation.
	 */
	private void performMathComputeOnLine(final Class<? extends Compute> computeOperation, final Integer columnIndex,
			final List<String> line, final String op1, final String op2) {

		if (op1.isBlank() || op2.isBlank()) {
			return;
		}

		try {
			if(MATH_FUNCTIONS_MAP.containsKey(computeOperation)) {
				String resultFunction = MATH_FUNCTIONS_MAP.get(computeOperation).apply(op1, op2);
				if (resultFunction != null) {
					line.set(columnIndex, resultFunction);
				}
			}
		} catch (NumberFormatException e) {
			log.warn("Hostname {} - There is a NumberFormatException on operand 1: {} or the operand 2: {}.", hostname, op1, op2);
			log.debug("Hostname {} - Stack trace:", hostname, e);
		}
	}

	/**
	 * Log the given throwable
	 * 
	 * @param connectorName The name of the connector defining the compute
	 * @param computeKey    The key of the compute
	 * @param context       Additional information about the operation
	 * @param throwable     The catched throwable to log
	 */
	private static void logComputeError(final String connectorName, final String computeKey,
			final String context, final Throwable throwable, final String hostname) {
		if (log.isErrorEnabled()) {
			log.error("Hostname {} - Compute Operation [{}] has failed. Context [{}]. Connector [{}]. Errors:\n{}\n",
					hostname, computeKey, context, connectorName, StringHelper.getStackMessages(throwable));
		}

		if (log.isDebugEnabled()) {
			log.debug(
					String.format("Hostname %s - Compute Operation [%s] has failed. Context [%s]. Connector [%s]. Stack trace:",
							hostname, computeKey, context, connectorName),
					throwable);
		}
	}

	@AllArgsConstructor
	private static class ComputeValue {
		@Getter
		private final List<String> row;
		@Getter
		private final int columnIndex;
		@Getter
		private final String value;
	}
}
