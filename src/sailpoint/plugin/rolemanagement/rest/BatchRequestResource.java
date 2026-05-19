package sailpoint.plugin.rolemanagement.rest;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


import sailpoint.plugin.PluginContext;
import sailpoint.plugin.rolemanagement.service.RmeBatchRequestService;
import sailpoint.rest.plugin.AllowAll;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.tools.GeneralException;

@AllowAll
@Path("rolemanagement")
public class BatchRequestResource extends BasePluginResource{
	
	public static final Log logger = LogFactory.getLog(BatchRequestResource.class);

	@Override
	public String getPluginName() {
		// TODO Auto-generated method stub
		return "rolemanagement";
	}
	
	public void log(String msg) {		
		this.logger.error(msg);
	}
	
	@Path("/batch/upload")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	
	public void batchUpload(Map<String,Object> payload) throws GeneralException, Exception {
		log("payload is "+payload);		
		/*
		 * config":{"roleType":"it","workItems":"No"},"fileName":"rolesExport (7).csv","fileContents":"in bytes"
		 */
		getBatchService().processBatch(payload);
	}
	
	@Path("/batch/load")
	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getExistingBatchRequests(@QueryParam("page") @DefaultValue("0") int pageIndex,
			@QueryParam("size") @DefaultValue("10") int pageSize) throws GeneralException, Exception {
				
		 Map<String, Object> returnMap = getBatchService().getBatchRequests(pageIndex, pageSize);
		 return Response.ok(returnMap).build();
	}
	
	@GET
	@Path("/batch/{batchId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response getBatchRequestItems(@PathParam("batchId") String batchId) throws Exception {
		Map<String, Object> returnMap = getBatchService().getBatchRequestItems(batchId);
		 return Response.ok(returnMap).build();
	}
	
	@GET
	@Path("/batch/config")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response getBulkRequestConfig() throws Exception {
		Map<String, Object> result = getBatchService().getConfig();	    
	    return Response.ok(result).build();
	}
	
	@GET
	@Path("/batch/downloadTemplate")
	@Produces(MediaType.APPLICATION_JSON)
	public Response downloadBatchTemplate(@QueryParam("roleType") @DefaultValue("it") String roleType) throws Exception {
		String normalizedRoleType = roleType != null ? roleType.trim().toLowerCase() : "it";
		if (!"it".equals(normalizedRoleType) && !"business".equals(normalizedRoleType)) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(error("roleType must be 'it' or 'business'"))
					.build();
		}
		Map<String, String> payload = getBatchService().downloadBatchRequestTemplate(normalizedRoleType);
		return Response.ok(payload).build();
	}
	
	/*
	 * payload {batchId: "", decision: ""}
	 */
	@POST
	@Path("/batch/approveOrReject")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response batchRequestApprovalDecision(Map<String,Object> payload) throws Exception {
		
		payload.put("approver", getLoggedInUser().getId());
		Map<String,Object> result = getBatchService().processBatchRequestApproval(payload);
		return Response.ok(result).build();
		
	}
	
	
	@GET
	@Path("/batch/downloadSummary/{batchId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response downloadBatchSummary(@PathParam("batchId") String batchId) throws Exception {
		String csvContent = getBatchService().downloadBatchSummary(batchId);
		return Response.ok(csvContent).header("Content-Disposition", "attachment; filename=\"roleBulkUploadSummary.csv\"").build();
	}

	//TOD: Implement get Role Differences API
	@GET
	@Path("/batch/differences")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response getRoleDifferences(@QueryParam("batchId") String batchId, @QueryParam("itemId") String itemId) throws Exception {
		Map<String, Object> result = getBatchService().getRoleDifferences(batchId,itemId);
		return Response.ok(result).build();
	}

	private RmeBatchRequestService getBatchService() throws GeneralException {
		return new RmeBatchRequestService((PluginContext) this, getContext(), getLoggedInUser(),getSettingString(sailpoint.plugin.rolemanagement.util.Constants.CONFIG_OBJECT));
	}
	
	private Map<String, String> error(String msg) {
        Map<String, String> m = new HashMap<>();
        m.put("message", msg);
        return m;
    }

	@DELETE
	@Path("/batch/{batchId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response deleteBatchRequest(@PathParam("batchId") String batchId) throws Exception {
		try {
			getBatchService().deleteBatchRequest(batchId);
			Map<String, String> result = new HashMap<>();
			result.put("status", "success");
			return Response.ok(result).build();
		} catch (Exception e) {
			logger.error("Failed to delete batch request " + batchId, e);
			return Response.serverError().entity(error(e.getMessage())).build();
		}
	}

	
	
}
