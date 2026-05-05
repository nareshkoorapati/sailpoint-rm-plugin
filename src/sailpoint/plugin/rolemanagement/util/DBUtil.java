package sailpoint.plugin.rolemanagement.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import sailpoint.plugin.rolemanagement.modal.RoleChangeApproval;
import sailpoint.plugin.rolemanagement.modal.RoleChangeDetail;
import sailpoint.plugin.rolemanagement.modal.RoleChangeRequest;

public class DBUtil {

    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/sailpoint";
    private static final String JDBC_USER = "root";
    private static final String JDBC_PASS = "password";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
    }

    public static void saveRequestMetadata(String requestId, String currentRoleJson, String updatedRoleJson) throws SQLException {
        String sql = "INSERT INTO role_approval_metadata (request_id, current_role_json, updated_role_json) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            ps.setString(2, currentRoleJson);
            ps.setString(3, updatedRoleJson);
            ps.executeUpdate();
        }
    }

    public static void saveApprovalStep(String requestId, String approverId, String status, String comments, int stepOrder) throws SQLException {
        String sql = "INSERT INTO approval_steps (request_id, approver_id, status, comments, step_order) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            ps.setString(2, approverId);
            ps.setString(3, status);
            ps.setString(4, comments);
            ps.setInt(5, stepOrder);
            ps.executeUpdate();
        }
    }

    public static List<String> getApproversForRequest(String requestId) throws SQLException {
        List<String> approvers = new ArrayList<>();
        String sql = "SELECT approver_id FROM approval_steps WHERE request_id = ? ORDER BY step_order";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    approvers.add(rs.getString("approver_id"));
                }
            }
        }
        return approvers;
    }

    public static String getCurrentStepStatus(String requestId) throws SQLException {
        String sql = "SELECT status FROM approval_steps WHERE request_id = ? ORDER BY step_order LIMIT 1";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("status");
                }
            }
        }
        return null;
    }

    public static String getRoleMetadata(String requestId, boolean isUpdated) throws SQLException {
        String sql = "SELECT " + (isUpdated ? "updated_role_json" : "current_role_json") + " FROM role_approval_metadata WHERE request_id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }

    public static void updateStepStatus(String requestId, String approverId, String status, String comments) throws SQLException {
        String sql = "UPDATE approval_steps SET status = ?, comments = ? WHERE request_id = ? AND approver_id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, comments);
            ps.setString(3, requestId);
            ps.setString(4, approverId);
            ps.executeUpdate();
        }
    }

	public static RoleChangeRequest getRequestById(String requestId) {
		// TODO Auto-generated method stub
		return null;
	}

	public static List<RoleChangeDetail> getChangeDetails(String requestId) {
		// TODO Auto-generated method stub
		return null;
	}

	public static RoleChangeApproval getCurrentPendingApproval(String requestId) {
		// TODO Auto-generated method stub
		return null;
	}

	public static void advanceApprovalStep(String requestId) {
		// TODO Auto-generated method stub
		
	}

	public static void markRequestRejected(String requestId) {
		// TODO Auto-generated method stub
		
	}

	public static void updateApprovalStatus(Object id, String string) {
		// TODO Auto-generated method stub
		
	}
}

