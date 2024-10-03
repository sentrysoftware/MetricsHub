package org.sentrysoftware.metricshub.engine.common.helpers;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import static org.sentrysoftware.metricshub.engine.common.helpers.JUtils.encodeSha256;
import static org.sentrysoftware.metricshub.engine.common.helpers.MacroType.AUTHENTICATIONTOKEN;
import static org.sentrysoftware.metricshub.engine.common.helpers.MacroType.BASIC_AUTH_BASE64;
import static org.sentrysoftware.metricshub.engine.common.helpers.MacroType.HOSTNAME;
import static org.sentrysoftware.metricshub.engine.common.helpers.MacroType.PASSWORD;
import static org.sentrysoftware.metricshub.engine.common.helpers.MacroType.PASSWORD_BASE64;
import static org.sentrysoftware.metricshub.engine.common.helpers.MacroType.USERNAME;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Utility class for updating HTTP macros in a text string.
 * Replaces known HTTP macros with literal target sequences such as username,
 * password, authentication-token, base64-password, base64-auth, and sha256-auth.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MacrosUpdater {

	// Set the macros regex
	private static final Pattern MACRO_PATTERN = Pattern.compile(
		"[$%]\\{(esc\\(([a-zA-Z0-9]+)\\))?(::)?([A-Z0-9_-]+)\\}"
	);

	/**
	 * Replaces each known HTTP macro in the given text with the corresponding values.
	 * Supported macros: %{USERNAME}, %{PASSWORD}, %{HOSTNAME}, %{AUTHENTICATIONTOKEN},
	 * %{PASSWORD_BASE64}, %{BASIC_AUTH}, %{SHA256}, along with various escape formats like JSON, XML, URL, etc.
	 *
	 * @param text                The text with macros to update.
	 * @param username            The HTTP username.
	 * @param password            The HTTP password.
	 * @param authenticationToken The HTTP authentication token.
	 * @param hostname            The remote hostname.
	 * @return The updated string with replaced macros.
	 */
	public static String update(
		String text,
		String username,
		char[] password,
		String authenticationToken,
		@NonNull final String hostname
	) {
		if (text == null || text.isEmpty()) {
			return EMPTY;
		}

		// Null values control
		final String passwordAsString = password != null ? String.valueOf(password) : EMPTY;
		username = username != null ? username : EMPTY;
		authenticationToken = authenticationToken != null ? authenticationToken : EMPTY;

		String updatedContent = text;
		if (updatedContent.contains("%{") || updatedContent.contains("${")) {
			final Map<String, String> simpleMacroNameToField = Map.of(
				USERNAME.name(),
				username,
				PASSWORD.name(),
				passwordAsString,
				HOSTNAME.name(),
				hostname,
				AUTHENTICATIONTOKEN.name(),
				authenticationToken
			);

			final Matcher matcher = MACRO_PATTERN.matcher(text);

			while (matcher.find()) {
				final String escapeType = matcher.group(2);
				final String macroName = matcher.group(4);
				updatedContent =
					processMacro(
						updatedContent,
						matcher.group(0),
						macroName,
						escapeType,
						passwordAsString,
						username,
						authenticationToken,
						simpleMacroNameToField
					);
			}
		}

		return updatedContent;
	}

	/**
	 * Processes the macro found in the text, replacing it with the corresponding value based on the macro name.
	 *
	 * @param content             The content string with macros.
	 * @param matchedString       The matched macro string.
	 * @param macroName           The name of the macro to replace.
	 * @param escapeType          The escape type for the macro value (e.g., JSON, XML).
	 * @param passwordAsString    The HTTP password as a string.
	 * @param username            The HTTP username.
	 * @param authenticationToken  The HTTP authentication token.
	 * @param macroNameField      A map of macro names and their corresponding values.
	 * @return The content with the macro replaced by the corresponding value.
	 */
	private static String processMacro(
		final String content,
		final String matchedString,
		final String macroName,
		final String escapeType,
		final String passwordAsString,
		final String username,
		final String authenticationToken,
		final Map<String, String> macroNameField
	) {
		String updatedContent = content;
		if (macroName.startsWith(PASSWORD_BASE64.name())) {
			// PasswordBase64 macros replacement
			updatedContent = replacePasswordBase64(updatedContent, escapeType, matchedString, passwordAsString);
		} else if (macroName.startsWith(BASIC_AUTH_BASE64.name())) {
			// BasicAuthBase64 macros replacement
			updatedContent =
				replaceBasicAuthBase64MacroValue(updatedContent, escapeType, matchedString, username, passwordAsString);
		} else if (macroName.startsWith(MacroType.SHA256_AUTH.name())) {
			// Sha256 macros replacement
			updatedContent = replaceSha256MacroValue(updatedContent, escapeType, matchedString, authenticationToken);
		} else {
			// Simple macro replacement: username, password, hostname and authenticationToken macros
			updatedContent = updateSimpleMacro(updatedContent, matchedString, macroName, escapeType, macroNameField);
		}
		return updatedContent;
	}

	/**
	 * Updates simple macros (e.g., USERNAME, PASSWORD) in the content based on the provided escape type.
	 * Replaces the macro with the corresponding value from the macroNameField map.
	 *
	 * @param content        The content string with macros.
	 * @param matchedString  The matched macro string.
	 * @param macroName      The name of the macro to be replaced.
	 * @param escapeType     The escape type (e.g., JSON, XML) for the macro value.
	 * @param macroNameField A map of macro names and their corresponding values.
	 * @return The content with the macro replaced by the corresponding value.
	 */
	private static String updateSimpleMacro(
		final String content,
		final String matchedString,
		final String macroName,
		final String escapeType,
		final Map<String, String> macroNameField
	) {
		final String replacement = macroNameField.getOrDefault(macroName, EMPTY);
		final String escapedReplacement = escapeType != null ? escapeReplacement(replacement, escapeType) : replacement;
		return content.replace(matchedString, escapedReplacement);
	}

	/**
	 * Escapes special characters in a string based on the provided escape type.
	 *
	 * @param replacement The string to escape (e.g. username, password)
	 * @param escapeType The type of escape to apply (e.g. JSON, XML, URL).
	 * @return The escaped string.
	 */
	private static String escapeReplacement(final String replacement, final String escapeType) {
		// If no escape type is provided, return the original replacement
		if (escapeType == null) {
			return replacement;
		}

		// Attempt to retrieve the EscapeType from the provided escapeType string
		final EscapeType type = EscapeType.fromString(escapeType);

		// If a valid EscapeType is found, use it to escape the replacement
		if (type != null) {
			return type.escape(replacement);
		}

		return replacement;
	}

	/**
	 * Replaces the %{BASIC_AUTH} macro with the corresponding base64-encoded username and password.
	 *
	 * @param valueToUpdate    The string to update.
	 * @param escapeType       The escape type to apply (e.g., JSON, XML).
	 * @param matchedString     The matched macro string.
	 * @param username         The username for basic authentication.
	 * @param passwordAsString The password for basic authentication.
	 * @return The updated string with the %{BASIC_AUTH} macro replaced.
	 */
	private static String replaceBasicAuthBase64MacroValue(
		final String valueToUpdate,
		final String escapeType,
		final String matchedString,
		final String username,
		final String passwordAsString
	) {
		// Join the username and password with a colon `username:password`
		// and encode the resulting string in `base64`
		// then replace the macro with the resulting value
		final String formattedBasicAuthString = String.format(
			"%s:%s",
			escapeReplacement(username, escapeType),
			escapeReplacement(passwordAsString, escapeType)
		);
		final String escapedValue = Base64
			.getEncoder()
			.encodeToString((formattedBasicAuthString).getBytes(StandardCharsets.UTF_8));
		return valueToUpdate.replace(matchedString, escapedValue);
	}

	/**
	 * Replaces the %{SHA256} macro with the corresponding SHA-256 encoded authentication token.
	 *
	 * @param valueToUpdate          The string to update.
	 * @param escapeType             The escape type to apply (e.g., JSON, XML).
	 * @param matchedString           The matched macro string.
	 * @param authenticationToken     The authentication token to encode.
	 * @return The updated string with the %{SHA256} macro replaced.
	 */
	private static String replaceSha256MacroValue(
		final String valueToUpdate,
		final String escapeType,
		final String matchedString,
		final String authenticationToken
	) {
		// Encode the authentication token into SHA256 string
		// then replace the macro with the resulting value
		if (authenticationToken == null || authenticationToken.isEmpty()) {
			return valueToUpdate.replace(matchedString, EMPTY);
		}
		final String hashedToken = encodeSha256(authenticationToken);
		return valueToUpdate.replace(matchedString, escapeReplacement(hashedToken, escapeType));
	}

	/**
	 * Replaces the %{PASSWORD_BASE64} macro with the base64-encoded password.
	 *
	 * @param valueToUpdate    The string to update.
	 * @param escapeType       The escape type to apply (e.g., JSON, XML).
	 * @param matchedString     The matched macro string.
	 * @param passwordAsString The password to encode.
	 * @return The updated string with the %{PASSWORD_BASE64} macro replaced.
	 */
	private static String replacePasswordBase64(
		final String valueToUpdate,
		final String escapeType,
		final String matchedString,
		final String passwordAsString
	) {
		// Encode the password into a base64 string
		// then replace the macro with the resulting value
		final String escapedValue = Base64
			.getEncoder()
			.encodeToString(escapeReplacement(passwordAsString, escapeType).getBytes(StandardCharsets.UTF_8));
		return valueToUpdate.replace(matchedString, escapedValue);
	}

	/**
	 * Escape special characters in a JSON string value (\ " \n \r \t).
	 *
	 * @param value The value to escape.
	 * @return The escaped value
	 */
	static String escapeJsonSpecialCharacters(final String value) {
		// Escape common characters
		return value
			// Escape characters (\ " \n \r \t)
			.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}

	/**
	 * Escapes special URL characters by replacing them with their percent-encoded equivalents.
	 *
	 * @param value the input string that may contain special URL characters
	 * @return a string where special URL characters have been percent-encoded
	 */
	static String escapeUrlSpecialCharacters(final String value) {
		// Escape common URL characters
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
	}

	/**
	 * Escapes special characters used in regular expressions by prepending a backslash.
	 *
	 * @param value the input string that may contain special regex characters
	 * @return a string where special regex characters have been escaped
	 */
	static String escapeRegexSpecialCharacters(final String value) {
		// Escape special regex characters
		return Pattern.quote(value);
	}

	/**
	 * Escapes special XML characters by replacing them with their corresponding XML entities.
	 *
	 * @param value the input string that may contain special XML characters
	 * @return a string where special XML characters have been replaced with entities
	 */
	static String escapeXmlSpecialCharacters(final String value) {
		// Escape special XML characters
		return value
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&apos;");
	}

	/**
	 * Escapes special Windows CMD characters by prepending a caret symbol.
	 *
	 * @param value the input string that may contain special CMD characters
	 * @return a string where special CMD characters have been escaped
	 */
	static String escapeWindowsCmdSpecialCharacters(final String value) {
		// Escape special CMD characters
		return value
			.replace("^", "^^")
			.replace("&", "^&")
			.replace("|", "^|")
			.replace("<", "^<")
			.replace(">", "^>")
			.replace("%", "^%")
			.replace("(", "^(")
			.replace(")", "^)")
			.replace("\"", "^\"");
	}

	/**
	 * Escape special characters in a PowerShell string value (", $, {, }, (, ), [, ], #, \n, \t, \r, \0).
	 *
	 * @param value The value to escape.
	 * @return The escaped value
	 */
	static String escapePowershellSpecialCharacters(final String value) {
		// Escape special characters for Windows PowerShell
		return value
			.replace("0", "`0") // Escape null character first
			.replace("`n", "``n") // Escape new line
			.replace("`t", "``t") // Escape tab
			.replace("`r", "``r") // Escape carriage return
			.replace("#", "`#") // Escape comment indicator
			.replace("[", "`[") // Escape opening bracket
			.replace("]", "`]") // Escape closing bracket
			.replace("(", "`(") // Escape opening parenthesis
			.replace(")", "`)") // Escape closing parenthesis
			.replace("{", "`{") // Escape opening curly brace
			.replace("}", "`}") // Escape closing curly brace
			.replace("$", "`$") // Escape variable indicator
			.replace("'", "''") // Escape single quote
			.replace("\"", "`\"") // Escape double quote
			.replace(".", "`."); // Escape dot at the end
	}

	/**
	 * Escapes special Bash characters by prepending a backslash.
	 *
	 * @param value the input string that may contain special Bash characters
	 * @return a string where special Bash characters have been escaped
	 */
	static String escapeBashSpecialCharacters(final String value) {
		// Escape special Bash characters
		return value
			.replace("'", "\\'") // Escape single quote
			.replace("\"", "\\\"") // Escape double quote
			.replace("\\", "\\\\") // Escape backslash
			.replace("$", "\\$") // Escape variable indicator
			.replace("!", "\\!") // Escape history expansion
			.replace("*", "\\*") // Escape wildcard
			.replace("?", "\\?") // Escape wildcard
			.replace("[", "\\[") // Escape opening bracket
			.replace("]", "\\]") // Escape closing bracket
			.replace("(", "\\(") // Escape opening parenthesis
			.replace(")", "\\)") // Escape closing parenthesis
			.replace("{", "\\{") // Escape opening brace
			.replace("}", "\\}") // Escape closing brace
			.replace("|", "\\|") // Escape pipe
			.replace("&", "\\&") // Escape background process indicator
			.replace("<", "\\<") // Escape input redirection
			.replace(">", "\\>") // Escape output redirection
			.replace("~", "\\~") // Escape tilde
			.replace(";", "\\;") // Escape semicolon
			.replace("\n", "\\n"); // Escape newline
	}

	/**
	 * Escapes special SQL characters such as single quotes and optionally backslashes or double quotes, depending on the SQL dialect.
	 *
	 * @param value the input string that may contain special SQL characters
	 * @return a string where special SQL characters have been escaped
	 */
	static String escapeSqlSpecialCharacters(final String value) {
		// Escape special SQL characters
		return value
			.replace("'", "''")
			.replace("\"", "\\\"") // Only if applicable for the specific SQL dialect
			.replace("\\", "\\\\") // Only if applicable for the specific SQL dialect
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}
}
