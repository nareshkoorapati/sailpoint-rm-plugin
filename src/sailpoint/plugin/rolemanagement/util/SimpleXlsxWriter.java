package sailpoint.plugin.rolemanagement.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Minimal .xlsx writer (Office Open XML) without third-party dependencies.
 */
public final class SimpleXlsxWriter {

	private static final String MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
	private static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
	private static final String PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships";
	private static final String VALIDATION_LISTS_SHEET = "ValidationLists";

	private SimpleXlsxWriter() {
	}

	public static byte[] write(List<SheetData> sheets) throws IOException {
		if (sheets == null || sheets.isEmpty()) {
			throw new IllegalArgumentException("At least one sheet is required");
		}

		List<SheetData> allSheets = new ArrayList<>(sheets);
		List<ColumnValidation> hiddenListValidations = new ArrayList<>();
		for (SheetData sheet : sheets) {
			if (sheet.getColumnValidations() != null) {
				for (ColumnValidation validation : sheet.getColumnValidations()) {
					if (requiresHiddenListSheet(validation.getOptions())) {
						hiddenListValidations.add(validation);
					}
				}
			}
		}
		if (!hiddenListValidations.isEmpty()) {
			allSheets.add(new SheetData(VALIDATION_LISTS_SHEET, buildHiddenListRows(hiddenListValidations), null, true));
		}

		List<String> sharedStrings = new ArrayList<>();
		List<String> sheetXmlList = new ArrayList<>();
		int hiddenListCol = 0;

		for (SheetData sheet : allSheets) {
			List<String> validationFormulas = null;
			if (!sheet.isHidden() && sheet.getColumnValidations() != null
					&& !sheet.getColumnValidations().isEmpty()) {
				validationFormulas = new ArrayList<>();
				for (ColumnValidation validation : sheet.getColumnValidations()) {
					List<String> options = validation.getOptions();
					if (requiresHiddenListSheet(options)) {
						String listCol = columnName(hiddenListCol);
						validationFormulas.add(buildRangeFormula(listCol, options.size()));
						hiddenListCol++;
					} else {
						validationFormulas.add(buildInlineListFormula(options));
					}
				}
			}
			sheetXmlList.add(buildSheetXml(sheet.getRows(), sharedStrings, sheet.getColumnValidations(),
					validationFormulas));
		}

		String sharedStringsXml = buildSharedStringsXml(sharedStrings);
		String workbookXml = buildWorkbookXml(allSheets);
		String workbookRelsXml = buildWorkbookRelsXml(allSheets.size());
		String contentTypesXml = buildContentTypesXml(allSheets.size());
		String rootRelsXml = buildRootRelsXml();
		String stylesXml = buildStylesXml();

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ZipOutputStream zip = new ZipOutputStream(baos)) {
			writeEntry(zip, "[Content_Types].xml", contentTypesXml);
			writeEntry(zip, "_rels/.rels", rootRelsXml);
			writeEntry(zip, "xl/workbook.xml", workbookXml);
			writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml);
			writeEntry(zip, "xl/styles.xml", stylesXml);
			writeEntry(zip, "xl/sharedStrings.xml", sharedStringsXml);
			for (int i = 0; i < sheetXmlList.size(); i++) {
				writeEntry(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheetXmlList.get(i));
			}
			writeEntry(zip, "docProps/core.xml", buildCoreXml());
			writeEntry(zip, "docProps/app.xml", buildAppXml(allSheets));
			zip.finish();
			return baos.toByteArray();
		}
	}

	private static boolean requiresHiddenListSheet(List<String> options) {
		int totalLength = 2;
		for (String option : options) {
			if (option == null) {
				continue;
			}
			if (option.contains(",") || option.contains("\"")) {
				return true;
			}
			totalLength += option.length() + 1;
		}
		return totalLength > 255;
	}

	private static String buildInlineListFormula(List<String> options) {
		StringBuilder sb = new StringBuilder("\"");
		for (int i = 0; i < options.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			String option = options.get(i) == null ? "" : options.get(i);
			sb.append(option.replace("\"", "\"\""));
		}
		sb.append('"');
		return sb.toString();
	}

	private static List<List<String>> buildHiddenListRows(List<ColumnValidation> validations) {
		int maxRows = 0;
		for (ColumnValidation validation : validations) {
			maxRows = Math.max(maxRows, validation.getOptions().size());
		}
		if (maxRows < 1) {
			maxRows = 1;
		}

		List<List<String>> rows = new ArrayList<>();
		for (int r = 0; r < maxRows; r++) {
			List<String> row = new ArrayList<>();
			for (ColumnValidation validation : validations) {
				List<String> options = validation.getOptions();
				row.add(r < options.size() ? options.get(r) : "");
			}
			rows.add(row);
		}
		return rows;
	}

	private static String buildRangeFormula(String listColumn, int optionCount) {
		return VALIDATION_LISTS_SHEET + "!$" + listColumn + "$1:$" + listColumn + "$" + optionCount;
	}

	private static void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(content.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	private static String buildSheetXml(List<List<String>> rows, List<String> sharedStrings,
			List<ColumnValidation> validations, List<String> validationFormulas) {
		String dimension = buildDimensionRef(rows);
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
		sb.append("<worksheet xmlns=\"").append(MAIN_NS).append("\" xmlns:r=\"").append(REL_NS).append("\">");
		sb.append("<dimension ref=\"").append(dimension).append("\"/>");
		sb.append("<sheetViews><sheetView workbookViewId=\"0\"/></sheetViews>");
		sb.append("<sheetFormatPr defaultRowHeight=\"15\"/>");
		sb.append("<sheetData>");
		for (int r = 0; r < rows.size(); r++) {
			List<String> row = rows.get(r);
			if (row == null) {
				continue;
			}
			sb.append("<row r=\"").append(r + 1).append("\">");
			for (int c = 0; c < row.size(); c++) {
				int sharedIndex = indexOfSharedString(sharedStrings, nullSafe(row.get(c)));
				String cellRef = columnName(c) + (r + 1);
				sb.append("<c r=\"").append(cellRef).append("\" t=\"s\"><v>").append(sharedIndex).append("</v></c>");
			}
			sb.append("</row>");
		}
		sb.append("</sheetData>");

		if (validations != null && validationFormulas != null && !validations.isEmpty()) {
			sb.append("<dataValidations count=\"").append(validations.size()).append("\">");
			for (int i = 0; i < validations.size(); i++) {
				ColumnValidation validation = validations.get(i);
				// showDropDown omitted: in OOXML, showDropDown="1" suppresses the combo box.
				sb.append("<dataValidation type=\"list\" allowBlank=\"1\" showInputMessage=\"0\" ")
						.append("showErrorMessage=\"1\" sqref=\"")
						.append(escapeXml(validation.getSqref())).append("\">");
				sb.append("<formula1>").append(validationFormulas.get(i)).append("</formula1>");
				sb.append("</dataValidation>");
			}
			sb.append("</dataValidations>");
		}

		sb.append("</worksheet>");
		return sb.toString();
	}

	private static String buildDimensionRef(List<List<String>> rows) {
		int maxRow = 0;
		int maxCol = 0;
		for (int r = 0; r < rows.size(); r++) {
			List<String> row = rows.get(r);
			if (row != null && !row.isEmpty()) {
				maxRow = Math.max(maxRow, r + 1);
				maxCol = Math.max(maxCol, row.size());
			}
		}
		if (maxRow < 1) {
			maxRow = 1;
		}
		if (maxCol < 1) {
			maxCol = 1;
		}
		return "A1:" + columnName(maxCol - 1) + maxRow;
	}

	private static int indexOfSharedString(List<String> sharedStrings, String value) {
		int index = sharedStrings.indexOf(value);
		if (index >= 0) {
			return index;
		}
		sharedStrings.add(value);
		return sharedStrings.size() - 1;
	}

	private static String buildSharedStringsXml(List<String> sharedStrings) {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
		sb.append("<sst xmlns=\"").append(MAIN_NS)
				.append("\" xmlns:xml=\"http://www.w3.org/XML/1998/namespace\" count=\"")
				.append(sharedStrings.size()).append("\" uniqueCount=\"").append(sharedStrings.size()).append("\">");
		for (String value : sharedStrings) {
			sb.append("<si>");
			if (needsPreserveSpace(value)) {
				sb.append("<t xml:space=\"preserve\">").append(escapeXml(value)).append("</t>");
			} else {
				sb.append("<t>").append(escapeXml(value)).append("</t>");
			}
			sb.append("</si>");
		}
		sb.append("</sst>");
		return sb.toString();
	}

	private static boolean needsPreserveSpace(String value) {
		return value != null && (value.startsWith(" ") || value.endsWith(" "));
	}

	private static String buildWorkbookXml(List<SheetData> sheets) {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
		sb.append("<workbook xmlns=\"").append(MAIN_NS).append("\" xmlns:r=\"").append(REL_NS).append("\">");
		sb.append("<workbookPr date1904=\"false\"/>");
		sb.append("<sheets>");
		for (int i = 0; i < sheets.size(); i++) {
			SheetData sheet = sheets.get(i);
			sb.append("<sheet name=\"").append(escapeXml(sanitizeSheetName(sheet.getName())))
					.append("\" sheetId=\"").append(i + 1).append("\"");
			if (sheet.isHidden()) {
				sb.append(" state=\"hidden\"");
			}
			sb.append(" r:id=\"rId").append(i + 1).append("\"/>");
		}
		sb.append("</sheets></workbook>");
		return sb.toString();
	}

	private static String buildWorkbookRelsXml(int sheetCount) {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
		sb.append("<Relationships xmlns=\"").append(PKG_REL_NS).append("\">");
		for (int i = 0; i < sheetCount; i++) {
			sb.append("<Relationship Id=\"rId").append(i + 1)
					.append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" ")
					.append("Target=\"worksheets/sheet").append(i + 1).append(".xml\"/>");
		}
		int stylesId = sheetCount + 1;
		int sharedStringsId = sheetCount + 2;
		sb.append("<Relationship Id=\"rId").append(stylesId)
				.append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" ")
				.append("Target=\"styles.xml\"/>");
		sb.append("<Relationship Id=\"rId").append(sharedStringsId)
				.append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" ")
				.append("Target=\"sharedStrings.xml\"/>");
		sb.append("</Relationships>");
		return sb.toString();
	}

	private static String buildStylesXml() {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
				+ "<styleSheet xmlns=\"" + MAIN_NS + "\">"
				+ "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
				+ "<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>"
				+ "<borders count=\"1\"><border/></borders>"
				+ "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
				+ "<cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>"
				+ "</styleSheet>";
	}

	private static String buildContentTypesXml(int sheetCount) {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
		sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
		sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
		sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
		sb.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
		sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
		sb.append("<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>");
		for (int i = 1; i <= sheetCount; i++) {
			sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
					.append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
		}
		sb.append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>");
		sb.append("<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>");
		sb.append("</Types>");
		return sb.toString();
	}

	private static String buildRootRelsXml() {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
				+ "<Relationships xmlns=\"" + PKG_REL_NS + "\">"
				+ "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
				+ "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
				+ "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>"
				+ "</Relationships>";
	}

	private static String buildCoreXml() {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
				+ "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" "
				+ "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" "
				+ "xmlns:dcterms=\"http://purl.org/dc/terms/\" "
				+ "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
				+ "<dc:creator>Role Management Plugin</dc:creator>"
				+ "</cp:coreProperties>";
	}

	private static String buildAppXml(List<SheetData> sheets) {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
		sb.append("<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\" ");
		sb.append("xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\">");
		sb.append("<Application>Role Management Plugin</Application>");
		int visibleCount = 0;
		for (SheetData sheet : sheets) {
			if (!sheet.isHidden()) {
				visibleCount++;
			}
		}
		sb.append("<TitlesOfParts><vt:vector size=\"").append(visibleCount).append("\" baseType=\"lpstr\">");
		for (SheetData sheet : sheets) {
			if (!sheet.isHidden()) {
				sb.append("<vt:lpstr>").append(escapeXml(sanitizeSheetName(sheet.getName()))).append("</vt:lpstr>");
			}
		}
		sb.append("</vt:vector></TitlesOfParts></Properties>");
		return sb.toString();
	}

	public static String columnName(int index) {
		int dividend = index + 1;
		StringBuilder column = new StringBuilder();
		while (dividend > 0) {
			int modulo = (dividend - 1) % 26;
			column.insert(0, (char) ('A' + modulo));
			dividend = (dividend - modulo) / 26;
		}
		return column.toString();
	}

	private static String sanitizeSheetName(String name) {
		if (name == null || name.isEmpty()) {
			return "Sheet";
		}
		String sanitized = name.replaceAll("[\\\\/*?\\[\\]:]", " ");
		return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
	}

	private static String escapeXml(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&apos;");
	}

	private static String nullSafe(String value) {
		return value != null ? value : "";
	}

	public static final class ColumnValidation {
		private final String sqref;
		private final List<String> options;

		public ColumnValidation(String sqref, List<String> options) {
			this.sqref = sqref;
			this.options = options;
		}

		public String getSqref() {
			return sqref;
		}

		public List<String> getOptions() {
			return options;
		}
	}

	public static final class SheetData {
		private final String name;
		private final List<List<String>> rows;
		private final List<ColumnValidation> columnValidations;
		private final boolean hidden;

		public SheetData(String name, List<List<String>> rows, List<ColumnValidation> columnValidations) {
			this(name, rows, columnValidations, false);
		}

		public SheetData(String name, List<List<String>> rows, List<ColumnValidation> columnValidations,
				boolean hidden) {
			this.name = name;
			this.rows = rows;
			this.columnValidations = columnValidations;
			this.hidden = hidden;
		}

		public String getName() {
			return name;
		}

		public List<List<String>> getRows() {
			return rows;
		}

		public List<ColumnValidation> getColumnValidations() {
			return columnValidations;
		}

		public boolean isHidden() {
			return hidden;
		}
	}
}
