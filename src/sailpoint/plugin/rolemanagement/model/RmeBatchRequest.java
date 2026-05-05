// RmeBatchRequest.java
package sailpoint.plugin.rolemanagement.model;

import com.fasterxml.jackson.databind.JsonNode;

public class RmeBatchRequest {

    private String id;
    private Long created;
    private Long modified;
    private String assignedScopePath;
    private String fileName;
    private String header;
    private Long runDate;
    private Long completedDate;
    private Integer recordCount;
    private Integer completedCount;
    private Integer errorCount;
    private Integer invalidCount;
    private String message;
    private String errorMessage;
    private String fileContents;   // raw CSV or whatever
    private String status;
    private String runConfig;    // JSON representation
    private String owner;
    private String assignedScope;
    private String approvalStatus; //allowed values Skipped, Pending, Approved
    private String approvedBy;

    // getters + setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getCreated() { return created; }
    public void setCreated(Long created) { this.created = created; }

    public Long getModified() { return modified; }
    public void setModified(Long modified) { this.modified = modified; }

    public String getAssignedScopePath() { return assignedScopePath; }
    public void setAssignedScopePath(String assignedScopePath) { this.assignedScopePath = assignedScopePath; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getHeader() { return header; }
    public void setHeader(String header) { this.header = header; }

    public Long getRunDate() { return runDate; }
    public void setRunDate(Long runDate) { this.runDate = runDate; }

    public Long getCompletedDate() { return completedDate; }
    public void setCompletedDate(Long completedDate) { this.completedDate = completedDate; }

    public Integer getRecordCount() { return recordCount; }
    public void setRecordCount(Integer recordCount) { this.recordCount = recordCount; }

    public Integer getCompletedCount() { return completedCount; }
    public void setCompletedCount(Integer completedCount) { this.completedCount = completedCount; }

    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }

    public Integer getInvalidCount() { return invalidCount; }
    public void setInvalidCount(Integer invalidCount) { this.invalidCount = invalidCount; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getFileContents() { return fileContents; }
    public void setFileContents(String fileContents) { this.fileContents = fileContents; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRunConfig() { return runConfig; }
    public void setRunConfig(String runConfig) { this.runConfig = runConfig; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getAssignedScope() { return assignedScope; }
    public void setAssignedScope(String assignedScope) { this.assignedScope = assignedScope; }
    
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
	public String getApprovedBy() {
		return approvedBy;
	}
	public void setApprovedBy(String approvedBy) {
		this.approvedBy = approvedBy;
	}
}
