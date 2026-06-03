/**
 * 
 */
package sailpoint.plugin.rolemanagement.task;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Rule;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.plugin.rolemanagement.dao.RmeBatchRequestDao;
import sailpoint.plugin.rolemanagement.model.RmeBatchRequest;
import sailpoint.plugin.rolemanagement.model.RmeBatchRequestItem;
import sailpoint.plugin.rolemanagement.util.CsvParseUtil;
import sailpoint.task.BasePluginTaskExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.IOUtil;
import sailpoint.tools.RFC4180LineIterator;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 * @author Naresh
 *
 */
public class RMEBatchRequestProcessor extends BasePluginTaskExecutor{

	private static Log logger = LogFactory.getLog(RMEBatchRequestProcessor.class);

	Rule perItemRuleObj = null;
	SailPointContext _context = null;
	
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

	@Override
	public void execute(SailPointContext context, TaskSchedule taskSchedule, TaskResult taskResult, Attributes<String, Object> attrs) {
		logEntry("execute");
		String submissionStatus = "Submitted";
		String fileName = null;
		int recordCount = 0;
		int completedCount = 0;
		int errorCount = 0;
		int invalidCount = 0;
		boolean performImpactAnalysisOnly = false;
		try {
			logger.info("Enter RME Batch Request Task Processor:::execute()");

			this._context = context;
			
			taskResult.setAttributes(attrs);

			boolean approvalRequired = false;
			String perItemRuleName = (String)attrs.get("perItemRuleName");
			
			
			

			if (Util.isNullOrEmpty(perItemRuleName)) {
				throw new GeneralException("Per Item Rule Name is Empty");
			}

			this.perItemRuleObj = context.getObjectByName(Rule.class, perItemRuleName);
			if (this.perItemRuleObj == null) {
				throw new GeneralException("Per Item Rule "+perItemRuleName+" not found.");
			}

			String batchId = (String)attrs.get("batchId");
			
			
			RFC4180LineParser parser = null;
			String dlm = ",";
			
			Map<String,Object> config = (Map<String, Object>) attrs.get("config");
			logger.error("Existing config "+config);
			//config.put("isRoleAdmin", attrs.get("isRoleAdmin"));
			//config expects performImpactAnalysisOnly, isRoleAdmin and createRoleIfNotExist

			

			Connection conn = getConnection();

			try {

				RmeBatchRequestDao rmeBatchDao = new RmeBatchRequestDao(conn);
				RmeBatchRequest batchRequest;
				List<RmeBatchRequestItem> items;

				try {
					batchRequest = rmeBatchDao.findBatchRequest(batchId);
					items = rmeBatchDao.findItemsByBatchId(batchId);
				}
				catch(Exception e) {
					throw new GeneralException("Exception in getting batch request with id "+batchId+" Erros: "+e.getMessage());
				}
				
				if (batchRequest==null) {
					throw new GeneralException("couldn't find batch request with id "+batchId);
				}
				
				if (items == null || items.isEmpty()) {
					throw new GeneralException("No Role Batch Update recordss found with batch id "+batchId);
				}

				String header = CsvParseUtil.stripUtf8Bom(batchRequest.getHeader());
				dlm = CsvParseUtil.detectDelimiter(header);
				parser = new RFC4180LineParser(dlm);
				LinkedList<String> headerList = new LinkedList<String>();
				headerList.addAll(CsvParseUtil.normalizeHeaders(parser.parseLine(header)));
				logger.error("Normalized batch CSV headers: " + headerList);
				
				//get Config
				String runConfig = batchRequest.getRunConfig();
				logger.error("Run config "+runConfig);
				XMLObjectFactory factory = XMLObjectFactory.getInstance();
				
				Attributes<String,Object> configAttrs = (Attributes<String, Object>) factory.parseXml(context, runConfig, false);
				
				config.putAll(configAttrs);;
				config.put("performImpactAnalysisOnly", false);
				
				int count = 0;

				for(RmeBatchRequestItem item : items) {
					
					if (item.getRoleId() == null) continue;
					
					List<String> tokens = parser.parseLine(item.getRequestData().trim());

					HashMap<String, String> row = new HashMap<String, String>(
							CsvParseUtil.toRowMap(headerList, tokens));
						
					Map<String,Object> result = processRMEBatchRequest(row,config);
						
					String status = (String) result.get("status");
					String finalStatus = Util.isNullOrEmpty(status) ? "Unknown" : status;

					if ("failed".equalsIgnoreCase(status)) { errorCount++; finalStatus = "Failed"; }
					else if ("invalid".equalsIgnoreCase(status)) { invalidCount++; finalStatus = "Invalid"; }
					else if ("success".equalsIgnoreCase(status)) { completedCount++; finalStatus = "Success"; }
					else if ("skipped".equalsIgnoreCase(status)) { invalidCount++; finalStatus = "Skipped"; }
					else if ("pending".equalsIgnoreCase(status)) { finalStatus = "Pending"; }
					else if ("updated".equalsIgnoreCase(status)) { completedCount++; finalStatus = "Updated"; }
					else if ("created".equalsIgnoreCase(status)) { completedCount++; finalStatus = "Created"; }

					item.setRoleId((String)result.get("roleId"));
					item.setRoleName((String)result.get("roleName"));
					item.setStatus(finalStatus);
					item.setErrorMessage((String)result.get("errorMessage"));


					rmeBatchDao.updateBatchRequestItem(item);
					
					
					count++;
				}

				batchRequest.setCompletedDate(System.currentTimeMillis());
				batchRequest.setInvalidCount(invalidCount);
				batchRequest.setCompletedCount(completedCount);
				batchRequest.setErrorCount(errorCount);
				batchRequest.setStatus("Completed");
				rmeBatchDao.updateBatchRequest(batchRequest);
				
			} catch (SQLException e) {
				logger.error("Exception in executing Batch Request "+e);
				submissionStatus = "Failed";
				e.printStackTrace();
			}
			finally {
				IOUtil.closeQuietly(conn);
			}
		} catch (GeneralException e) {
			submissionStatus = "Failed";
			logger.error("Exception in executing Batch Request "+e);
			e.printStackTrace();
		} catch (Exception e) {
			submissionStatus = "Failed";
			logger.error("Exception in executing Batch Request "+e);
			e.printStackTrace();
		}
		
		//send Email notification
		try {
			String notificationRule = (String)attrs.get("notificationRule");
			if (Util.isNullOrEmpty(notificationRule)) {
				throw new GeneralException("Notification rule is required for sending notification.");
			}

			Rule  notificationRuleObj = context.getObjectByName(Rule.class, notificationRule);
			if (notificationRuleObj == null) {
				throw new GeneralException("Notification Rule "+notificationRule+" not found.");
			}
			
			Map<String,Object> params = new HashMap<String,Object>();
			params.put("status",submissionStatus);
			params.put("context",this._context);
			params.put("log",this.logger);
			params.put("fileName", fileName);
			params.put("performImpactAnalysisOnly", performImpactAnalysisOnly);
			params.put("totalRecordCount", recordCount);
			params.put("completedCount", completedCount);
			params.put("errorCount", errorCount);
			params.put("invalidCount", invalidCount);
			
			this._context.runRule(notificationRuleObj, params);
			
		}
		catch(Exception e) {
			logger.error("Exception in sending email notification"+e);
		}
		finally {
			logExit("execute");
		}
		
		
	}

	@Override
	public boolean terminate() {
		logEntry("terminate");
		try {
			// TODO Auto-generated method stub
			return true;
		} finally {
			logExit("terminate");
		}
	}

	protected Map<String,Object> processRMEBatchRequest(Map<String,String> row, Map<String,Object> config) throws GeneralException {
		logEntry("processRMEBatchRequest");
		try {
			Map<String,Object> params = new HashMap<String,Object>();
			params.put("roleMap",row);
			params.put("context",this._context);
			params.put("log",this.logger);
			params.put("config", config);

			@SuppressWarnings("unchecked")
			Map<String,Object> result = (Map<String, Object>) this._context.runRule(this.perItemRuleObj, params);	


			return result;
		} finally {
			logExit("processRMEBatchRequest");
		}
	}

	@Override
	public String getPluginName() {
		logEntry("getPluginName");
		try {
			// TODO Auto-generated method stub
			return "RoleManagement";
		} finally {
			logExit("getPluginName");
		}
	}

}
