package sailpoint.plugin.rolemanagement.modal;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;

import sailpoint.plugin.rolemanagement.util.DBUtil;

import java.io.Serializable;
import java.util.List;

@ManagedBean(name = "approvalBean")
@SessionScoped
public class ApprovalBean implements Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String requestId;
    private RoleChangeRequest currentRequest;
    private List<RoleChangeDetail> changeDetails;
    private RoleChangeApproval currentApprovalStep;

    public String loadRequest(String requestId) {
        this.requestId = requestId;
        this.currentRequest = DBUtil.getRequestById(requestId);
        this.changeDetails = DBUtil.getChangeDetails(requestId);
        this.currentApprovalStep = DBUtil.getCurrentPendingApproval(requestId);
        return "viewChangeRequest.xhtml";
    }

    public void approve() {
        if (currentApprovalStep != null) {
            DBUtil.updateApprovalStatus(currentApprovalStep.getId(), "APPROVED");
            DBUtil.advanceApprovalStep(requestId);
        }
        this.loadRequest(requestId); // Refresh
    }

    public void reject() {
        if (currentApprovalStep != null) {
            DBUtil.updateApprovalStatus(currentApprovalStep.getId(), "REJECTED");
            DBUtil.markRequestRejected(requestId);
        }
        this.loadRequest(requestId); // Refresh
    }

    public RoleChangeRequest getCurrentRequest() { return currentRequest; }
    public List<RoleChangeDetail> getChangeDetails() { return changeDetails; }
    public RoleChangeApproval getCurrentApprovalStep() { return currentApprovalStep; }
}
