package sailpoint.plugin.rolemanagement.rest;

import java.util.*;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;

import sailpoint.authorization.Authorizer;
import sailpoint.plugin.PluginContext;
import sailpoint.plugin.rolemanagement.service.IdentityService;
import sailpoint.plugin.rolemanagement.service.RoleService;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.rest.plugin.RequiredRight;
import sailpoint.tools.GeneralException;

@RequiredRight(value = "RoleManagementRight")
@Path("rolemanagement")
public class RoleManagementResource extends BasePluginResource {
	public static final Log logger = LogFactory.getLog(RoleManagementResource.class);
	
	public void log(String msg) {
		this.logger.error(msg);
	}
	
	@GET
	@Path("/roles")
	@Produces(MediaType.APPLICATION_JSON)
	public List<Map<String, String>> getRoles() throws GeneralException {
		log("Enter Roles");
		return  getRoleService().getRoles(getLoggedInUserName());		
	}
	
	@GET
    @Path("/role/{roleName}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRoleDetails(@PathParam("roleName") String roleName) throws GeneralException, JSONException {
        Map<String, Object> roleDetails = new HashMap<>();        
        roleDetails = getRoleService().getRole(roleName);
        return Response.ok(roleDetails).build();
    }
	
	@GET
    @Path("/workgroup/{groupId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGroupMembers(@PathParam("groupId") String groupId) throws GeneralException {
		 List<Map<String,Object>> workgroupMembers = getIdentityService().getWorkGroupMembers(groupId);
		 return Response.ok(workgroupMembers).build();
	}
	
	@GET
    @Path("/itroles/search")
    @Produces(MediaType.APPLICATION_JSON)
    public Response searchITRoles(@QueryParam("q") String query) throws GeneralException {
		try {
            List<Map<String, String>> matchedRoles = getRoleService().findMatchingITRoles(query);
            return Response.ok(matchedRoles).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error fetching IT roles").build();
        }
	}
	
	@Path("itroles/details/{id}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getITRoleDetails(@PathParam("id") String id) {
	    try {
            Map<String, Object> matchedRoles = getRoleService().getSingleITRoleDetails(id);
            return Response.ok(matchedRoles).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error fetching getITRoleDetails").build();
        }
	}


	@Override
	public String getPluginName() {
		return "rolemanagement";
	}
	
	private RoleService getRoleService() throws GeneralException {
		return new RoleService((PluginContext)this,getContext(),getLoggedInUser());
	}
	
	private IdentityService getIdentityService() throws GeneralException {
		return new IdentityService((PluginContext)this,getContext(),getLoggedInUser());
	}
	
	
}
