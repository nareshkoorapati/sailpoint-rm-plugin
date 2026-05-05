package sailpoint.plugin.rolemanagement.modal;

import java.util.Date;

public class ApprovalStep {

    private String stepId;
    private String requestId;
    private String stepName;
    private String approver;
    private String status; // Pending, Approved, Rejected
    private String comments;
    private Date decisionDate;

    public ApprovalStep() {}

    public ApprovalStep(String stepId, String requestId, String stepName, String approver,
                        String status, String comments, Date decisionDate) {
        this.stepId = stepId;
        this.requestId = requestId;
        this.stepName = stepName;
        this.approver = approver;
        this.status = status;
        this.comments = comments;
        this.decisionDate = decisionDate;
    }

    // Getters and Setters

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
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

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public Date getDecisionDate() {
        return decisionDate;
    }

    public void setDecisionDate(Date decisionDate) {
        this.decisionDate = decisionDate;
    }

    @Override
    public String toString() {
        return "ApprovalStep{" +
                "stepId='" + stepId + '\'' +
                ", requestId='" + requestId + '\'' +
                ", stepName='" + stepName + '\'' +
                ", approver='" + approver + '\'' +
                ", status='" + status + '\'' +
                ", decisionDate=" + decisionDate +
                '}';
    }
}

