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
	 * Aggregate counts for the workgroup management dashboard: totals, active/disabled,
	 * how many workgroups own at least one role, and how many roles list a workgroup as owner.
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

		Set<String> workgroupIdsOwningRoles = new HashSet<>();
		int rolesWithWorkgroupOwner = 0;

		QueryOptions bundleOps = new QueryOptions();
		bundleOps.addFilter(Filter.notNull("owner"));
		Iterator<Bundle> bitr = _context.search(Bundle.class, bundleOps);
		if (bitr != null) {
			try {
				while (bitr.hasNext()) {
					Bundle b = bitr.next();
					Identity owner = b.getOwner();
					if (owner != null && owner.isWorkgroup()) {
						rolesWithWorkgroupOwner++;
						workgroupIdsOwningRoles.add(owner.getId());
					}
				}
			} finally {
				Util.flushIterator(bitr);
			}
		}

		int workgroupsOwningRoles = workgroupIdsOwningRoles.size();
		stats.put("workgroupsOwningRoles", workgroupsOwningRoles);
		stats.put("workgroupsNotOwningRoles", Math.max(0, totalWorkgroups - workgroupsOwningRoles));
		stats.put("rolesWithWorkgroupOwner", rolesWithWorkgroupOwner);

		return stats;
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
