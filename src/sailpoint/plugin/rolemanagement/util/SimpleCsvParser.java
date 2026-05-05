package sailpoint.plugin.rolemanagement.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleCsvParser {

    /**
     * Low-level parser: returns list of rows, each row = list of fields.
     * Handles quotes and commas inside quotes.
     */
    public static List<List<String>> parseToRows(String csv) {
        List<List<String>> rows = new ArrayList<>();

        List<String> currentRow = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;

        int len = csv.length();
        for (int i = 0; i < len; i++) {
            char c = csv.charAt(i);

            if (c == '"') {
                // Handle escaped double quote ("")
                if (inQuotes && i + 1 < len && csv.charAt(i + 1) == '"') {
                    currentField.append('"');
                    i++; // skip second quote
                } else {
                    inQuotes = !inQuotes; // toggle
                }
            } else if (c == ',' && !inQuotes) {
                // end of field
                currentRow.add(currentField.toString());
                currentField.setLength(0);
            } else if ((c == '\n' || c == '\r') && !inQuotes) {
                // end of line
                if (currentField.length() > 0 || !currentRow.isEmpty()) {
                    currentRow.add(currentField.toString());
                    currentField.setLength(0);
                }
                if (!currentRow.isEmpty()) {
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                }

                // handle \r\n
                if (c == '\r' && i + 1 < len && csv.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                currentField.append(c);
            }
        }

        // last line (no newline at end)
        if (currentField.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(currentField.toString());
            rows.add(currentRow);
        }

        return rows;
    }

    /**
     * High-level helper:
     * - First row is header
     * - Remaining rows are data
     * - Returns List<Map<headerName, value>>
     */
    public static List<Map<String, String>> parseToMaps(String csv) {
        List<List<String>> rows = parseToRows(csv);
        List<Map<String, String>> result = new ArrayList<>();

        if (rows.isEmpty()) {
            return result;
        }

        // first row = header
        List<String> headerRow = rows.get(0);
        int headerSize = headerRow.size();

        // normalize header names (trim)
        List<String> headers = new ArrayList<>(headerSize);
        for (String h : headerRow) {
            headers.add(h != null ? h.trim() : "");
        }

        // remaining rows = data
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            Map<String, String> map = new HashMap<>();

            for (int c = 0; c < headerSize; c++) {
                String key = headers.get(c);
                // If row is shorter, missing values become null/empty
                String value = (c < row.size()) ? row.get(c) : null;
                map.put(key, value);
            }

            result.add(map);
        }

        return result;
    }
}

