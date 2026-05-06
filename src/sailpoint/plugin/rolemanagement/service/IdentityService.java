/**
 * 
 */
package sailpoint.plugin.rolemanagement.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
	 */
	public Map<String, Object> searchWorkgroups(int start, int limit, String query, String sort, String dir)
			throws GeneralException {
		Map<String, Object> result = new HashMap<>();
		QueryOptions ops = new QueryOptions();
		ops.addFilter(Filter.eq("workgroup", true));
		if (Util.isNotNullOrEmpty(query)) {
			String q = query.trim();
			ops.addFilter(Filter.or(Filter.like("name", q), Filter.like("displayName", q)));
		}
		String orderBy = Util.isNotNullOrEmpty(sort) ? sort : "name";
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

	/**
	 * Dashboard aggregates aligned with the Workgroup Management UI tiles:
	 * <ul>
	 * <li>{@code totalWorkgroups} – identities flagged as workgroup</li>
	 * <li>{@code rolesOwnedByWorkgroups} – role ({@link Bundle}) count whose owner is a workgroup</li>
	 * <li>{@code workgroupsOwningRoles} – distinct workgroups that own ≥1 role (charts / donut)</li>
	 * <li>{@code workgroupsNotOwningRoles} – workgroups that do not own any role</li>
	 * <li>{@code singleMemberWorkgroups} – workgroups with exactly one member identity</li>
	 * <li>{@code workgroupsWithTermedMembers} – workgroups having ≥1 member with {@code inactive == true}</li>
	 * <li>{@code totalRoles} – all bundles (for % of roles owned by workgroups)</li>
	 * <li>{@code activeWorkgroups} / {@code disabledWorkgroups} – workgroup identity activation chart</li>
	 * </ul>
	 */
	public Map<String, Object> getWorkgroupDashboardStats() throws GeneralException {
		Map<String, Object> stats = new HashMap<>();

		QueryOptions allWg = new QueryOptions();
		allWg.addFilter(Filter.eq("workgroup", true));
		int totalWorkgroups = _context.countObjects(Identity.class, allWg);
		stats.put("totalWorkgroups", totalWorkgroups);

		QueryOptions disabledOps = new QueryOptions();
		disabledOps.addFilter(Filter.eq("workgroup", true));
		disabledOps.addFilter(Filter.eq("inactive", true));
		int disabledWorkgroups = _context.countObjects(Identity.class, disabledOps);
		stats.put("disabledWorkgroups", disabledWorkgroups);
		stats.put("activeWorkgroups", Math.max(0, totalWorkgroups - disabledWorkgroups));

		int totalRoles = _context.countObjects(Bundle.class, new QueryOptions());
		stats.put("totalRoles", totalRoles);

		Set<String> distinctWorkgroupIdsOwningRoles = new HashSet<>();
		int rolesOwnedByWorkgroups = 0;
		QueryOptions bundleIterOps = new QueryOptions();
		bundleIterOps.addFilter(Filter.notNull("owner"));
		Iterator<Bundle> bitr = _context.search(Bundle.class, bundleIterOps);
		if (bitr != null) {
			try {
				while (bitr.hasNext()) {
					Bundle b = bitr.next();
					Identity owner = b.getOwner();
					if (owner != null && owner.isWorkgroup()) {
						rolesOwnedByWorkgroups++;
						distinctWorkgroupIdsOwningRoles.add(owner.getId());
					}
				}
			} finally {
				Util.flushIterator(bitr);
			}
		}
		stats.put("rolesOwnedByWorkgroups", rolesOwnedByWorkgroups);
		stats.put("rolesWithWorkgroupOwner", rolesOwnedByWorkgroups);

		int distinctOwning = distinctWorkgroupIdsOwningRoles.size();
		stats.put("workgroupsOwningRoles", distinctOwning);
		stats.put("workgroupsNotOwningRoles", Math.max(0, totalWorkgroups - distinctOwning));

		int singleMemberWorkgroups = 0;
		int workgroupsWithTermedMembers = 0;
		QueryOptions wgIterOps = new QueryOptions();
		wgIterOps.addFilter(Filter.eq("workgroup", true));
		Iterator<Identity> wgit = _context.search(Identity.class, wgIterOps);
		if (wgit != null) {
			try {
				while (wgit.hasNext()) {
					Identity wg = wgit.next();
					QueryOptions memQ = new QueryOptions();
					memQ.addFilter(Filter.eq("workgroups.id", wg.getId()));
					int memberCount = _context.countObjects(Identity.class, memQ);
					if (memberCount == 1) {
						singleMemberWorkgroups++;
					}
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
		stats.put("singleMemberWorkgroups", singleMemberWorkgroups);
		stats.put("workgroupsWithTermedMembers", workgroupsWithTermedMembers);

		return stats;
	}

	/**
	 * Typeahead search for non-workgroup identities (potential owners / members).
	 */
	public List<Map<String, Object>> suggestIdentities(String query, int limit) throws GeneralException {
		int lim = limit <= 0 ? 25 : Math.min(limit, 50);
		QueryOptions ops = new QueryOptions();
		ops.addFilter(Filter.eq("workgroup", false));
		ops.addFilter(Filter.eq("inactive", false));
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
					rows.add(row);
				}
			} finally {
				Util.flushIterator(itr);
			}
		}
		return rows;
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
