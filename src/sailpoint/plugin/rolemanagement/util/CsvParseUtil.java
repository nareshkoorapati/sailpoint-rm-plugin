package sailpoint.plugin.rolemanagement.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Util;

/**
 * Normalizes CSV text produced from Excel "Save As CSV" so column keys match template headers
 * (e.g. {@code Role Name}) when building maps for bulk import rules.
 */
public final class CsvParseUtil {

	private static final String UTF8_BOM = "\uFEFF";

	private CsvParseUtil() {
	}

	public static String stripUtf8Bom(String text) {
		if (text != null && text.startsWith(UTF8_BOM)) {
			return text.substring(UTF8_BOM.length());
		}
		return text;
	}

	/**
	 * Excel CSV may use comma or semicolon depending on regional settings.
	 */
	public static String detectDelimiter(String headerLine) {
		if (Util.isNullOrEmpty(headerLine)) {
			return ",";
		}
		int commas = countChar(headerLine, ',');
		int semicolons = countChar(headerLine, ';');
		return semicolons > commas ? ";" : ",";
	}

	private static int countChar(String s, char ch) {
		int count = 0;
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == ch) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Trim and remove surrounding double quotes (Excel often quotes headers/cells).
	 */
	public static String normalizeCell(String value) {
		if (value == null) {
			return "";
		}
		String v = value.trim();
		if (v.length() >= 2 && v.charAt(0) == '"' && v.charAt(v.length() - 1) == '"') {
			v = v.substring(1, v.length() - 1).trim();
		}
		return v;
	}

	public static List<String> normalizeHeaders(List<String> rawHeaders) {
		List<String> headers = new ArrayList<>();
		if (rawHeaders == null) {
			return headers;
		}
		for (String raw : rawHeaders) {
			String key = normalizeCell(raw);
			if (!key.isEmpty()) {
				headers.add(key);
			} else {
				headers.add("");
			}
		}
		return headers;
	}

	public static Map<String, String> toRowMap(List<String> headers, List<String> values) {
		Map<String, String> row = new HashMap<>();
		if (headers == null) {
			return row;
		}
		for (int i = 0; i < headers.size(); i++) {
			String key = headers.get(i);
			if (Util.isNullOrEmpty(key)) {
				continue;
			}
			String val = (values != null && i < values.size()) ? normalizeCell(values.get(i)) : "";
			row.put(key, val);
		}
		return row;
	}

	public static List<String> parseLine(String line, String delimiter) throws Exception {
		RFC4180LineParser parser = new RFC4180LineParser(delimiter);
		return parser.parseLine(line);
	}

	public static List<String> nonEmptyLines(String csvText) {
		List<String> lines = new ArrayList<>();
		if (csvText == null) {
			return lines;
		}
		for (String line : csvText.split("\\r?\\n")) {
			if (line != null && !line.trim().isEmpty()) {
				lines.add(line);
			}
		}
		return lines;
	}
}
