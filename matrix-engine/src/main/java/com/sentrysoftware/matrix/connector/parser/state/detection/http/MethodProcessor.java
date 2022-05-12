package com.sentrysoftware.matrix.connector.parser.state.detection.http;

import com.sentrysoftware.matrix.connector.model.Connector;
import com.sentrysoftware.matrix.connector.model.detection.criteria.http.HTTP;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MethodProcessor extends HttpProcessor {

	private static final Pattern METHOD_KEY_PATTERN = Pattern.compile(
		"^\\s*detection\\.criteria\\(([1-9]\\d*)\\)\\.method\\s*$",
		Pattern.CASE_INSENSITIVE);

	@Override
	protected Matcher getMatcher(String key) {
		return METHOD_KEY_PATTERN.matcher(key);
	}

	@Override
	public void parse(final String key, final String value, final Connector connector) {

		super.parse(key, value, connector);

		((HTTP) getCriterion(key, connector)).setMethod(value);
	}
}
