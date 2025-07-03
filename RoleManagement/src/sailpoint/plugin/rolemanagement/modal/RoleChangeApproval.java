package sailpoint.plugin.rolemanagement.modal;


import java.util.Date;

public class RoleChangeApproval {

    private String approvalId;
    private String requestId;
    private String approver;
    private String status; // Pending, Approved, Rejected
    private String stepName;
    private Date decisionDate;
    private String comments;

    public RoleChangeApproval() {}

    public RoleChangeApproval(String approvalId, String requestId, String approver,
                               String status, String stepName, Date decisionDate, String comments) {
        this.approvalId = approvalId;
        this.requestId = requestId;
        this.approver = approver;
        this.status = status;
        this.stepName = stepName;
        this.decisionDate = decisionDate;
        this.comments = comments;
    }

    // Getters and Setters

    public String getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(String approvalId) {
        this.approvalId = approvalId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getApprover() {
        return approver;
    }

    public void setApprover(String approver) {
        this.approver = approver;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public Date getDecisionDate() {
        return decisionDate;
    }

    public void setDecisionDate(Date decisionDate) {
        this.decisionDate = decisionDate;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    @Override
    public String toString() {
        return "RoleChangeApproval{" +
                "approvalId='" + approvalId + '\'' +
                ", requestId='" + requestId + '\'' +
                ", approver='" + approver + '\'' +
                ", status='" + status + '\'' +
                ", stepName='" + stepName + '\'' +
                ", decisionDate=" + decisionDate +
                ", comments='" + comments + '\'' +
                '}';
    }

	public Object getId() {
		// TODO Auto-generated method stub
		return null;
	}

	public void setId(String string) {
		// TODO Auto-generated method stub
		
	}

	public void setStepNumber(int i) {
		// TODO Auto-generated method stub
		
	}

	public void setApproverId(String approverId) {
		// TODO Auto-generated method stub
		
	}
}

