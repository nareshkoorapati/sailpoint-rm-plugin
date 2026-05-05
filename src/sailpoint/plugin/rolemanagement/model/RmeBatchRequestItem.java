// RmeBatchRequestItem.java
package sailpoint.plugin.rolemanagement.model;

import com.fasterxml.jackson.databind.JsonNode;

public class RmeBatchRequestItem {

    private String id;
    private Long created;
    private Long modified;
    private String assignedScopePath;
    private String requestData;  // JSON for the row
    private String status;
    private String message;
    private String errorMessage;
    private String roleId;
    private String roleName;
    private String batchRequestId;
    private String owner;
    private String ownerId;
    private Integer idx;
    private String differences;

    // getters + setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getCreated() { return created; }
    public void setCreated(Long created) { this.created = created; }

    public Long getModified() { return modified; }
    public void setModified(Long modified) { this.modified = modified; }

    public String getAssignedScopePath() { return assignedScopePath; }
    public void setAssignedScopePath(String assignedScopePath) { this.assignedScopePath = assignedScopePath; }

    public String getRequestData() { return requestData; }
    public void setRequestData(String requestData) { this.requestData = requestData; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }


    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getBatchRequestId() { return batchRequestId; }
    public void setBatchRequestId(String batchRequestId) { this.batchRequestId = batchRequestId; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public Integer getIdx() { return idx; }
    public void setIdx(Integer idx) { this.idx = idx; }
	public String getDifferences() {
		return differences;
	}
	public void setDifferences(String differences) {
		this.differences = differences;
	}
}
