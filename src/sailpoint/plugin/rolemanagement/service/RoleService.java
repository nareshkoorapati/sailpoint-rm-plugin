/**
 * 
 */
package sailpoint.plugin.rolemanagement.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.json.JSONException;

import sailpoint.api.SailPointContext;

import sailpoint.object.Bundle;
import sailpoint.object.Custom;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.plugin.PluginContext;
import sailpoint.plugin.rolemanagement.util.Constants;
import sailpoint.plugin.rolemanagement.util.RuleUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.modeler.RoleUtil;

/**
 * @author shrey
 *
 */
public class RoleService {
	
	private static final Logger logger = Logger.getLogger(RoleService.class);
	
	
	private String logPrefix = "RoleService:";
	private PluginContext pluginContext;
	private SailPointContext _context;
	private Identity loggedInIdentity;
	private Map<String, Object> roleAttributes;
	private List<String> itDisAllowedAttributes;
	private List<String> buzDisAllowedAttributes;
	private List<ObjectAttribute> objectAttributes;
	private List<String> itRoleRequiredAttributes;
	private List<String> buzRoleRequiredAttributes;
	
	private String customObjectName;
	private Custom customObj;
	private Map<String,Object> config;
	
	/**
	 * @param customObjectName 
	 * @throws GeneralException 
	 * 
	 */
	public RoleService(PluginContext pluginContext, SailPointContext _context, Identity loggedInIdentity, String customObjectName) throws GeneralException {
		// TODO Auto-generated constructor stub
		log(logPrefix+"Enter RoleService constructor");
		this.pluginContext = pluginContext;
		this._context=_context;
		this.loggedInIdentity=loggedInIdentity;
		this.customObjectName = customObjectName;
		initialize();
		log(logPrefix+"Exit RoleService constructor");
	}
	
	private void initialize() throws GeneralException {
		log(logPrefix+"Enter initialize ");
		ObjectConfig objConfig = _context.getObjectByName(ObjectConfig.class, "Bundle");
		
		RoleTypeDefinition itDef = objConfig.getRoleType("it");
		this.itDisAllowedAttributes = itDef.getDisallowedAttributes();
		
		log(logPrefix+"this.itDisAllowedAttributes "+this.itDisAllowedAttributes);
	
	
		RoleTypeDefinition buzDef = objConfig.getRoleType("business");
		this.buzDisAllowedAttributes = buzDef.getDisallowedAttributes();	
		this.objectAttributes = objConfig.getObjectAttributes();
	
		
		log(logPrefix+"this.buzDisAllowedAttributes "+this.buzDisAllowedAttributes);
		log(logPrefix+"this.objectAttributes "+this.objectAttributes);
		
	
		this.customObj = _context.getObjectByName(Custom.class, customObjectName);
		log(logPrefix+"this.customObj "+this.customObj);
		if (customObj == null) {
			throw new GeneralException("Config Custom Object not found "+customObjectName);
		}
		else {
			this.config = customObj.getAttributes().getMap();
		}
	
	
		List<String> nonEditableFields = (List<String>) this.config.get(Constants.ROLE_NON_EDITABLE_FIELDS);
		log(logPrefix+"this.nonEditableFields "+nonEditableFields);
	
		this.itRoleRequiredAttributes = new ArrayList<String>();
		this.itRoleRequiredAttributes.add("displayName");
		this.itRoleRequiredAttributes.add("sysDescriptions");
		for(ObjectAttribute attr :  Util.safeIterable(this.objectAttributes)) {
			List<String> disAllowedAttributes = this.itDisAllowedAttributes;
			
			if (disAllowedAttributes == null) disAllowedAttributes = new ArrayList<String>();
			if (!disAllowedAttributes.contains(attr.getName()) 
					&& !nonEditableFields.contains(attr.getName())
					&& attr.isRequired()
				) {
				this.itRoleRequiredAttributes.add(attr.getName());
			}
		}
	
	
		this.buzRoleRequiredAttributes = new ArrayList<String>();
		this.buzRoleRequiredAttributes.add("displayName");
		this.buzRoleRequiredAttributes.add("sysDescriptions");
		for(ObjectAttribute attr :  Util.safeIterable(this.objectAttributes)) {
			List<String> disAllowedAttributes = this.buzDisAllowedAttributes;
			
			if (disAllowedAttributes == null) disAllowedAttributes = new ArrayList<String>();
			if (!disAllowedAttributes.contains(attr.getName()) 
					&& !nonEditableFields.contains(attr.getName())
					&& attr.isRequired()
				) {
				this.buzRoleRequiredAttributes.add(attr.getName());
			}
		}
		log(logPrefix+"Exit initialize ");
		
	}
	
	public void log(String msg) {
		this.logger.error(msg);
	}
	
	public Map<String,Object> getFullRole(Bundle b) {
		log(logPrefix+"Enter getFullRole for "+b.getName());
		Map<String,Object> returnMap = new HashMap<>();
		try {
			
			returnMap.put("name",b.getName());
			returnMap.put("displayName",b.getDisplayName());
			returnMap.put("type",b.getType());
			returnMap.put("owner", b.getOwner() != null ? b.getOwner().getName() : null);
			returnMap.put("memberCount", countRoleMembers(b.getId()));
			returnMap.put("id", b.getId());
			returnMap.put("created", b.getCreated());
			returnMap.put("modified", b.getModified());
			returnMap.put("description", b.getDescription("en_US"));
			
			Map<String,Object> extendedMap = b.getExtendedAttributes();
			if (extendedMap != null && !extendedMap.isEmpty()) {
				if (extendedMap.containsKey("accountSelectorRules")) {
					extendedMap.remove("accountSelectorRules");
				}
				if (extendedMap.containsKey("provisioningPolicyAttributes")) {
					extendedMap.remove("provisioningPolicyAttributes");
				}
			}
			returnMap.putAll(extendedMap);
			List<String> requiredFields = new ArrayList<String>();
			if (b.getType().equals(Constants.BUSINESS)) {
				requiredFields = buzRoleRequiredAttributes;
			}
			else if (b.getType().equals(Constants.IT)) {
				requiredFields = itRoleRequiredAttributes;
			}
			long filledCount = requiredFields.stream()
		            .filter(attr -> hasValue(returnMap.get(attr)))
		            .count();
	        int complianceScore = (int) Math.round((filledCount * 100.0) / requiredFields.size());
	        returnMap.put("complaineScore", complianceScore);
	        
		}
		catch(Exception e) {
			log(logPrefix+"Exception in getFullRole for "+b.getName());
		}
		log(logPrefix+"Exit getFullRole for "+returnMap);
		return returnMap;
	}
	
	private static boolean hasValue(Object value) {
        if (value == null) return false;

        if (value instanceof String) {
            return !((String) value).trim().isEmpty();
        }

        if (value instanceof List<?>) {
            return !((List<?>) value).isEmpty();
        }

        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.isEmpty()) return false;
            // check if ALL values are empty/null
            boolean allEmpty = map.values().stream().allMatch(v -> !hasValue(v));
            return !allEmpty;
        }

        return true; // numbers, booleans, etc.
    }
	
	public Map<String,Object> getRoles(String loggedInUser, int start, int limit, String sort, String dir, String query, String filter,
			String roleType, String ownerId, String requestable, String extendedAttrQuery) throws GeneralException{
		log(logPrefix+"Enter getRoles with loggedInUser");
		Map<String,Object> rolesMap = new HashMap<>();
		rolesMap.put("start", start);
		rolesMap.put("limit", limit);
		rolesMap.put("sort", sort);
		rolesMap.put("dir", dir);
		rolesMap.put("query", query);
		rolesMap.put("filter", filter);
		
		List<Map<String,Object>> results = new ArrayList<Map<String,Object>>();
		sailpoint.api.SailPointContext ctx = getContext();
		
		log(logPrefix+"Logged in User:" + loggedInUser);
		
		QueryOptions ops = new QueryOptions();
		ops.addFilter(Filter.or(Filter.eq("type",Constants.IT),Filter.eq("type",Constants.BUSINESS)));
		if(Util.isNotNullOrEmpty(query)) {
			Filter queryFilter = Filter.or(Filter.like("name", query),Filter.like("displayName", query));
			ops.addFilter(queryFilter);
		}
		if(Util.isNotNullOrEmpty(filter)) {
			log(logPrefix+" filter is "+filter);
			Filter f = Filter.compile(filter);
			ops.addFilter(f);
		}
		appendRoleFacetFilters(ops, roleType, ownerId, requestable, extendedAttrQuery);
		ops.addFilter(QueryOptions.getOwnerScopeFilterUsingSubqueries(ctx.getObjectByName(Identity.class, loggedInUser),"owner"));
		rolesMap.put("total", ctx.countObjects(Bundle.class, ops));
		ops.setResultLimit(limit);
		ops.setFirstRow(start);
		ops.setOrderBy(sort != null ? sort : "modified");
		if (dir != null && dir.equalsIgnoreCase("ASC")) {
			ops.setOrderAscending(true);
		} else {	
			ops.setOrderAscending(false);
		}
		log(logPrefix+"roles filters " + ops.getFilters().toString());
		
		Iterator<Bundle> itr = ctx.search(Bundle.class,ops);
		if (itr != null) {
			while(itr.hasNext()) {				
				
				results.add(getFullRole((Bundle)itr.next()));
			}
			sailpoint.tools.Util.flushIterator(itr);
		}
		rolesMap.put("objects", results);
		log(logPrefix+"getRiles RoleMap "+rolesMap);
		return rolesMap;
	}

	private void appendRoleFacetFilters(QueryOptions ops, String roleType, String ownerId, String requestable,
			String extendedAttrQuery) throws GeneralException {
		if (Util.isNotNullOrEmpty(roleType)) {
			String rt = roleType.trim();
			if (Constants.IT.equalsIgnoreCase(rt)) {
				ops.addFilter(Filter.eq("type", Constants.IT));
			} else if (Constants.BUSINESS.equalsIgnoreCase(rt)) {
				ops.addFilter(Filter.eq("type", Constants.BUSINESS));
			}
		}
		if (Util.isNotNullOrEmpty(ownerId)) {
			ops.addFilter(Filter.eq("owner.id", ownerId.trim()));
		}
		if (Util.isNotNullOrEmpty(requestable)) {
			String r = requestable.trim();
			if ("true".equalsIgnoreCase(r)) {
				ops.addFilter(Filter.eq("privileged", true));
			} else if ("false".equalsIgnoreCase(r)) {
				ops.addFilter(Filter.eq("privileged", false));
			}
		}
		Filter ext = buildExtendedAttributesSearchFilter(extendedAttrQuery);
		if (ext != null) {
			ops.addFilter(ext);
		}
	}

	private Filter buildExtendedAttributesSearchFilter(String q) throws GeneralException {
		if (Util.isNullOrEmpty(q)) {
			return null;
		}
		String needle = q.trim();
		if (needle.length() < 2) {
			return null;
		}
		ObjectConfig oc = _context.getObjectByName(ObjectConfig.class, "Bundle");
		if (oc == null) {
			return null;
		}
		List<ObjectAttribute> attrs = oc.getObjectAttributes();
		if (attrs == null || attrs.isEmpty()) {
			return null;
		}
		List<Filter> parts = new ArrayList<>();
		for (ObjectAttribute attr : attrs) {
			if (attr == null) {
				continue;
			}
			String name = attr.getName();
			if (Util.isNullOrEmpty(name)) {
				continue;
			}
			try {
				parts.add(Filter.like("attributes." + name, needle, Filter.MatchMode.ANYWHERE));
			} catch (Exception ex) {
				log(logPrefix + "extended attr filter skip " + name + ": " + ex.getMessage());
			}
		}
		if (parts.isEmpty()) {
			return null;
		}
		return parts.size() == 1 ? parts.get(0) : Filter.or(parts.toArray(new Filter[0]));
	}

	public Map<String, Object> getRolesOwnedByWorkgroup(String workgroupId) throws GeneralException {
		Map<String, Object> returnMap = new HashMap<String,Object>();
		List<Map<String,Object>> roles = new ArrayList<Map<String,Object>>();

		QueryOptions ops = new QueryOptions();
		ops.addFilter(Filter.eq("owner.id", workgroupId));
		ops.addFilter(Filter.or(Filter.eq("type", Constants.IT), Filter.eq("type", Constants.BUSINESS)));
		ops.setOrderBy("name");
		ops.setOrderAscending(true);

		returnMap.put("total", _context.countObjects(Bundle.class, ops));

		Iterator<Bundle> itr = _context.search(Bundle.class, ops);
		if (itr != null) {
			while (itr.hasNext()) {
				Bundle role = itr.next();
				Map<String,Object> row = new HashMap<String,Object>();
				row.put("id", role.getId());
				row.put("name", role.getName());
				row.put("displayName", role.getDisplayName());
				String type = role.getType();
				if ("it".equals(type)) type = "IT";
				if ("business".equals(type)) type = "Business";
				row.put("type", type);
				row.put("status", role.isDisabled() ? "Disabled" : "Enabled");
				roles.add(row);
			}
			Util.flushIterator(itr);
		}

		List<Map<String,Object>> headers = new ArrayList<Map<String,Object>>();
		headers.add(createHeader("name", "Role Name"));
		headers.add(createHeader("displayName", "Display Name"));
		headers.add(createHeader("type", "Type"));
		headers.add(createHeader("status", "Status"));

		returnMap.put("headers", headers);
		returnMap.put("objects", roles);
		return returnMap;
	}

	public Map<String, Object> getRole(String roleName) throws GeneralException, JSONException {
		Map<String, Object> roleDetails = new HashMap<>();
		SailPointContext ctx = getContext();
		
		Bundle b = ctx.getObjectById(Bundle.class,roleName);
		if (b==null) throw new GeneralException(roleName+" not found.");
		
		roleDetails.putAll(getRoleBasiDetails(b));

        // Add Extended Attributes        
        roleDetails.put("extendedAttributes", getRoleExtendedAttributesMap(b));

        // Add Entitlements
        
        roleDetails.put("entitlements", getRoleSimpleEntitlements(b));
        roleDetails.put("itRoles", getRoleItRoles(b));
		return roleDetails;
	}
	
	private SailPointContext getContext() throws GeneralException {
		return _context;
	}
	
	public Map<String, Object> getRoleBasiDetails(Bundle b) {
		Map<String, Object> basicDetails = new HashMap<>();
		basicDetails.put("id", b.getId());
		basicDetails.put("name", b.getName());
		basicDetails.put("displayName", b.getDisplayName());
		basicDetails.put("description", b.getDescription("en_US"));		
		basicDetails.put("status",b.isDisabled() ? "Disabled" : "Enabled");
		
		String type = b.getType();
		if ("it".equals(type)) type = "IT";
		if ("business".equals(type)) type ="Business";
		basicDetails.put("type", type);
		
		Identity owner = b.getOwner();
		if (owner != null) {
			basicDetails.put("ownerIsWorkgroup", owner.isWorkgroup());
			basicDetails.put("owner", owner.getDisplayName());
			basicDetails.put("ownerId", owner.getId());
		}

		basicDetails.put("memberCount", countRoleMembers(b.getId()));

		return basicDetails;
	}
	
	public Map<String, Object> getRoleExtendedAttributes(Bundle b) throws GeneralException {
		Map<String, Object> extendedAttrs = new HashMap<>();
		List<String> disAllowedAttributes = null;
		if (b.getType().equals("it")) disAllowedAttributes = itDisAllowedAttributes;
		else if(b.getType().equals("business")) disAllowedAttributes = buzDisAllowedAttributes;
		
		if (disAllowedAttributes == null) disAllowedAttributes = new ArrayList<String>();
		
		for(ObjectAttribute attr :  Util.safeIterable(objectAttributes)) {
			if (!disAllowedAttributes.contains(attr.getName())) {
				extendedAttrs.put(attr.getDisplayableName(Locale.ENGLISH),b.getAttribute(attr.getName()));
			}
		}
		
		return extendedAttrs;
		
	}
	
	public List<Map<String,Object>> getRoleExtendedAttributesMap(Bundle b) {
		List<Map<String,Object>> list = new ArrayList<>();
		
		List<String> disAllowedAttributes = null;
		if (b.getType().equals("it")) disAllowedAttributes = itDisAllowedAttributes;
		else if(b.getType().equals("business")) disAllowedAttributes = buzDisAllowedAttributes;
		
		if (disAllowedAttributes == null) disAllowedAttributes = new ArrayList<String>();
		
		List<String> nonEditableFields = (List<String>) config.get(Constants.ROLE_NON_EDITABLE_FIELDS);
		
		for(ObjectAttribute attr :  Util.safeIterable(objectAttributes)) {
			if (!disAllowedAttributes.contains(attr.getName())) {
				Map<String, Object> map = new HashMap<>();
				map.put("name", attr.getName());
				map.put("displayName", attr.getDisplayableName(Locale.ENGLISH));
				map.put("value", b.getAttribute(attr.getName()));
				map.put("required", attr.isRequired());
				map.put("type", attr.getType());
				map.put("defaultValue", attr.getDefaultValue());
				List<Object> allowedValues =  attr.getAllowedValues();
				if (allowedValues != null) {
					Util.removeDuplicates(allowedValues);
				}
				map.put("allowedValues", allowedValues);
				map.put("helpKey", attr.getDescription());
				boolean isEdit = true;
				if (Util.nullSafeSize(nonEditableFields) > 0) {
					isEdit = !nonEditableFields.contains(attr.getName());					
				}
				map.put("editable", isEdit);
				list.add(map);
			}
		}
		return list;
	}
	
	private Map<String,Object> createHeader(String key, String label) {
		Map<String,Object> header = new HashMap<String,Object>();
		header.put("key", key);
		header.put("label", label);
		return header;
	}
	
	public List<Map<String, Object>> getRoleSimpleEntitlements(Bundle b) throws GeneralException, JSONException {
		log(logPrefix+"Enter getRoleProfiles");
		List<Map<String, Object>> entitlements = new ArrayList<>();
		entitlements = RoleUtil.getAllSimpleEntitlements(b,_context,Locale.ENGLISH,null,loggedInIdentity);
		
		return entitlements;
	}
	
	public Map<String, Object> getSingleITRoleDetails(String id) throws GeneralException, JSONException {
		log(logPrefix+"Enter getRoleSimpleEntitlements");
		Map<String, Object> roleMap = new HashMap<>();
		try {
	        if (id == null || id.trim().isEmpty()) {
	            logger.error("getRoleSimpleEntitlements: Provided ID is null or empty.");
	            return roleMap;
	        }

	        // Load the IT Role (Bundle)
	        Bundle itRole = _context.getObjectById(Bundle.class, id);
	        if (itRole == null) {
	            logger.warn("getRoleSimpleEntitlements: No Bundle found with ID: " + id);
	            return roleMap;
	        }
	        
	        roleMap.put("id", id);
	        roleMap.put("name", itRole.getName());
	        roleMap.put("displayName", itRole.getDisplayableName());
	        
	        roleMap.put("entitlements",getRoleSimpleEntitlements(itRole));
	        
	        logger.debug(logPrefix+"Fetched IT Role details with entitlements for ID: " + id);
	    } catch (Exception e) {
	    	logger.error(logPrefix+"Error in getITRoleDetails() for ID: " + id, e);
	    }
		
		return roleMap;
	}
	
	
	public List<Map<String,Object>> getRoleItRoles(Bundle b) throws GeneralException, JSONException {
		List<Map<String,Object>> list = new ArrayList<>();
		
		if (Constants.BUSINESS.equals(b.getType())) {
			List<Bundle> itRoles = b.getRequirements();
			for(Bundle itRole : Util.safeIterable(itRoles)) {
				Map<String,Object> itRoleMap = new HashMap<String,Object>();
				itRoleMap.put("name", itRole.getName());
				itRoleMap.put("displayName", itRole.getDisplayableName());
				itRoleMap.put("entitlements", getRoleSimpleEntitlements(itRole));
				list.add(itRoleMap);
			}
		}
		else
			return null;		
		
		return list;
	}

	public List<Map<String, String>> findMatchingITRoles(String query) throws GeneralException {
        List<Map<String, String>> results = new ArrayList<>();

        try {
        	logger.debug(logPrefix+"Enter findMatchingITRoles with query: " + query);

            if (query == null || query.trim().isEmpty()) {
            	logger.debug("Query is empty or null. Returning empty result.");
                return results;
            }

            QueryOptions qo = new QueryOptions();
            qo.addFilter(
                Filter.and(
                    Filter.eq("type", Constants.IT),
                    Filter.or(
                        Filter.like("name", query, Filter.MatchMode.ANYWHERE),
                        Filter.like("displayName", query, Filter.MatchMode.ANYWHERE)
                    )
                )
            );

            List<String> props = Arrays.asList("id", "name", "displayName");
            Iterator<Object[]> roles = _context.search(Bundle.class, qo, props);

            if (roles != null) {
                while (roles.hasNext()) {
                    Object[] row = roles.next();
                    Map<String, String> roleMap = new HashMap<>();
                    roleMap.put("id", (String) row[0]);
                    roleMap.put("name", (String) row[1]);
                    roleMap.put("displayName", (String) row[2]);
                    results.add(roleMap);
                }
            }

            logger.debug(logPrefix+"Exit findMatchingITRoles. Found " + results.size() + " roles.");
        } catch (Exception e) {
        	logger.error(logPrefix+"Error occurred in findMatchingITRoles: " + e.getMessage(), e);
            throw new GeneralException("Failed to search for IT Roles", e);
        }

        return results;
    }

	public List<Map<String, Object>> getRoleTableColumns() {
		List<Map<String, Object>> columns = new ArrayList<>();

		 // Define all available columns
	    columns.add(createColumn("name", "Role Name", true, true));
	    columns.add(createColumn("displayName", "Display Name", true,true));
	    columns.add(createColumn("type", "Type", true,false));
	    columns.add(createColumn("description", "Description", true,false));
	    columns.add(createColumn("owner", "Role Owner", true,false));
	    columns.add(createColumn("memberCount", "Member Count", true,false));
	    columns.add(createColumn("created", "Created", true, true)); 
		columns.add(createColumn("modified", "Modified", true, true)); 
	    
	    for(ObjectAttribute attr :  Util.safeIterable(objectAttributes)) {
	    	columns.add(createColumn(attr.getName(), attr.getDisplayableName(Locale.ENGLISH),false,false));
		}

	    return columns;
	}
	
	private Map<String, Object> createColumn(String name, String label, boolean defaultVisible,boolean sortable) {
	    Map<String, Object> col = new HashMap<>();
	    col.put("name", name);
	    col.put("label", label);
	    col.put("defaultVisible", defaultVisible);
		col.put("sortable", sortable);
	    return col;
	}

	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> getDashboardCards(String loggedInUserName) throws Exception {
		log(logPrefix+"Enter getDashboardCards");
		
		String ruleName = null;
		List<Map<String, Object>> result = null;
		
		try {
			ruleName = (String) config.get(Constants.DASHBOARD_RULE);
			Map<String,Object> params = new HashMap<String,Object>();
			params.put("loggedInUser", loggedInUserName);
			result = (List<Map<String, Object>>) RuleUtil.runIIQRule(_context, ruleName, params);
		}
		catch(Exception e) {
			log(logPrefix+"Error in getDashboardCards "+e);
			throw e;
		}
		return result;
	}

	public String downloadRoles(String loggedInUserName, Map<String, Object> body) throws Exception{
		log(logPrefix+"Enter downloadRoles");
		
		String ruleName = null;
		String result = null;
		
		try {
			ruleName = (String) config.get(Constants.DOWNLOAD_RULE);
			Map<String,Object> params = new HashMap<String,Object>();
			List<String> roleIds = (List<String>) body.get("roleIds");
			params.put("loggedInUser", loggedInUserName);
			params.put("roleIds", roleIds);
			params.put("notify", false);
			result = (String) RuleUtil.runIIQRule(_context, ruleName, params);
		}
		catch(Exception e) {
			log(logPrefix+"Error in downloadRoles "+e);
			throw e;
		}
		return result;
	}

	public Map<String, Object> getRoleDownloadTimeOut() {
		log(logPrefix+"Enter getRoleDownloadTimeOut");
		Map<String,Object> returnMap = new HashMap<String,Object>();
		int timeoutInMillis = 30000; //default
		try {
			timeoutInMillis = (int) config.get(Constants.DOWNLOAD_TIMEOUT);
			returnMap.put("timeout", timeoutInMillis);
		}
		catch(Exception e) {
			log(logPrefix+"Error in getRoleDownloadTimeOut "+e);
			throw e;
		}
		log(logPrefix+"Exit getRoleDownloadTimeOut");
		return returnMap;
	}
	
	@SuppressWarnings("unchecked")
	public Map<String, Object> getWorkGroupMembers(String loggedInUserName, String workgroupId) throws GeneralException {
		Map<String, Object> returnMap = new HashMap<String,Object>();
		
		log(logPrefix+"Enter getWorkGroupMembers");
		
		String ruleName = null;		
		
		try {
			ruleName = (String) config.get(Constants.GET_WORKGROUP_RULE);
			Map<String,Object> params = new HashMap<String,Object>();			
			params.put("loggedInUser", loggedInUserName);
			params.put("workgroupId", workgroupId);
			returnMap = (Map<String, Object>) RuleUtil.runIIQRule(_context, ruleName, params);
		}
		catch(Exception e) {
			log(logPrefix+"Error in getWorkGroupMembers "+e);
			throw e;
		}
		return returnMap;
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> updateRole(String loggedInUserName, Map<String, Object> newRoleMap) throws Exception {
		Map<String, Object> returnMap = new HashMap<String,Object>();
		
		log(logPrefix+"Enter getWorkGroupMembers");
		
		String ruleName = null;		
		
		try {
			ruleName = (String) config.get(Constants.UPDATE_RULE);
			Map<String,Object> params = new HashMap<String,Object>();			
			params.put("loggedInUser", loggedInUserName);
			params.put("newRoleMap", newRoleMap);
			returnMap = (Map<String, Object>) RuleUtil.runIIQRule(_context, ruleName, params);
		}
		catch(Exception e) {
			log(logPrefix+"Error in getWorkGroupMembers "+e);
			throw e;
		}
		return returnMap;
	}

	public Map<String, Object> performBulkAction(String loggedInUserName, Map<String, Object> request) throws Exception {
		log("Enter performBulkAction "+request);
		Map<String,Object> result = null;
		
		String action = (String) request.get("action");
		if (action.equals("export")) {
			String ruleName = null;
			String csvContent = null;
			
			try {
				ruleName = (String) config.get(Constants.DOWNLOAD_RULE);
				Map<String,Object> params = new HashMap<String,Object>();
				List<String> roleIds = (List<String>) request.get("roleIds");
				params.put("loggedInUser", loggedInUserName);
				params.put("roleIds", roleIds);
				params.put("notify", false);
				csvContent = (String) RuleUtil.runIIQRule(_context, ruleName, params);
			}
			catch(Exception e) {
				log(logPrefix+"Error in downloadRoles "+e);
				throw e;
			}
		}
		
		
		return result;
	}

	public Map<String, Object> getConfig() {
		log(logPrefix+"Enter getDebugConfig");
		Map<String,Object> returnMap = new HashMap<String,Object>();
		try {
			returnMap =  (Map<String, Object>) config.get(Constants.CONFIG);
			
		}
		catch(Exception e) {
			log(logPrefix+"Error in getDebugConfig "+e);
			throw e;
		}
		log(logPrefix+"Exit getDebugConfig");
		return returnMap;
	}

	/**
	 * Same semantics as bulk-request config / UI {@code isRoleAdmin} (bulk request config rule).
	 */
	public boolean isRoleAdmin() {
		try {
			String ruleName = (String) config.get(Constants.BULK_REQUEST_CONFIG_RULE);
			if (Util.isNullOrEmpty(ruleName)) {
				return false;
			}
			Map<String, Object> params = new HashMap<>();
			params.put("loggedInUser", loggedInIdentity != null ? loggedInIdentity.getName() : null);
			@SuppressWarnings("unchecked")
			Map<String, Object> userConfig = (Map<String, Object>) RuleUtil.runIIQRule(_context, ruleName, params);
			if (userConfig == null) {
				return false;
			}
			Object raw = userConfig.get("isRoleAdmin");
			if (raw instanceof Boolean) {
				return (Boolean) raw;
			}
			return Util.otob(raw);
		} catch (Exception e) {
			log(logPrefix + "Error in isRoleAdmin " + e.getMessage());
			return false;
		}
	}

	/**
	 * Member count = identities with the role assigned plus identities with the role detected
	 * (computed) but not directly assigned. IT roles may be both assignable and detected.
	 */
	public int countRoleMembers(String roleId) {
		if (Util.isNullOrEmpty(roleId)) {
			return 0;
		}
		try {
			return countAssignedRoleMembers(roleId) + countDetectedRoleMembers(roleId);
		} catch (GeneralException e) {
			log(logPrefix + "Error counting role members for " + roleId + ": " + e.getMessage());
			return 0;
		}
	}

	public Map<String, Object> getRoleMembers(String roleId, int start, int limit) throws GeneralException {
		Map<String, Object> result = new HashMap<>();
		result.put("headers", roleMemberHeaders());
		result.put("start", start);
		result.put("limit", limit);

		if (Util.isNullOrEmpty(roleId)) {
			result.put("total", 0);
			result.put("objects", new ArrayList<Map<String, Object>>());
			return result;
		}

		int total = countRoleMembers(roleId);
		result.put("total", total);

		List<Map<String, Object>> members = collectRoleMemberRows(roleId, Math.max(start, 0), Math.max(limit, 0));
		result.put("objects", members);
		return result;
	}

	public String downloadRoleMembersCsv(String roleId) throws GeneralException {
		Bundle role = _context.getObjectById(Bundle.class, roleId);
		String roleName = role != null ? role.getName() : roleId;

		StringBuilder sb = new StringBuilder();
		sb.append("Identity Name,First Name,Last Name,Employee ID\n");

		for (Map<String, Object> row : collectRoleMemberRows(roleId, 0, Integer.MAX_VALUE)) {
			sb.append(escapeCsvField(stringValue(row.get("name")))).append(',');
			sb.append(escapeCsvField(stringValue(row.get("firstName")))).append(',');
			sb.append(escapeCsvField(stringValue(row.get("lastName")))).append(',');
			sb.append(escapeCsvField(stringValue(row.get("employeeId")))).append('\n');
		}

		log(logPrefix + "downloadRoleMembersCsv for role " + roleName);
		return sb.toString();
	}

	/**
	 * IIQ search on link collections (assignedRoles/bundles) can return the same identity
	 * once per matching link. Clone results + id de-duplication keeps list aligned with count.
	 */
	private List<Map<String, Object>> collectRoleMemberRows(String roleId, int start, int limit)
			throws GeneralException {
		List<Map<String, Object>> members = new ArrayList<>();
		if (Util.isNullOrEmpty(roleId) || limit <= 0) {
			return members;
		}

		QueryOptions qo = new QueryOptions();
		qo.addFilter(buildRoleMemberFilter(roleId));
		qo.addFilter(Filter.eq("workgroup", false));
		qo.setCloneResults(true);
		qo.setOrderBy("name");
		qo.setOrderAscending(true);

		Set<String> seenIds = new HashSet<>();
		int skipped = 0;
		Iterator<Identity> itr = _context.search(Identity.class, qo);
		if (itr != null) {
			try {
				while (itr.hasNext() && members.size() < limit) {
					Identity identity = itr.next();
					if (identity == null || Util.isNullOrEmpty(identity.getId())) {
						continue;
					}
					if (!seenIds.add(identity.getId())) {
						continue;
					}
					if (skipped < start) {
						skipped++;
						continue;
					}
					members.add(roleMemberRow(identity));
				}
			} finally {
				Util.flushIterator(itr);
			}
		}
		return members;
	}

	private int countAssignedRoleMembers(String roleId) throws GeneralException {
		QueryOptions qo = new QueryOptions();
		qo.addFilter(Filter.contains("assignedRoles.id", roleId));
		qo.addFilter(Filter.eq("workgroup", false));
		qo.setCloneResults(true);
		return (int) _context.countObjects(Identity.class, qo);
	}

	private int countDetectedRoleMembers(String roleId) throws GeneralException {
		QueryOptions qo = new QueryOptions();
		qo.addFilter(Filter.contains("bundles.id", roleId));
		qo.addFilter(Filter.not(Filter.contains("assignedRoles.id", roleId)));
		qo.addFilter(Filter.eq("workgroup", false));
		qo.setCloneResults(true);
		return (int) _context.countObjects(Identity.class, qo);
	}

	private Filter buildRoleMemberFilter(String roleId) {
		Filter detectedOnly = Filter.and(
				Filter.contains("bundles.id", roleId),
				Filter.not(Filter.contains("assignedRoles.id", roleId)));
		return Filter.or(Filter.contains("assignedRoles.id", roleId), detectedOnly);
	}

	private static List<Map<String, Object>> roleMemberHeaders() {
		List<Map<String, Object>> headers = new ArrayList<>();
		headers.add(roleMemberHeader("Identity Name", "name"));
		headers.add(roleMemberHeader("First Name", "firstName"));
		headers.add(roleMemberHeader("Last Name", "lastName"));
		headers.add(roleMemberHeader("Employee ID", "employeeId"));
		return headers;
	}

	private static Map<String, Object> roleMemberHeader(String label, String key) {
		Map<String, Object> header = new HashMap<>();
		header.put("label", label);
		header.put("key", key);
		return header;
	}

	private static Map<String, Object> roleMemberRow(Identity identity) {
		Map<String, Object> member = new HashMap<>();
		member.put("id", identity.getId());
		member.put("name", identity.getName());
		member.put("firstName", identity.getFirstname() != null ? identity.getFirstname() : "");
		member.put("lastName", identity.getLastname() != null ? identity.getLastname() : "");
		Object employeeId = identity.getAttribute("employeeid");
		if (employeeId == null) {
			employeeId = identity.getAttribute("employeeId");
		}
		member.put("employeeId", employeeId != null ? employeeId.toString() : "");
		return member;
	}

	private static String stringValue(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private static String escapeCsvField(String value) {
		if (value == null) {
			return "";
		}
		if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
				|| value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

}
