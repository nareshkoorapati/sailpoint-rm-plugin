package sailpoint.plugin.rolemanagement.modal;

import java.util.Date;
import java.util.List;

public class RoleChangeRequest {

    private String requestId;
    private String roleId;
    private String requester;
    private String status;
    private Date createdDate;
    private int currentStep;
    private String originalMetadataJson;
    private String updatedMetadataJson;

    // Optional for UI use: list of approval steps
    private List<ApprovalStep> approvalSteps;

    public RoleChangeRequest() {}

    public RoleChangeRequest(String requestId, String roleId, String requester, String status,
                             Date createdDate, int currentStep,
                             String originalMetadataJson, String updatedMetadataJson) {
        this.requestId = requestId;
        this.roleId = roleId;
        this.requester = requester;
        this.status = status;
        this.createdDate = createdDate;
        this.currentStep = currentStep;
        this.originalMetadataJson = originalMetadataJson;
        this.updatedMetadataJson = updatedMetadataJson;
    }

    // Getters and Setters
    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getRequester() {
        return requester;
    }

    public void setRequester(String requester) {
        this.requester = requester;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(int i) {
        this.currentStep = i;
    }

    public String getOriginalMetadataJson() {
        return originalMetadataJson;
    }

    public void setOriginalMetadataJson(String originalMetadataJson) {
        this.originalMetadataJson = originalMetadataJson;
    }

    public String getUpdatedMetadataJson() {
        return updatedMetadataJson;
    }

    public void setUpdatedMetadataJson(String updatedMetadataJson) {
        this.updatedMetadataJson = updatedMetadataJson;
    }

    public List<ApprovalStep> getApprovalSteps() {
        return approvalSteps;
    }

    public void setApprovalSteps(List<ApprovalStep> approvalSteps) {
        this.approvalSteps = approvalSteps;
    }

    @Override
    public String toString() {
        return "RoleChangeRequest{" +
                "requestId='" + requestId + '\'' +
                ", roleId='" + roleId + '\'' +
                ", requester='" + requester + '\'' +
                ", status='" + status + '\'' +
                ", createdDate=" + createdDate +
                ", currentStep='" + currentStep + '\'' +
                '}';
    }

	public void setId(String string) {
		// TODO Auto-generated method stub
		
	}

	public void setRequesterId(String requesterId) {
		// TODO Auto-generated method stub
		
	}

	public String getId() {
		// TODO Auto-generated method stub
		return null;
	}
}

