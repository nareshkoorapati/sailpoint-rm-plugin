package sailpoint.plugin.rolemanagement.service;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.Util;
import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.object.Attributes;
import sailpoint.object.BundleDifference;
import sailpoint.object.Custom;
import sailpoint.object.Difference;
import sailpoint.object.Identity;
import sailpoint.plugin.PluginContext;
import sailpoint.plugin.rolemanagement.dao.RmeBatchRequestDao;
import sailpoint.plugin.rolemanagement.model.RmeBatchRequest;
import sailpoint.plugin.rolemanagement.model.RmeBatchRequestItem;
import sailpoint.plugin.rolemanagement.util.BulkUploadTemplateCsvBuilder;
import sailpoint.plugin.rolemanagement.util.RuleUtil;
import sailpoint.plugin.rolemanagement.util.Constants;
import sailpoint.tools.GeneralException;
import sailpoint.tools.IOUtil;
import sailpoint.tools.xml.XMLObjectFactory;

public class RmeBatchRequestService {
	public static final Log logger = LogFactory.getLog(RmeBatchRequestService.class);

	public void log(String message) {
		logEntry("log");
		try {
			this.logger.error(message);
		} finally {
			logExit("log");
		}
	}

	private PluginContext pluginContext;
	private SailPointContext _context;
	private Identity loggedInIdentity;
	private String customObjectName;

	private void logEntry(String methodName) {
		if (logger.isDebugEnabled()) {
			logger.debug("Entering " + methodName);
		}
	}

	private void logExit(String methodName) {
		if (logger.isDebugEnabled()) {
			logger.debug("Exiting " + methodName);
		}
	}


	public RmeBatchRequestService(PluginContext pluginContext, SailPointContext _context, Identity loggedInIdentity, String customObjectName) {
		super();
		logEntry("RmeBatchRequestService");
		try {
			this.pluginContext = pluginContext;
			this._context = _context;
			this.loggedInIdentity = loggedInIdentity;
			this.customObjectName = customObjectName;
		} finally {
			logExit("RmeBatchRequestService");
		}
	}

	public RmeBatchRequestDao getRmeBatchRequestDao(Connection conn) throws GeneralException {		
		logEntry("getRmeBatchRequestDao");
		try {
			RmeBatchRequestDao rmeBatchRequestDao = new RmeBatchRequestDao(conn);				
			return rmeBatchRequestDao;
		} finally {
			logExit("getRmeBatchRequestDao");
		}
	}

	public void processBatch(Map<String, Object> payload) throws SQLException, GeneralException, IOException {		
		logEntry("processBatch");
		try {
			//TaskDefinition td = _context.getObjectByName(TaskDefinition.class, );
			TaskManager tm = new TaskManager(_context);
			tm.setLauncher(this.loggedInIdentity.getName());
			tm.runSync("RME Batch Request", payload);
		} finally {
			logExit("processBatch");
		}

	}

	public Map<String, Object> getBatchRequests(int pageIndex, int pageSize)
	        throws GeneralException, SQLException, Exception {
		try {
			logEntry("getBatchRequests");
			try {
				Map<String,Object> returnMap = new HashMap<String,Object>();
				Connection conn = pluginContext.getConnection();
				try {
					Map<String, Object> userConfig = getConfig();
					boolean isRoleAdmin = userConfig.get("isRoleAdmin") != null ? (boolean) userConfig.get("isRoleAdmin") : false;
					int offset = pageIndex * pageSize;
					RmeBatchRequestDao dao = getRmeBatchRequestDao(conn);
					List<RmeBatchRequest> requests = null;
					int totalCount = 0;
					if (isRoleAdmin) {
						requests = dao.getBatchRequests(offset, pageSize);
						totalCount = dao.countBatchRequests();
					} else {
						requests = dao.getBatchRequestsForUser(offset, pageSize, this.loggedInIdentity.getName());
						totalCount = dao.countBatchRequestsForUser(this.loggedInIdentity.getName());
					}

					int totalPages = pageSize > 0
			                ? (int) Math.ceil(totalCount / (double) pageSize)
			                : 0;
					returnMap.put("count", totalCount);
					returnMap.put("totalPages", totalPages);
					if (requests != null) {
						
						List<Map<String,Object>> items = new ArrayList<Map<String,Object>>();
						for(RmeBatchRequest request : requests) {
							Map<String,Object> item = new HashMap<String,Object>();
							item.put("id", request.getId());
							item.put("fileName", request.getFileName());
							item.put("status", request.getStatus());
							item.put("owner", request.getOwner());
							item.put("submittedOn", request.getCreated() != null ? request.getCreated().toString() : "");
							item.put("recordCount", String.valueOf(request.getRecordCount()));
							item.put("completedDate", request.getCompletedDate() != null ? request.getCompletedDate().toString() : "");
							item.put("errorCount", String.valueOf(request.getErrorCount()));
							item.put("completedCount", String.valueOf(request.getCompletedCount()));
							item.put("invalidCount", String.valueOf(request.getInvalidCount()));
							item.put("approvalStatus", String.valueOf(request.getApprovalStatus()));
							item.put("header", String.valueOf(request.getHeader()));
							
							String config = request.getRunConfig();
							if (config != null) {
								XMLObjectFactory factory = XMLObjectFactory.getInstance();
								Attributes<String, Object> configAttrs = (Attributes<String, Object>) factory.parseXml(_context, config, false);
								item.put("config", configAttrs.getMap());
							}
							items.add(item);
						}
						returnMap.put("objects", items);
						
					}
					
				}
				catch (Exception e) {
					logger.error("Error in getBatchRequests: ", e);
					throw e;
				}
				finally {
					IOUtil.closeQuietly(conn);
				}

				return returnMap;
			}
			finally {
				logExit("getBatchRequests");
			}
		}
		catch (GeneralException | SQLException e) {
			logExit("getBatchRequests");
			throw e;
		}
	}

	public Map<String, Object> getBatchRequestItems(String batchId) throws Exception {
		try {
			logEntry("getBatchRequestItems");
			try {
				Map<String,Object> returnMap = new HashMap<String,Object>();
				Connection conn = pluginContext.getConnection();
				try {
					
					RmeBatchRequestDao dao = getRmeBatchRequestDao(conn);
					RmeBatchRequest batchRequest = dao.findBatchRequest(batchId);
					List<RmeBatchRequestItem> requests = dao.findItemsByBatchId(batchId);

					
					if (requests != null) {
						
						List<Map<String,String>> items = new ArrayList<Map<String,String>>();
						for(RmeBatchRequestItem request : requests) {
							Map<String,String> item = new HashMap<String,String>();
							item.put("id", request.getId());
							item.put("roleName", request.getRoleName());
							item.put("roleId", request.getRoleId());
							item.put("errorMessage", request.getErrorMessage());
							item.put("record", request.getRequestData());
							item.put("status", request.getStatus());							
							items.add(item);
						}
						returnMap.put("objects", items);
						returnMap.put("count", items.size());
						returnMap.put("header", batchRequest.getHeader());
						
					}
					
				}
				finally {
					IOUtil.closeQuietly(conn);
				}

				return returnMap;
			}
			finally {
				logExit("getBatchRequests");
			}
		}
		catch (GeneralException | SQLException e) {
			logExit("getBatchRequestItems");
			throw e;
		}
	}
	
	public Map<String, String> downloadBatchRequestTemplate(String roleType, String templateType) throws Exception {
		log("Enter downloadBatchRequestTemplate roleType=" + roleType + " templateType=" + templateType);

		try {
			String normalizedTemplateType = normalizeTemplateType(templateType);

			Custom customObj = _context.getObjectByName(Custom.class, customObjectName);
			String ruleName = (String) customObj.get(Constants.DOWNLOAD_BULK_UPLOAD_TEMPLATE);
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("roleType", roleType);
			params.put("templateType", normalizedTemplateType);
			if (loggedInIdentity != null && Util.isNotNullOrEmpty(loggedInIdentity.getName())) {
				params.put("loggedInUser", loggedInIdentity.getName());
			}
			Object ruleResult = RuleUtil.runIIQRule(_context, ruleName, params);

			byte[] csvBytes = null;
			if (ruleResult instanceof Map) {
				csvBytes = BulkUploadTemplateCsvBuilder.build((Map<String, Object>) ruleResult);
			} else if (ruleResult instanceof String) {
				String csvText = (String) ruleResult;
				csvBytes = csvText.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			} else if (ruleResult instanceof byte[]) {
				csvBytes = (byte[]) ruleResult;
			}

			if (csvBytes == null || csvBytes.length == 0) {
				throw new GeneralException("Bulk upload template rule returned empty content. Type: "
						+ (ruleResult != null ? ruleResult.getClass().getName() : "null"));
			}

			String fileName = resolveTemplateFileName(roleType, normalizedTemplateType);

			Map<String, String> result = new HashMap<String, String>();
			result.put("fileName", fileName);
			result.put("content", java.util.Base64.getEncoder().encodeToString(csvBytes));
			return result;
		} catch (Exception e) {
			log("Error in downloadBatchRequestTemplate " + e);
			throw e;
		}
	}

	private static String normalizeTemplateType(String templateType) throws GeneralException {
		String normalized = templateType != null ? templateType.trim() : "bulk";
		if (Util.isNullOrEmpty(normalized)) {
			normalized = "bulk";
		}
		if ("bulk".equalsIgnoreCase(normalized) || "upload".equalsIgnoreCase(normalized)) {
			return "bulk";
		}
		if ("attributeDefinition".equalsIgnoreCase(normalized)
				|| "attribute_definition".equalsIgnoreCase(normalized)
				|| "attributes".equalsIgnoreCase(normalized)) {
			return "attributeDefinition";
		}
		throw new GeneralException("templateType must be 'bulk' or 'attributeDefinition'");
	}

	private static String resolveTemplateFileName(String roleType, String templateType) {
		boolean business = "business".equalsIgnoreCase(roleType);
		if ("attributeDefinition".equals(templateType)) {
			return business
					? "Business_Role_Attribute_Definitions.csv"
					: "IT_Role_Attribute_Definitions.csv";
		}
		return business
				? "Business_Role_BulkUpload_Template.csv"
				: "IT_Role_BulkUpload_Template.csv";
	}

	public Map<String, Object> getConfig() throws Exception {
		
		log("Enter getConfig");
		
		String ruleName = null;
		Map<String,Object> result = null;
		
		try {
			Custom customObj = _context.getObjectByName(Custom.class, customObjectName);			
			
			ruleName = (String) customObj.get(Constants.BULK_REQUEST_CONFIG_RULE);
			Map<String,Object> params = new HashMap<String,Object>();
			params.put("loggedInUser", loggedInIdentity.getName());
			result =  (Map<String, Object>) RuleUtil.runIIQRule(_context, ruleName, params);
		}
		catch(Exception e) {
			log("Error in getConfig "+e);
			throw e;
		}
		log("Exit getConfig");
		return result;
	}
	
	public void runBatchUpdate(Map<String, Object> payload) throws SQLException, GeneralException, IOException {		
		logEntry("runBatchUpdate");
		try {
			TaskManager tm = new TaskManager(_context);
			tm.setLauncher(this.loggedInIdentity.getName());
			tm.runSync("RME Batch Request Processing Task", payload);
		} finally {
			logExit("runBatchUpdate");
		}

	}
	
	/*
	 * payload {batchId: "", decision: ""}
	 */

	public Map<String,Object> processBatchRequestApproval(Map<String, Object> payload) throws GeneralException, IOException {
		log("Enter processBatchRequestApproval");
		Map<String,Object> returnMap = new HashMap<String,Object>();
		
		String batchId = (String) payload.get("batchId");
		log("processBatchRequestApproval: batchId-"+batchId);
		
		String decision = (String)payload.get("decision");
		log("processBatchRequestApproval: decision-"+decision);
		
		String approvedBy = (String)payload.get("approver");
		
		try {
			
			Connection conn = pluginContext.getConnection();
			
			try {				
				RmeBatchRequestDao dao = getRmeBatchRequestDao(conn);
				dao.updateBatchRequestDecision(batchId,decision,approvedBy);
			}
			catch(SQLException e) {
				returnMap.put("status", "Failed");
				returnMap.put("error",e.getMessage());
			}
			finally {
				IOUtil.closeQuietly(conn);
			}
			
			if ("Approved".equalsIgnoreCase(decision)) {				
				runBatchUpdate(payload);
			}

		} catch (SQLException e) {
			returnMap.put("status", "Failed");
			returnMap.put("error",e.getMessage());
		}
		finally {
			logExit("getBatchRequests");
			returnMap.put("status", "success");
		}
		
		
		log("Exit processBatchRequestApproval");
		return returnMap;
	}

	public String downloadBatchSummary(String batchId) throws Exception {
		
		
		logEntry("downloadBatchSummary");
		String csvContent = "";
		try {
			Connection conn = pluginContext.getConnection();
			try {
				
				RmeBatchRequestDao dao = getRmeBatchRequestDao(conn);
				RmeBatchRequest batchRequest = dao.findBatchRequest(batchId);
				List<RmeBatchRequestItem> requests = dao.findItemsByBatchId(batchId);

				
				if (requests != null) {
					
					Custom customObj = _context.getObjectByName(Custom.class, customObjectName);			
				
					String ruleName = (String) customObj.get(Constants.DOWNLOAD_BATCH_REQUEST_SUMMARY);
					for(RmeBatchRequestItem request : requests) {
						Map<String,Object> params = new HashMap<String,Object>();
						params.put("roleId", request.getId());
						params.put("roleName", request.getRoleName());
						params.put("header", batchRequest.getHeader());
						params.put("errorMessage", request.getErrorMessage());
						params.put("record", request.getRequestData());
						params.put("status", request.getStatus());
						params.put("differences", request.getDifferences());
						csvContent += (String) RuleUtil.runIIQRule(_context, ruleName, params);
						
						
					}
					
				}
				
			}
			finally {
				IOUtil.closeQuietly(conn);
			}

		}
		finally {
			logExit("downloadBatchSummary");
		}
		return csvContent;
		
	}

	public Map<String, Object> getRoleDifferences(String batchId, String itemId) throws GeneralException {
		log("Enter getRoleDifferences");
		Map<String,Object> returnMap = new HashMap<String,Object>();
		
		
		
		try {
			
			Connection conn = pluginContext.getConnection();
			String differences = null;
			
			try {				
				RmeBatchRequestDao dao = getRmeBatchRequestDao(conn);
				differences = dao.getRoleDifferences(itemId);
			}
			catch(SQLException e) {
				returnMap.put("status", "Failed");
				returnMap.put("error",e.getMessage());
			}
			finally {
				IOUtil.closeQuietly(conn);
			}
			
			if (Util.isNotNullOrEmpty(differences)) {
				XMLObjectFactory factory = XMLObjectFactory.getInstance();
				BundleDifference changes = (BundleDifference) factory.parseXml(_context, differences, false);
				List<Map<String,Object>> list = new ArrayList<>();
			    
			    for(Difference diff : changes.getAttributeDifferences()) {
			    	Map<String,Object> m  = new HashMap<String,Object>();
				      m.put("attribute",diff.getAttribute());
				      m.put("oldValue",diff.getOldValue());
				      m.put("newValue",diff.getNewValue());
				      list.add(m);
			    }
			    returnMap.put("objects", list);
			}
			

		}
		finally {
			logExit("getRoleDifferences");
			returnMap.put("status", "success");
		}
		
		
		log("Exit getRoleDifferences");
		return returnMap;
	}



	public void deleteBatchRequest(String batchId) throws GeneralException {
		logEntry("deleteBatchRequest");
		Connection conn = null;
		try {
			conn = pluginContext.getConnection();
			RmeBatchRequestDao dao = getRmeBatchRequestDao(conn);
			dao.deleteBatchRequest(batchId);
		} catch (SQLException e) {
			throw new GeneralException("Failed to delete batch request", e);
		} finally {
			IOUtil.closeQuietly(conn);
			logExit("deleteBatchRequest");
		}
	}

}
