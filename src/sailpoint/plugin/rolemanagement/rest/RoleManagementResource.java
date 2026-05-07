package sailpoint.plugin.rolemanagement.rest;

import java.util.*;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;

import bsh.org.objectweb.asm.Constants;
import sailpoint.authorization.Authorizer;
import sailpoint.plugin.PluginContext;
import sailpoint.plugin.rolemanagement.service.IdentityService;
import sailpoint.plugin.rolemanagement.service.RoleService;
import sailpoint.rest.plugin.Deferred;
import sailpoint.rest.plugin.AllowAll;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.rest.plugin.RequiredRight;
import sailpoint.tools.GeneralException;

//@RequiredRight(value = "RoleManagementRight")

@AllowAll
@Path("rolemanagement")
public class RoleManagementResource extends BasePluginResource {
	public static final Log logger = LogFactory.getLog(RoleManagementResource.class);

	public void log(String msg) {		
		this.logger.error(msg);
	}

	@GET
	@Deferred
	@Path("/dashboard/cards")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getDashboardCards() {
		log("Enter getDashboardCards");

		try {
			List<Map<String, Object>> dashboard = getRoleService().getDashboardCards(getLoggedInUserName());
			
			return Response.ok(dashboard).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Error fetching roles").build();
		}
	}
	@GET
	@Deferred
	@Path("/roles")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getRoles(@QueryParam("start") @DefaultValue("0") int start,
			@QueryParam("limit") @DefaultValue("10") int limit,
			@QueryParam("sort") String sort,
			@QueryParam("dir") String dir,
			@QueryParam("query") String query,
			@QueryParam("filter") String filter) {
		log("Enter getRoles with start=" + start + ", limit=" + limit+
				", sort=" + sort + ", dir=" + dir+", query "+query);

		try {
			Map<String, Object> roles = getRoleService().getRoles(getLoggedInUserName(), start, limit, sort, dir, query,filter);
			
			return Response.ok(roles).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Error fetching roles").build();
		}
	}

	@GET
	@Deferred
	@Path("/role/{roleName}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getRoleDetails(@PathParam("roleName") String roleName) throws GeneralException, JSONException {
		Map<String, Object> roleDetails = new HashMap<>();
		roleDetails = getRoleService().getRole(roleName);
		return Response.ok(roleDetails).build();
	}

	@GET
	@Deferred
	@Path("/workgroup/{groupId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getGroupMembers(@PathParam("groupId") String groupId) throws GeneralException {
		Map<String, Object> workgroupMembers = getRoleService().getWorkGroupMembers(getLoggedInUserName(),groupId);
		return Response.ok(workgroupMembers).build();
	}
	
	@GET
	@Deferred
	@Path("/workgroup/{groupId}/roles")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getRolesOwnedByWorkgroup(@PathParam("groupId") String groupId) throws GeneralException {
		Map<String, Object> roles = getRoleService().getRolesOwnedByWorkgroup(groupId);
		return Response.ok(roles).build();
	}

	@GET
	@Deferred
	@Path("/workgroups")
	@Produces(MediaType.APPLICATION_JSON)
	public Response searchWorkgroups(@QueryParam("start") @DefaultValue("0") int start,
			@QueryParam("limit") @DefaultValue("25") int limit,
			@QueryParam("query") String query,
			@QueryParam("memberIds") String memberIds,
			@QueryParam("sort") @DefaultValue("name") String sort,
			@QueryParam("dir") @DefaultValue("ASC") String dir) throws GeneralException {
		boolean roleAdmin = getRoleService().isRoleAdmin();
		List<String> memberIdList = new ArrayList<>();
		if (memberIds != null && memberIds.trim().length() > 0) {
			String[] chunks = memberIds.split(",");
			for (String chunk : chunks) {
				if (chunk != null) {
					String id = chunk.trim();
					if (!id.isEmpty()) {
						memberIdList.add(id);
					}
				}
			}
		}
		Map<String, Object> data = getIdentityService()
				.searchWorkgroups(start, limit, query, sort, dir, roleAdmin, memberIdList);
		return Response.ok(data).build();
	}

	@GET
	@Deferred
	@Path("/workgroups/stats")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getWorkgroupDashboardStats() throws GeneralException {
		boolean roleAdmin = getRoleService().isRoleAdmin();
		Map<String, Object> stats = getIdentityService().getWorkgroupDashboardStats(roleAdmin);
		return Response.ok(stats).build();
	}

	@POST
	@Deferred
	@Path("/workgroups")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createWorkgroup(Map<String, Object> body) throws GeneralException {
		Map<String, Object> result = getIdentityService().createWorkgroup(body);
		if (Boolean.FALSE.equals(result.get("success"))) {
			return Response.status(Response.Status.BAD_REQUEST).entity(result).build();
		}
		return Response.ok(result).build();
	}

	@GET
	@Deferred
	@Path("/identities/suggest")
	@Produces(MediaType.APPLICATION_JSON)
	public Response suggestIdentities(@QueryParam("q") String q,
			@QueryParam("limit") @DefaultValue("25") int limit) throws GeneralException {
		List<Map<String, Object>> rows = getIdentityService().suggestIdentities(q, limit);
		return Response.ok(rows).build();
	}

	@GET
	@Deferred
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
	@Deferred
	@Produces(MediaType.APPLICATION_JSON)
	public Response getITRoleDetails(@PathParam("id") String id) {
		try {
			Map<String, Object> matchedRoles = getRoleService().getSingleITRoleDetails(id);
			return Response.ok(matchedRoles).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error fetching getITRoleDetails")
					.build();
		}
	}

	@Path("columns")
	@GET
	@Deferred
	@Produces(MediaType.APPLICATION_JSON)
	public Response getRoleTableColumns() {

		try {
			List<Map<String, Object>> columns = getRoleService().getRoleTableColumns();
			log("Column sending "+columns);
			return Response.ok(columns).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error fetching getRoleTableColumns")
					.build();
		}

	}

	@POST
	@Deferred
	@Path("/roles/bulk-action")
	public Response performBulkAction(Map<String, Object> requestData) {
		try {
			Map<String,Object> result = getRoleService().performBulkAction(getLoggedInUserName(),requestData);

			return Response.ok(result).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Bulk action failed: " + e.getMessage())
					.build();
		}
	}
	
	
	@Path("role/update")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateRoles(Map<String,Object> updatedRoleData) throws GeneralException, Exception {
		log("Enter updateRoles "+updatedRoleData);
	
	    Map<String,Object> result = getRoleService().updateRole(getLoggedInUserName(),updatedRoleData); // logic to generate CSS
	    return Response.ok(result).build();
	}
	
	@Path("roles/download")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces("application/csv")
	public Response downloadCsvFile(Map<String,Object> params) throws GeneralException, Exception {
		log("Enter downloadCsvFile "+params);
	
	    String csvContent = getRoleService().downloadRoles(getLoggedInUserName(),params); // logic to generate CSS
	    return Response.ok(csvContent).header("Content-Disposition", "attachment; filename=\"rolesExport.cvs\"").build();
	}
	
	@Path("config/timeout")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getTimeoutConfig() throws GeneralException {
	    Map<String, Object> result = getRoleService().getRoleDownloadTimeOut();	    
	    return Response.ok(result).build();
	}
	
	
	@Path("config")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getDebugConfig() throws GeneralException {
	    Map<String, Object> result = getRoleService().getConfig();	    
	    return Response.ok(result).build();
	}	


	@Override
	public String getPluginName() {
		return "rolemanagement";
	}

	private RoleService getRoleService() throws GeneralException {
		return new RoleService((PluginContext) this, getContext(), getLoggedInUser(),getSettingString(sailpoint.plugin.rolemanagement.util.Constants.CONFIG_OBJECT));
	}

	private IdentityService getIdentityService() throws GeneralException {
		return new IdentityService((PluginContext) this, getContext(), getLoggedInUser());
	}

}
