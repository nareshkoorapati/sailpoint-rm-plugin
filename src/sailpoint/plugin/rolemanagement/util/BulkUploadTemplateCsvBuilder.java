package sailpoint.plugin.rolemanagement.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds bulk-upload CSV template (header row only) from metadata returned by the download rule.
 */
public final class BulkUploadTemplateCsvBuilder {

	private BulkUploadTemplateCsvBuilder() {
	}

	@SuppressWarnings("unchecked")
	public static byte[] build(Map<String, Object> templateData) {
		if (templateData == null) {
			throw new IllegalArgumentException("Template data is required");
		}

		List<String> roleHeader = toStringList((List<Object>) templateData.get("roleHeader"));
		if (roleHeader == null || roleHeader.isEmpty()) {
			throw new IllegalArgumentException("roleHeader is required");
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < roleHeader.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(escapeCsvField(roleHeader.get(i)));
		}
		sb.append('\n');

		return sb.toString().getBytes(StandardCharsets.UTF_8);
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
