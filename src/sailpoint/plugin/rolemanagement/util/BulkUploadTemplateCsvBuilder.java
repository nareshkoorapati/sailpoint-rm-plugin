package sailpoint.plugin.rolemanagement.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds downloadable CSV files for bulk-upload templates and role attribute definitions.
 */
public final class BulkUploadTemplateCsvBuilder {

	private BulkUploadTemplateCsvBuilder() {
	}

	@SuppressWarnings("unchecked")
	public static byte[] build(Map<String, Object> templateData) {
		if (templateData == null) {
			throw new IllegalArgumentException("Template data is required");
		}

		String templateType = templateData.get("templateType") == null
				? "bulk"
				: String.valueOf(templateData.get("templateType")).trim();

		if ("attributeDefinition".equalsIgnoreCase(templateType)) {
			return buildAttributeDefinition((List<Map<String, Object>>) templateData.get("attributeRows"));
		}

		return buildBulkUploadHeader(templateData);
	}

	@SuppressWarnings("unchecked")
	private static byte[] buildBulkUploadHeader(Map<String, Object> templateData) {
		List<String> roleHeader = toStringList((List<Object>) templateData.get("roleHeader"));
		if (roleHeader == null || roleHeader.isEmpty()) {
			throw new IllegalArgumentException("roleHeader is required");
		}

		StringBuilder sb = new StringBuilder();
		appendCsvRow(sb, roleHeader);
		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] buildAttributeDefinition(List<Map<String, Object>> attributeRows) {
		if (attributeRows == null || attributeRows.isEmpty()) {
			throw new IllegalArgumentException("attributeRows is required");
		}

		List<String> attributeNames = new ArrayList<>();
		List<String> attributeTypes = new ArrayList<>();
		List<List<String>> allowedValuesByAttribute = new ArrayList<>();

		for (Map<String, Object> row : attributeRows) {
			if (row == null) {
				continue;
			}
			attributeNames.add(stringValue(row.get("name")));
			attributeTypes.add(stringValue(row.get("type")));
			allowedValuesByAttribute.add(toAllowedValueList(row.get("allowedValues")));
		}

		if (attributeNames.isEmpty()) {
			throw new IllegalArgumentException("attributeRows is required");
		}

		int maxAllowedValues = 0;
		for (List<String> allowedValues : allowedValuesByAttribute) {
			maxAllowedValues = Math.max(maxAllowedValues, allowedValues.size());
		}
		if (maxAllowedValues == 0) {
			maxAllowedValues = 1;
		}

		StringBuilder sb = new StringBuilder();

		List<String> nameRow = new ArrayList<>();
		nameRow.add("");
		nameRow.addAll(attributeNames);
		appendCsvRow(sb, nameRow);

		List<String> typeRow = new ArrayList<>();
		typeRow.add("Type");
		typeRow.addAll(attributeTypes);
		appendCsvRow(sb, typeRow);

		for (int valueIndex = 0; valueIndex < maxAllowedValues; valueIndex++) {
			List<String> allowedRow = new ArrayList<>();
			allowedRow.add(valueIndex == 0 ? "Allowed Values" : "");
			for (List<String> allowedValues : allowedValuesByAttribute) {
				String value = valueIndex < allowedValues.size() ? allowedValues.get(valueIndex) : "";
				allowedRow.add(value);
			}
			appendCsvRow(sb, allowedRow);
		}

		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}

	@SuppressWarnings("unchecked")
	private static List<String> toAllowedValueList(Object allowedValuesObj) {
		List<String> values = new ArrayList<>();
		if (!(allowedValuesObj instanceof List)) {
			return values;
		}
		for (Object value : (List<Object>) allowedValuesObj) {
			if (value == null) {
				continue;
			}
			String text = String.valueOf(value).trim();
			if (!text.isEmpty()) {
				values.add(text);
			}
		}
		return values;
	}

	private static void appendCsvRow(StringBuilder sb, List<String> fields) {
		for (int i = 0; i < fields.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(escapeCsvField(fields.get(i)));
		}
		sb.append('\n');
	}

	private static String stringValue(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	static String escapeCsvField(String value) {
		if (value == null) {
			return "";
		}
		if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
				|| value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	private static List<String> toStringList(List<Object> values) {
		if (values == null) {
			return null;
		}
		List<String> result = new ArrayList<>(values.size());
		for (Object value : values) {
			result.add(value == null ? "" : String.valueOf(value));
		}
		return result;
	}
}
