package sailpoint.plugin.rolemanagement.service;

import java.util.List;
import java.util.UUID;

import sailpoint.object.Bundle;
import sailpoint.plugin.rolemanagement.modal.RoleChangeApproval;
import sailpoint.plugin.rolemanagement.modal.RoleChangeRequest;

public class RoleChangeService {

    public void createChangeRequest(Bundle original, Bundle updated, String requesterId) {
        RoleChangeRequest request = new RoleChangeRequest();
        request.setId(UUID.randomUUID().toString());
        request.setRoleId(original.getId());
        request.setRequesterId(requesterId);
        request.setStatus("PENDING");
        request.setCurrentStep(1);
        // Save to DB...

        storeSnapshots(request.getId(), original, updated);
        createApprovalSteps(request.getId(), getApprovers());
    }

    private List<String> getApprovers() {
		// TODO Auto-generated method stub
		return null;
	}

	private void storeSnapshots(String requestId, Bundle original, Bundle updated) {
        saveSnapshot(requestId, "OLD", serialize(original));
        saveSnapshot(requestId, "NEW", serialize(updated));
    }

    private void saveSnapshot(String requestId, String string, Object serialize) {
		// TODO Auto-generated method stub
		
	}

	private Object serialize(Bundle original) {
		// TODO Auto-generated method stub
		return null;
	}

	private void createApprovalSteps(String requestId, List<String> approvers) {
        int step = 1;
        for (String approverId : approvers) {
            RoleChangeApproval approval = new RoleChangeApproval();
            approval.setId(UUID.randomUUID().toString());
            approval.setRequestId(requestId);
            approval.setStepNumber(step++);
            approval.setApproverId(approverId);
            approval.setStatus("PENDING");
            // Save to DB...
        }
    }
}

