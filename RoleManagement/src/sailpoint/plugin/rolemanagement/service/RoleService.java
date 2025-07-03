/**
 * 
 */
package sailpoint.plugin.rolemanagement.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.log4j.Logger;
import org.json.JSONException;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Profile;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.plugin.PluginContext;
import sailpoint.plugin.rolemanagement.util.Constants;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.modeler.RoleUtil;

/**
 * @author shrey
 *
 */
public class RoleService {
	
	private static final Logger logger = Logger.getLogger(RoleService.class);

	private PluginContext pluginContext;
	private SailPointContext _context;
	private Identity loggedInIdentity;
	private Map<String, Object> roleAttributes;
	private List<String> itDisAllowedAttributes;
	private List<String> buzDisAllowedAttributes;
	private List<ObjectAttribute> objectAttributes;
	
	/**
	 * @throws GeneralException 
	 * 
	 */
	public RoleService(PluginContext pluginContext, SailPointContext _context, Identity loggedInIdentity) throws GeneralException {
		// TODO Auto-generated constructor stub
		this.pluginContext = pluginContext;
		this._context=_context;
		this.loggedInIdentity=loggedInIdentity;
		initialize();
	}
	
	private void initialize() throws GeneralException {
		ObjectConfig config = _context.getObjectByName(ObjectConfig.class, "Bundle");
		RoleTypeDefinition itDef = config.getRoleType("it");
		this.itDisAllowedAttributes = itDef.getDisallowedAttributes();
		
		RoleTypeDefinition buzDef = config.getRoleType("business");
		this.buzDisAllowedAttributes = buzDef.getDisallowedAttributes();	
		
		
		this.objectAttributes = config.getObjectAttributes();
	}
	
	public void log(String msg) {
		this.logger.error(msg);
	}
	
	public List<Map<String,String>> getRoles(String loggedInUser) throws GeneralException{
		List<Map<String,String>> results = new ArrayList<Map<String,String>>();
		sailpoint.api.SailPointContext ctx = getContext();
		
		log("Logged in User:" + loggedInUser);
		
		QueryOptions ops = new QueryOptions();
		ops.addFilter(Filter.or(Filter.eq("type","it"),Filter.eq("type","business")));
		List<String> props = new ArrayList<String>();
		props.add("name");
		props.add("displayName");
		props.add("type");
		
		props.add("owner.name");
		props.add("id");
		
		Iterator<Object[]> itr = ctx.search(Bundle.class,ops,props);
		if (itr != null) {
			while(itr.hasNext()) {
				Map<String, String> map = new HashMap<String, String>();
				Object[] row = (Object[])itr.next();
				map.put("name", (String)row[0]);
				map.put("displayName", (String)row[1]);
				map.put("type", (String)row[2]);
				
				map.put("owner", (String)row[3]);
				map.put("id", (String)row[4]);
				
				results.add(map);
			}
			sailpoint.tools.Util.flushIterator(itr);
		}
		
		return results;
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
		
		for(ObjectAttribute attr :  Util.safeIterable(objectAttributes)) {
			if (!disAllowedAttributes.contains(attr.getName())) {
				Map<String, Object> map = new HashMap<>();
				map.put("name", attr.getName());
				map.put("displayName", attr.getDisplayableName(Locale.ENGLISH));
				map.put("value", b.getAttribute(attr.getName()));
				map.put("required", attr.isRequired());
				map.put("required", attr.isRequired());
				map.put("type", attr.getType());
				map.put("defaultValue", attr.getDefaultValue());
				map.put("allowedValues", attr.getAllowedValues());
				map.put("helpKey", attr.getDescription());
				list.add(map);
			}
		}
		return list;
	}
	
	public List<Map<String, Object>> getRoleSimpleEntitlements(Bundle b) throws GeneralException, JSONException {
		log("Enter getRoleProfiles");
		List<Map<String, Object>> entitlements = new ArrayList<>();
		entitlements = RoleUtil.getAllSimpleEntitlements(b,_context,Locale.ENGLISH,null,loggedInIdentity);
		
		return entitlements;
	}
	
	public Map<String, Object> getSingleITRoleDetails(String id) throws GeneralException, JSONException {
		log("Enter getRoleSimpleEntitlements");
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
	        
	        logger.debug("Fetched IT Role details with entitlements for ID: " + id);
	    } catch (Exception e) {
	    	logger.error("Error in getITRoleDetails() for ID: " + id, e);
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
        	logger.debug("Enter findMatchingITRoles with query: " + query);

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

            logger.debug("Exit findMatchingITRoles. Found " + results.size() + " roles.");
        } catch (Exception e) {
        	logger.error("Error occurred in findMatchingITRoles: " + e.getMessage(), e);
            throw new GeneralException("Failed to search for IT Roles", e);
        }

        return results;
    }

}
