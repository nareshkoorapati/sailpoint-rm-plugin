package sailpoint.plugin.rolemanagement.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.tools.Util;

/**
 * Builds bulk-upload Excel (.xlsx) from template metadata returned by the download rule.
 */
public final class BulkUploadTemplateExcelBuilder {

	private static final int DATA_ROW_START = 2;
	private static final int DATA_ROW_END = 5000;

	private BulkUploadTemplateExcelBuilder() {
	}

	@SuppressWarnings("unchecked")
	public static byte[] build(Map<String, Object> templateData) throws IOException {
		if (templateData == null) {
			throw new IllegalArgumentException("Template data is required");
		}

		List<String> roleHeader = toStringList((List<Object>) templateData.get("roleHeader"));
		String rolesSheetName = Util.otos(templateData.get("rolesSheetName"));

		if (roleHeader == null || roleHeader.isEmpty()) {
			throw new IllegalArgumentException("roleHeader is required");
		}
		if (Util.isNullOrEmpty(rolesSheetName)) {
			rolesSheetName = "Roles";
		}

		List<List<String>> rolesRows = new ArrayList<>();
		rolesRows.add(roleHeader);

		List<SimpleXlsxWriter.ColumnValidation> columnValidations = buildColumnValidations(
				roleHeader, templateData.get("columnValidations"));

		List<SimpleXlsxWriter.SheetData> sheets = new ArrayList<>();
		sheets.add(new SimpleXlsxWriter.SheetData(rolesSheetName, rolesRows, columnValidations));

		return SimpleXlsxWriter.write(sheets);
	}

	private static List<SimpleXlsxWriter.ColumnValidation> buildColumnValidations(
			List<String> roleHeader, Object columnValidationsObj) {
		List<SimpleXlsxWriter.ColumnValidation> validations = new ArrayList<>();
		if (!(columnValidationsObj instanceof Map)) {
			return validations;
		}

		Map<?, ?> columnValidationsMap = (Map<?, ?>) columnValidationsObj;
		for (Map.Entry<?, ?> entry : columnValidationsMap.entrySet()) {
			String columnName = entry.getKey() == null ? null : String.valueOf(entry.getKey());
			if (Util.isNullOrEmpty(columnName)) {
				continue;
			}

			int columnIndex = indexOfColumn(roleHeader, columnName);
			if (columnIndex < 0) {
				continue;
			}

			List<String> options = toStringList(castToList(entry.getValue()));
			if (options == null || options.isEmpty()) {
				continue;
			}

			String columnLetter = SimpleXlsxWriter.columnName(columnIndex);
			String sqref = columnLetter + DATA_ROW_START + ":" + columnLetter + DATA_ROW_END;
			validations.add(new SimpleXlsxWriter.ColumnValidation(sqref, options));
		}

		return validations;
	}

	private static int indexOfColumn(List<String> roleHeader, String columnName) {
		int index = roleHeader.indexOf(columnName);
		if (index >= 0) {
			return index;
		}
		for (int i = 0; i < roleHeader.size(); i++) {
			if (columnName.equalsIgnoreCase(roleHeader.get(i))) {
				return i;
			}
		}
		return -1;
	}

	@SuppressWarnings("unchecked")
	private static List<Object> castToList(Object value) {
		if (value instanceof List) {
			return (List<Object>) value;
		}
		return null;
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
