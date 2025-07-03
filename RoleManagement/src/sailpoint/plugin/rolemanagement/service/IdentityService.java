/**
 * 
 */
package sailpoint.plugin.rolemanagement.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
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
