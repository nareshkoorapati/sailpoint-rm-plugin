/**
 * 
 */
package sailpoint.plugin.rolemanagement.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.plugin.PluginContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * @author shrey
 *
 */
public class IdentityService {

	private static final Logger logger = Logger.getLogger(IdentityService.class);
	private static final Set<String> WORKGROUP_SORT_COLUMNS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"name", "displayName", "email", "description", "created", "modified")));

	private PluginContext pluginContext;
	private SailPointContext _context;
	private Identity loggedInIdentity;
	
	public IdentityService(PluginContext pluginContext, SailPointContext _context, Identity loggedInIdentity) throws GeneralException {
		// TODO Auto-generated constructor stub
		this.pluginContext = pluginContext;
		this._context=_context;
		this.loggedInIdentity = loggedInIdentity;
	}

	/**
	 * Paginated search of workgroup identities (type workgroup).
	 * When {@code roleAdmin} is false, results are limited to workgroups the logged-in user belongs to.
	 */
	public Map<String, Object> searchWorkgroups(int start, int limit, String query, String sort, String dir,
			boolean roleAdmin, List<String> memberIds, String moreFilter) throws GeneralException {
		Map<String, Object> result = new HashMap<>();
		QueryOptions ops = new QueryOptions();
		ops.addFilter(Filter.eq("workgroup", true));

		Set<String> allowedWgIds = null;
		if (!roleAdmin) {
			allowedWgIds = new LinkedHashSet<>(workgroupIdsForLoggedInUser());
		}
		if (memberIds != null && !memberIds.isEmpty()) {
			Set<String> memberScoped = workgroupIdsForMembers(memberIds);
			if (allowedWgIds == null) {
				allowedWgIds = memberScoped;
			} else {
				allowedWgIds.retainAll(memberScoped);
			}
		}
		Set<String> moreFilterIds = workgroupIdsForMoreFilter(moreFilter);
		if (moreFilterIds != null) {
			if (allowedWgIds == null) {
				allowedWgIds = moreFilterIds;
			} else {
				allowedWgIds.retainAll(moreFilterIds);
			}
		}
		if (allowedWgIds != null) {
			if (allowedWgIds.isEmpty()) {
				result.put("total", 0);
				result.put("objects", new ArrayList<Map<String, Object>>());
				result.put("start", start);
				result.put("limit", limit);
				return result;
			}
			ops.addFilter(Filter.in("id", new ArrayList<>(allowedWgIds)));
		}
		if (Util.isNotNullOrEmpty(query)) {
			String q = query.trim();
			ops.addFilter(Filter.or(Filter.like("name", q), Filter.like("displayName", q)));
		}
		String orderBy = resolveWorkgroupSortColumn(sort);
		ops.setOrderBy(orderBy);
		ops.setOrderAscending(dir != null && "ASC".equalsIgnoreCase(dir));

		result.put("total", _context.countObjects(Identity.class, ops));
		ops.setFirstRow(start);
		ops.setResultLimit(limit);

		List<Map<String, Object>> rows = new ArrayList<>();
		Iterator<Identity> itr = _context.search(Identity.class, ops);
		if (itr != null) {
			while (itr.hasNext()) {
				Identity wg = itr.next();
				Map<String, Object> row = new HashMap<>();
				row.put("id", wg.getId());
				row.put("name", wg.getName());
				row.put("displayName", wg.getDisplayableName());
				row.put("disabled", wg.isInactive());
				row.put("email", wg.getEmail());
				row.put("description", wg.getDescription());
				row.put("created", wg.getCreated());
				row.put("modified", wg.getModified());

				//Members count
				
				QueryOptions qo = new QueryOptions();
				qo.add(new Filter[] { Filter.eq("workgroups.id", wg.getId()) });
				qo.setCloneResults(true);
				row.put("membersCount", _context.countObjects(Identity.class, qo));

				//Role Objects count
				QueryOptions roleQo = new QueryOptions();
				roleQo.add(new Filter[] { Filter.eq("owner.id", wg.getId()) });
				roleQo.setCloneResults(true);
				row.put("rolesCount", _context.countObjects(Bundle.class, roleQo));
				rows.add(row);
			}
			Util.flushIterator(itr);
		}
		result.put("objects", rows);
		result.put("start", start);
		result.put("limit", limit);
		return result;
	}

	private String resolveWorkgroupSortColumn(String sort) {
		if (Util.isNotNullOrEmpty(sort) && WORKGROUP_SORT_COLUMNS.contains(sort.trim())) {
			return sort.trim();
		}
		return "name";
	}

	private Set<String> workgroupIdsForMoreFilter(String moreFilter) throws GeneralException {
		if (Util.isNullOrEmpty(moreFilter)) {
			return null;
		}
		String key = moreFilter.trim();
		if ("assignedToRoles".equalsIgnoreCase(key)) {
			return workgroupIdsAssignedToRoles();
		}
		if ("singleMember".equalsIgnoreCase(key)) {
			return workgroupIdsSingleMember();
		}
		if ("termedMember".equalsIgnoreCase(key)) {
			return workgroupIdsWithTermedMembers();
		}
		return null;
	}

	private Set<String> workgroupIdsAssignedToRoles() throws GeneralException {
		Set<String> ids = new LinkedHashSet<>();
		QueryOptions qo = new QueryOptions();
		qo.addFilter(Filter.eq("owner.workgroup", true));
		Iterator<Bundle> itr = _context.search(Bundle.class, qo);
		if (itr != null) {
			try {
				while (itr.hasNext()) {
					Bundle b = itr.next();
					Identity owner = b != null ? b.getOwner() : null;
					if (owner != null && owner.getId() != null) {
						ids.add(owner.getId());
					}
				}
			} finally {
				Util.flushIterator(itr);
			}
		}
		return ids;
	}

	private Set<String> workgroupIdsSingleMember() throws GeneralException {
		Set<String> ids = new LinkedHashSet<>();
		QueryOptions qo = new QueryOptions();
		qo.setCloneResults(true);
		String sql = "sql:SELECT workgroup FROM spt_identity_workgroups GROUP BY workgroup HAVING COUNT(DISTINCT identity_id) = 1";
		Iterator<?> itr = _context.search(sql, new HashMap<>(), qo);
		if (itr != null) {
			try {
				while (itr.hasNext()) {
					Object row = itr.next();
					String id = sqlFirstCellAsString(row);
					if (Util.isNotNullOrEmpty(id)) {
						ids.add(id);
					}
				}
			} finally {
				Util.flushIterator(itr);
			}
		}
		return ids;
	}

	private Set<String> workgroupIdsWithTermedMembers() throws GeneralException {
		Set<String> ids = new LinkedHashSet<>();
		QueryOptions qo = new QueryOptions();
		qo.addFilter(Filter.eq("inactive", true));
		Iterator<Identity> itr = _context.search(Identity.class, qo);
		if (itr != null) {
			try {
				while (itr.hasNext()) {
					Identity idn = itr.next();
					List<Identity> wgs = idn != null ? idn.getWorkgroups() : null;
					if (wgs == null) {
						continue;
					}
					for (Identity wg : wgs) {
						if (wg != null && wg.isWorkgroup() && wg.getId() != null) {
							ids.add(wg.getId());
						}
					}
				}
			} finally {
				Util.flushIterator(itr);
			}
		}
		return ids;
	}

	/**
	 * Dashboard aggregates for the Workgroup Management overview tiles.
	 * When {@code roleAdmin} is false, workgroup-scoped metrics only include the logged-in user's workgroups.
	 */
	public Map<String, Object> getWorkgroupDashboardStats(boolean roleAdmin) throws GeneralException {
		Map<String, Object> stats = new HashMap<>();

		List<String> scopedWgIds = null;
		if (!roleAdmin) {
			scopedWgIds = workgroupIdsForLoggedInUser();
			if (scopedWgIds.isEmpty()) {
				stats.put("totalWorkgroups", 0);
				stats.put("totalRoles", _context.countObjects(Bundle.class, new QueryOptions()));
				stats.put("rolesOwnedByWorkgroups", 0);
				stats.put("singleMemberWorkgroups", 0);
				stats.put("workgroupsWithTermedMembers", 0);
				return stats;
			}
		}

		QueryOptions allWg = new QueryOptions();
		allWg.addFilter(Filter.eq("workgroup", true));
		if (!roleAdmin) {
			allWg.addFilter(Filter.in("id", scopedWgIds));
		}
		int totalWorkgroups = _context.countObjects(Identity.class, allWg);
		stats.put("totalWorkgroups", totalWorkgroups);

		int totalRoles = _context.countObjects(Bundle.class, new QueryOptions());
		stats.put("totalRoles", totalRoles);

		QueryOptions bundleOps = new QueryOptions();
		bundleOps.addFilter(Filter.eq("owner.workgroup", true));
		if (!roleAdmin) {
			bundleOps.addFilter(Filter.in("owner.id", scopedWgIds));
		}
		int rolesOwnedByWorkgroups = _context.countObjects(Bundle.class, bundleOps);
		stats.put("rolesOwnedByWorkgroups", rolesOwnedByWorkgroups);

		int singleMemberWorkgroups = countWorkgroupsWithExactlyOneMemberSql(roleAdmin, scopedWgIds);
		stats.put("singleMemberWorkgroups", singleMemberWorkgroups);

		int workgroupsWithTermedMembers = 0;
		QueryOptions wgIterOps = new QueryOptions();
		wgIterOps.addFilter(Filter.eq("workgroup", true));
		if (!roleAdmin) {
			wgIterOps.addFilter(Filter.in("id", scopedWgIds));
		}
		Iterator<Identity> wgit = _context.search(Identity.class, wgIterOps);
		if (wgit != null) {
			try {
				while (wgit.hasNext()) {
					Identity wg = wgit.next();
					QueryOptions termedQ = new QueryOptions();
					termedQ.addFilter(Filter.eq("workgroups.id", wg.getId()));
					termedQ.addFilter(Filter.eq("inactive", true));
					int termedMemberCount = _context.countObjects(Identity.class, termedQ);
					if (termedMemberCount > 0) {
						workgroupsWithTermedMembers++;
					}
				}
			} finally {
				Util.flushIterator(wgit);
			}
		}
		stats.put("workgroupsWithTermedMembers", workgroupsWithTermedMembers);

		return stats;
	}


	/**
	 * Counts workgroups that have exactly one member.
	 * <ul>
	 * <li>{@code roleAdmin == true}: all workgroups in the system.</li>
	 * <li>{@code roleAdmin == false}: only among {@code scopedWorkgroupIds} (caller's workgroups); empty → 0.</li>
	 * </ul>
	 */
	private int countWorkgroupsWithExactlyOneMemberSql(boolean roleAdmin, List<String> scopedWorkgroupIds)
			throws GeneralException {
		if (!roleAdmin) {
			if (scopedWorkgroupIds == null || scopedWorkgroupIds.isEmpty()) {
				return 0;
			}
		}

		QueryOptions qo = new QueryOptions();
		qo.setCloneResults(true);

		String sql;
		if (roleAdmin) {
			sql =
				"sql:SELECT COUNT(*) FROM ( "
					+ "SELECT workgroup FROM spt_identity_workgroups "
					+ "GROUP BY workgroup "
					+ "HAVING COUNT(DISTINCT identity_id) = 1 "
					+ ") single_member_wg";
		} else {
			StringBuilder inList = new StringBuilder();
			for (int i = 0; i < scopedWorkgroupIds.size(); i++) {
				if (i > 0) {
					inList.append(',');
				}
				String id = scopedWorkgroupIds.get(i);
				inList.append('\'').append(id != null ? id.replace("'", "''") : "").append('\'');
			}
			sql =
				"sql:SELECT COUNT(*) FROM ( "
					+ "SELECT workgroup FROM spt_identity_workgroups "
					+ "WHERE workgroup IN (" + inList + ") "
					+ "GROUP BY workgroup "
					+ "HAVING COUNT(DISTINCT identity_id) = 1 "
					+ ") single_member_wg";
		}
		Iterator<?> itr = _context.search(sql, new HashMap<>(), qo);
		int count = 0;
		if (itr != null) {
			try {
				if (itr.hasNext()) {
					Object row = itr.next();
					count = sqlCountResultToInt(row);
				}
			} finally {
				Util.flushIterator(itr);
			}
		}
		return count;
	}

	/**
	 * SQL scalar COUNT may be returned as {@link java.math.BigInteger} or a single cell;
	 * multi-column rows come back as {@code Object[]}.
	 */
	private static int sqlCountResultToInt(Object row) {
		if (row == null) {
			return 0;
		}
		if (row instanceof Object[]) {
			Object[] cells = (Object[]) row;
			if (cells.length == 0 || cells[0] == null) {
				return 0;
			}
			if (cells[0] instanceof Number) {
				return numberToInt((Number) cells[0]);
			}
			return 0;
		}
		if (row instanceof Number) {
			return numberToInt((Number) row);
		}
		return 0;
	}

	private static String sqlFirstCellAsString(Object row) {
		if (row == null) {
			return null;
		}
		if (row instanceof Object[]) {
			Object[] cells = (Object[]) row;
			if (cells.length == 0 || cells[0] == null) {
				return null;
			}
			return String.valueOf(cells[0]);
		}
		return String.valueOf(row);
	}

	private List<String> workgroupIdsForLoggedInUser() throws GeneralException {
		if (loggedInIdentity == null) {
			return Collections.emptyList();
		}
		Identity fresh = _context.getObjectById(Identity.class, loggedInIdentity.getId());
		if (fresh == null) {
			return Collections.emptyList();
		}
		List<Identity> wgs = fresh.getWorkgroups();
		List<String> ids = new ArrayList<>();
		if (wgs != null) {
			for (Identity wg : wgs) {
				if (wg != null && wg.isWorkgroup() && wg.getId() != null) {
					ids.add(wg.getId());
				}
			}
		}
		return ids;
	}

	/**
	 * Returns workgroups common to all selected members.
	 */
	private Set<String> workgroupIdsForMembers(List<String> memberIds) throws GeneralException {
		Set<String> common = null;
		for (String mid : memberIds) {
			if (Util.isNullOrEmpty(mid)) {
				continue;
			}
			Identity member = _context.getObjectById(Identity.class, mid);
			if (member == null || member.isWorkgroup()) {
				return new HashSet<>();
			}
			List<Identity> memberWgs = member.getWorkgroups();
			Set<String> memberSet = new LinkedHashSet<>();
			if (memberWgs != null) {
				for (Identity wg : memberWgs) {
					if (wg != null && wg.getId() != null && wg.isWorkgroup()) {
						memberSet.add(wg.getId());
					}
				}
			}
			if (common == null) {
				common = memberSet;
			} else {
				common.retainAll(memberSet);
			}
			if (common.isEmpty()) {
				return common;
			}
		}
		return common == null ? new LinkedHashSet<>() : common;
	}

	private static int numberToInt(Number n) {
		if (n instanceof java.math.BigInteger) {
			return ((java.math.BigInteger) n).intValue();
		}
		if (n instanceof java.math.BigDecimal) {
			return ((java.math.BigDecimal) n).intValue();
		}
		return n.intValue();
	}

	/**
	 * Typeahead search for identities. When {@code includeWorkgroups} is false, workgroups are excluded
	 * (typical member pickers). When true, both person identities and workgroup identities are returned;
	 * each row includes a boolean {@code workgroup}.
	 */
	public List<Map<String, Object>> suggestIdentities(String query, int limit, boolean includeWorkgroups)
			throws GeneralException {
		int lim = limit <= 0 ? 25 : Math.min(limit, 50);
		QueryOptions ops = new QueryOptions();
		if (!includeWorkgroups) {
			ops.addFilter(Filter.eq("workgroup", false));
		}
		else {
			ops.addFilter(Filter.in("workgroup", Util.csvToList("true,false")));
		}
		//ops.addFilter(Filter.eq("inactive", false));
		if (Util.isNotNullOrEmpty(query)) {
			String q = query.trim();
			ops.addFilter(Filter.or(Filter.like("name", q), Filter.like("displayName", q)));
		}
		ops.setOrderBy("name");
		ops.setOrderAscending(true);
		ops.setResultLimit(lim);

		List<Map<String, Object>> rows = new ArrayList<>();
		Iterator<Identity> itr = _context.search(Identity.class, ops);
		if (itr != null) {
			try {
				while (itr.hasNext()) {
					Identity idn = itr.next();
					Map<String, Object> row = new HashMap<>();
					row.put("id", idn.getId());
					row.put("name", idn.getName());
					row.put("displayName", idn.getDisplayableName());
					row.put("firstname", idn.getFirstname());
					row.put("lastname", idn.getLastname());
					row.put("workgroup", idn.isWorkgroup());
					rows.add(row);
				}
			} finally {
				Util.flushIterator(itr);
			}
		}
		return rows;
	}

	/** Backward-compatible suggest (non-workgroup identities only). */
	public List<Map<String, Object>> suggestIdentities(String query, int limit) throws GeneralException {
		return suggestIdentities(query, limit, false);
	}

	/**
	 * Create a workgroup {@link Identity} and optionally attach members.
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> createWorkgroup(Map<String, Object> body) throws GeneralException {
		Map<String, Object> result = new HashMap<>();
		String name = body.get("name") != null ? body.get("name").toString().trim() : "";
		if (Util.isNullOrEmpty(name)) {
			result.put("success", false);
			result.put("message", "Name is required.");
			return result;
		}

		Identity dup = _context.getObjectByName(Identity.class, name);
		if (dup != null) {
			result.put("success", false);
			result.put("message", "An identity with this name already exists.");
			return result;
		}

		String displayName = optTrim(body, "displayName");
		if (Util.isNullOrEmpty(displayName)) {
			displayName = name;
		}
		String description = optTrim(body, "description");
		String email = optTrim(body, "email");
		String notificationSetting = optTrim(body, "notificationSetting");
		String ownerId = optTrim(body, "ownerId");

		Identity wg = new Identity();
		wg.setName(name);
		wg.setWorkgroup(true);
		wg.setDisplayName(displayName);
		if (Util.isNotNullOrEmpty(description)) {
			wg.setDescription(description);
		}
		if (Util.isNotNullOrEmpty(email)) {
			wg.setEmail(email);
		}
		wg.setInactive(false);

		if (Util.isNotNullOrEmpty(ownerId)) {
			Identity owner = _context.getObjectById(Identity.class, ownerId);
			if (owner != null && !owner.isWorkgroup()) {
				wg.setOwner(owner);
			}
		} else if (loggedInIdentity != null) {
			wg.setOwner(loggedInIdentity);
		}

		if (Util.isNotNullOrEmpty(notificationSetting)) {
			wg.setAttribute("notificationScheme", notificationSetting);
		}

		_context.saveObject(wg);
		_context.commitTransaction();
		wg = _context.getObjectByName(Identity.class, name);

		List<String> memberIds = new ArrayList<>();
		Object rawMembers = body.get("memberIds");
		if (rawMembers instanceof List) {
			for (Object o : (List<?>) rawMembers) {
				if (o != null && Util.isNotNullOrEmpty(o.toString())) {
					memberIds.add(o.toString());
				}
			}
		}

		List<String> failedMembers = new ArrayList<>();
		for (String mid : memberIds) {
			try {
				Identity member = _context.getObjectById(Identity.class, mid);
				if (member != null && !member.isWorkgroup()) {
					addIdentityToWorkgroup(member, wg);
				}
			} catch (Exception ex) {
				logger.warn("Could not add member identity " + mid + ": " + ex.getMessage(), ex);
				failedMembers.add(mid);
			}
		}

		result.put("success", true);
		result.put("id", wg.getId());
		result.put("name", wg.getName());
		if (!failedMembers.isEmpty()) {
			result.put("memberWarnings", failedMembers);
			result.put(
					"message",
					"Workgroup created, but some members could not be added. Verify identities are active users.");
		}
		return result;
	}

	private String optTrim(Map<String, Object> body, String key) {
		Object v = body.get(key);
		return v != null ? v.toString().trim() : "";
	}

	private void addIdentityToWorkgroup(Identity member, Identity workgroup) throws GeneralException {
		if (member == null || workgroup == null || member.isWorkgroup()) {
			return;
		}
		List<Identity> wgs = member.getWorkgroups();
		if (wgs == null) {
			wgs = new ArrayList<>();
		} else {
			wgs = new ArrayList<>(wgs);
		}
		String wid = workgroup.getId();
		for (Identity existing : wgs) {
			if (existing != null && wid != null && wid.equals(existing.getId())) {
				return;
			}
		}
		wgs.add(workgroup);
		member.setWorkgroups(wgs);
		_context.saveObject(member);
		_context.commitTransaction();

	}
	
	public List<Map<String, Object>> getWorkGroupMembers(String workgroupId) throws GeneralException {
		List<Map<String, Object>> members = new ArrayList<>();
		Identity workgroup = _context.getObjectById(Identity.class, workgroupId);
		if (workgroup.isWorkgroup()) {
			List<String> props = new ArrayList<>();
			props.add("name");
			props.add("displayName");
			props.add("employeeid");
			Iterator<Object[]> itr = ObjectUtil.getWorkgroupMembers(_context, workgroup, props);
			if (itr != null) {
				while(itr.hasNext()) {
					Object[] row = (Object[])itr.next();
					Map<String,Object> member = new HashMap<>();
					member.put("name",(String)row[0]);
					member.put("displayName",(String)row[1]);
					member.put("employeeid",(String)row[2]);
					members.add(member);
				}
				Util.flushIterator(itr);
			}
		}
		else {
			Map<String,Object> member = new HashMap<>();
			member.put("name",workgroup.getName());
			member.put("displayName",workgroup.getDisplayableName());
			member.put("employeeid",workgroup.getAttribute("employeeid"));
			members.add(member);
		}
		return members;
	}
}
