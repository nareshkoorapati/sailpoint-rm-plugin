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

import sailpoint.task.BasePluginTaskExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.IOUtil;
import sailpoint.tools.Message;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 * @author Naresh
 *
 */
public class RMEBatchRequestExecutor extends BasePluginTaskExecutor{

	private static Log logger = LogFactory.getLog(RMEBatchRequestExecutor.class);

	Rule perItemRuleObj = null;
	SailPointContext _context = null;
	
	private void logEntry(String methodName) {
		if (logger.isErrorEnabled()) {
			logger.error("Entering " + methodName);
		}
	}

	private void logExit(String methodName) {
		if (logger.isErrorEnabled()) {
			logger.error("Exiting " + methodName);
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
		boolean performImpactAnalysisOnly = true;
		try {
			logger.info("Enter RME Batch Request Task Executor:::execute()");

			this._context = context;
			
			taskResult.setAttributes(attrs);

			//boolean approvalRequired = true;
			String perItemRuleName = (String)attrs.get("perItemRuleName");
//			String approvalRequiredRule = (String)attrs.get("approvalRequiredRule");
//			
//			if (Util.isNotNullOrEmpty(approvalRequiredRule)) {
//				Rule approvalRequiredRuleObj = context.getObjectByName(Rule.class, approvalRequiredRule);
//				Map<String,Object> params = new HashMap<String,Object>();
//				params.put("context",this._context);
//				params.put("log",this.logger);
//				params.put("identityName",taskResult.getLauncher());
//				approvalRequired = (boolean)this._context.runRule(approvalRequiredRuleObj, params);				
//			}

			if (Util.isNullOrEmpty(perItemRuleName)) {
				throw new GeneralException("Per Item Rule Name is Empty");
			}

			this.perItemRuleObj = context.getObjectByName(Rule.class, perItemRuleName);
			if (this.perItemRuleObj == null) {
				throw new GeneralException("Per Item Rule "+perItemRuleName+" not found.");
			}

			Map<String,Object> config = (Map<String, Object>) attrs.get("config");
			logger.error("original config "+config);
			performImpactAnalysisOnly = Util.otob(config.get("performImpactAnalysisOnly"));
			config.put("isRoleAdmin", attrs.get("isRoleAdmin"));
			
			
			fileName = (String)attrs.get("fileName");
			taskResult.addMessage(new Message(Message.Type.Info, "File Name is "+fileName));
			byte[] fileBytes = java.util.Base64.getDecoder()
					.decode((String) attrs.get("fileContents"));

			logger.info("payload config "+attrs.get("config"));

			String csvText = new String(fileBytes, java.nio.charset.StandardCharsets.UTF_8);
			logger.info("payload csvText "+csvText);

			List<String> lines = new ArrayList<String>();
			StringBuilder sb = new StringBuilder();
			String newLine = System.lineSeparator();
			for (String l : csvText.split("\\r?\\n")) {
			    if (l != null && !l.trim().isEmpty()) {
			    	l = l.trim();
			        lines.add(l);
			        sb.append(l);
			        sb.append(newLine);
			        logger.error(l);
			    }
			}

			logger.error("payload lines "+lines);
			
			String dlm = ",";
			
			RFC4180LineParser parser = new RFC4180LineParser(dlm);
			XMLObjectFactory factory = XMLObjectFactory.getInstance();
			Attributes<String,Object> configAttr = new Attributes<String,Object>();
			configAttr.setMap(config);

			String header = lines.get(0);

			recordCount = lines.size()-1;
			completedCount = 0;
			errorCount = 0;
			invalidCount = 0;

			String batchId = Util.uuid();
			RmeBatchRequest batch = new RmeBatchRequest();
			batch.setId(batchId);
			batch.setCreated(System.currentTimeMillis());
			batch.setStatus("Submitted");
			batch.setFileContents(sb.toString());
			batch.setFileName(fileName);
			batch.setHeader(header);
			batch.setRunConfig(factory.toXml(configAttr, false));
			batch.setRecordCount(recordCount);
			batch.setOwner(taskResult.getLauncher());
			batch.setRunDate(System.currentTimeMillis());
			if (performImpactAnalysisOnly) {
				batch.setApprovalStatus("Pending");
			}
			else {
				batch.setApprovalStatus("Skipped");
			}

			Connection conn = getConnection();

			try {

				RmeBatchRequestDao rmeBatchDao = new RmeBatchRequestDao(conn);

				try {
					rmeBatchDao.insertBatchRequest(batch);
				}
				catch(Exception e) {
					throw new GeneralException("Exception in inserting batch request "+e.getMessage());
				}


				int count = 0;
				
				LinkedList<String> headerList = new LinkedList<String>();

				for(String line : lines) {
					
					List<String> tokens = parser.parseLine(line);
					if (count == 0) {
						headerList.addAll(tokens);
					}
					else {
						HashMap<String,String> row = new HashMap<String,String>();
						for(int i=0; i < tokens.size(); i++) {
							String headerString  = headerList.get(i);
							String valueString = tokens.get(i);
							row.put(headerString, valueString);
						}
						RmeBatchRequestItem item = new RmeBatchRequestItem();
						item.setId(Util.uuid());
						item.setCreated(System.currentTimeMillis());
						item.setRequestData(line);
						item.setBatchRequestId(batchId);
						item.setIdx(count);
						
						rmeBatchDao.insertBatchRequestItem(item);
						Map<String,Object> result = processRMEBatchRequest(row,config);
						logger.error("rule result "+result);
						
						//1. Failed - Failed to update/create role
						//2. Updated - Updated 
						//3. Created - If role created
						//4. Pending - 
						//5. Skipped - If no change
						//6. Invalid - If any information is missing or not valid
						//7. Success - Validation Success
						
						String status = (String) result.get("status");
						String finalStatus = Util.isNullOrEmpty(status) ? "Unknown" : status;

						if ("failed".equalsIgnoreCase(status)) { errorCount++; finalStatus = "Failed"; }
						else if ("invalid".equalsIgnoreCase(status)) { invalidCount++; finalStatus = "Invalid"; }
						else if ("success".equalsIgnoreCase(status)) { completedCount++; finalStatus = "Success"; }
						else if ("skipped".equalsIgnoreCase(status)) { finalStatus = "Skipped"; }
						else if ("pending".equalsIgnoreCase(status)) { finalStatus = "Pending"; }
						else if ("updated".equalsIgnoreCase(status)) { finalStatus = "Updated"; }
						else if ("created".equalsIgnoreCase(status)) { finalStatus = "Created"; }


						item.setRoleId((String)result.get("roleId"));
						item.setRoleName((String)result.get("roleName"));
						item.setStatus(finalStatus);
						item.setErrorMessage((String)result.get("errorMessage"));
						
						item.setDifferences((String)result.get("changes"));

						rmeBatchDao.updateBatchRequestItem(item);
					}
					
					count++;
				}

				batch.setCompletedDate(System.currentTimeMillis());
				batch.setInvalidCount(invalidCount);
				batch.setCompletedCount(completedCount);
				batch.setErrorCount(errorCount);
				if (performImpactAnalysisOnly) {
					batch.setStatus("Submitted");
				}
				else {
					batch.setStatus("Processed");
				}
				
				rmeBatchDao.updateBatchRequest(batch);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				submissionStatus = "Failed";
				logger.error("Exception in executing Batch Request "+e);
				e.printStackTrace();
			}
			finally {
				IOUtil.closeQuietly(conn);
			}
		} catch (GeneralException e) {
			submissionStatus = "Failed";
			logger.error("Exception in executing Batch Request "+e);
			e.printStackTrace();
		}  catch (Exception e) {
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
			params.put("submmittedBy", taskResult.getLauncher());
			
			this._context.runRule(notificationRuleObj, params);
			
		}
		catch(Exception e) {
			logger.error("Exception in sending email notification"+e);
			taskResult.addMessage(new Message(Message.Type.Error,"Error is "+e));
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

			Map<String,Object> result = (Map<String, Object>) this._context.runRule(this.perItemRuleObj, params);	

			logger.error("processRMEBatchRequest resutl "+result);
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
