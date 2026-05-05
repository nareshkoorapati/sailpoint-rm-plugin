// RmeBatchRequestDao.java
package sailpoint.plugin.rolemanagement.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import sailpoint.plugin.rolemanagement.model.RmeBatchRequest;
import sailpoint.plugin.rolemanagement.model.RmeBatchRequestItem;
import sailpoint.tools.IOUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RmeBatchRequestDao {

    private final Connection conn;             // provided by plugin
    private final ObjectMapper mapper = new ObjectMapper();

    public RmeBatchRequestDao(Connection conn) {
        this.conn = conn;
    }

    /* ======================= BATCH REQUEST ======================= */

    public void insertBatchRequest(RmeBatchRequest r) throws SQLException {
        String sql = "INSERT INTO rme_batch_request " +
                "(id, created, modified, assigned_scope_path, file_name, header, run_date, " +
                " completed_date, record_count, completed_count, error_count, invalid_count, " +
                " message, error_message, file_contents, status, run_config, owner, assigned_scope,approval_status) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        PreparedStatement ps = null;
        try {
        	ps = conn.prepareStatement(sql);
            ps.setString(1, r.getId());
            setLong(ps, 2, r.getCreated());
            setLong(ps, 3, r.getModified());
            ps.setString(4, r.getAssignedScopePath());
            ps.setString(5, r.getFileName());
            ps.setString(6, r.getHeader());
            setLong(ps, 7, r.getRunDate());
            setLong(ps, 8, r.getCompletedDate());
            setInt(ps, 9, r.getRecordCount());
            setInt(ps, 10, r.getCompletedCount());
            setInt(ps, 11, r.getErrorCount());
            setInt(ps, 12, r.getInvalidCount());
            ps.setString(13, r.getMessage());
            ps.setString(14, r.getErrorMessage());
            ps.setString(15, r.getFileContents());
            ps.setString(16, r.getStatus());
            ps.setString(17, r.getRunConfig());
            ps.setString(18, r.getOwner());
            ps.setString(19, r.getAssignedScope());
            ps.setString(20, r.getApprovalStatus());
            ps.executeUpdate();
        }
        finally {
        	 IOUtil.closeQuietly(ps);
        }
    }

    public void updateBatchRequest(RmeBatchRequest r) throws SQLException {
        String sql = "UPDATE rme_batch_request SET " +
                "created=?, modified=?, assigned_scope_path=?, file_name=?, header=?, run_date=?, " +
                "completed_date=?, record_count=?, completed_count=?, error_count=?, invalid_count=?, " +
                "message=?, error_message=?, file_contents=?, status=?, run_config=?, owner=?, approval_status =?, " +
                "approved_by = ? "+
                "WHERE id=?";

        PreparedStatement ps = null;
        try {
        	ps = conn.prepareStatement(sql);
            setLong(ps, 1, r.getCreated());
            setLong(ps, 2, System.currentTimeMillis());
            ps.setString(3, r.getAssignedScopePath());
            ps.setString(4, r.getFileName());
            ps.setString(5, r.getHeader());
            setLong(ps, 6, r.getRunDate());
            setLong(ps, 7, r.getCompletedDate());
            setInt(ps, 8, r.getRecordCount());
            setInt(ps, 9, r.getCompletedCount());
            setInt(ps, 10, r.getErrorCount());
            setInt(ps, 11, r.getInvalidCount());
            ps.setString(12, r.getMessage());
            ps.setString(13, r.getErrorMessage());
            ps.setString(14, r.getFileContents());
            ps.setString(15, r.getStatus());
            ps.setString(16, r.getRunConfig());
            ps.setString(17, r.getOwner());
            
            ps.setString(18, r.getApprovalStatus());
            ps.setString(19, r.getApprovedBy());
            ps.setString(20, r.getId());
            ps.executeUpdate();
        }
        finally {
       	 IOUtil.closeQuietly(ps);
       }
    }

    public void deleteBatchRequest(String id) throws SQLException {
        // delete child items first
        deleteItemsByBatchId(id);

        String sql = "DELETE FROM rme_batch_request WHERE id=?";
        PreparedStatement ps = null;
        try {
        	ps = conn.prepareStatement(sql);
            ps.setString(1, id);
            ps.executeUpdate();
        }
        finally {
          	 IOUtil.closeQuietly(ps);
        }
    }

    public RmeBatchRequest findBatchRequest(String id) throws SQLException {
        String sql = "SELECT * FROM rme_batch_request WHERE id=?";
        PreparedStatement ps = null;
        try  {
        	ps = conn.prepareStatement(sql);
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapBatchRequest(rs);
                }
            }
        }
        finally {
         	 IOUtil.closeQuietly(ps);
        }
        return null;
    }
    
    public List<RmeBatchRequest> getBatchRequests() throws SQLException {
    	List<RmeBatchRequest> requests = new ArrayList<>();
        String sql = "SELECT * FROM rme_batch_request";
        PreparedStatement ps = null;
        try {
        	ps = conn.prepareStatement(sql);
            try (ResultSet rs = ps.executeQuery()) {
            	while (rs.next()) {
                	requests.add(mapBatchRequest(rs));
                }
            }
        }
        finally {
        	 IOUtil.closeQuietly(ps);
        }
        return requests;
    }

    /* ======================= BATCH ITEMS ======================= */

    public void insertBatchRequestItem(RmeBatchRequestItem i) throws SQLException {
        String sql = "INSERT INTO rme_batch_request_item " +
                "(id, created, modified, assigned_scope_path, request_data, status, message, " +
                " error_message, role_id, role_name, batch_request_id, " +
                " owner, owner_id, idx,differences) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        PreparedStatement ps = null;
        try {
        	ps = conn.prepareStatement(sql);
            ps.setString(1, i.getId());
            setLong(ps, 2, i.getCreated());
            setLong(ps, 3, i.getModified());
            ps.setString(4, i.getAssignedScopePath());
            ps.setString(5, i.getRequestData());
            ps.setString(6, i.getStatus());
            ps.setString(7, i.getMessage());
            ps.setString(8, i.getErrorMessage());
            
            ps.setString(9, i.getRoleId());
            ps.setString(10, i.getRoleName());
            ps.setString(11, i.getBatchRequestId());
            ps.setString(12, i.getOwner());
            ps.setString(13, i.getOwnerId());
            setInt(ps, 14, i.getIdx());
            ps.setString(15, i.getDifferences());
            ps.executeUpdate();
        }
        finally {
        	IOUtil.closeQuietly(ps);
        }
    }

    public void updateBatchRequestItem(RmeBatchRequestItem i) throws SQLException {
        String sql = "UPDATE rme_batch_request_item SET " +
                "created=?, modified=?, assigned_scope_path=?, request_data=?, status=?, message=?, " +
                "error_message=?, role_id=?, role_name=?, " +
                "batch_request_id=?, owner=?, owner_id=?, idx=?, differences=? " +
                "WHERE id=?";
        PreparedStatement ps = null;
        try {
        	ps = conn.prepareStatement(sql); 
            setLong(ps, 1, i.getCreated());
            setLong(ps, 2, System.currentTimeMillis());
            ps.setString(3, i.getAssignedScopePath());
            ps.setString(4, i.getRequestData());
            ps.setString(5, i.getStatus());
            ps.setString(6, i.getMessage());
            ps.setString(7, i.getErrorMessage());            
            ps.setString(8, i.getRoleId());
            ps.setString(9, i.getRoleName());
            ps.setString(10, i.getBatchRequestId());
            ps.setString(11, i.getOwner());
            ps.setString(12, i.getOwnerId());
            setInt(ps, 13, i.getIdx());
            ps.setString(14, i.getDifferences());
            ps.setString(15, i.getId());
            ps.executeUpdate();
        }
        finally {
        	IOUtil.closeQuietly(ps);
        }
    }

    public void deleteBatchRequestItem(String id) throws SQLException {
        String sql = "DELETE FROM rme_batch_request_item WHERE id=?";
        PreparedStatement ps = null;
        try  {
        	ps = conn.prepareStatement(sql);
            ps.setString(1, id);
            ps.executeUpdate();
        }
        finally {
        	IOUtil.closeQuietly(ps);
        }
    }

    public void deleteItemsByBatchId(String batchId) throws SQLException {
        String sql = "DELETE FROM rme_batch_request_item WHERE batch_request_id=?";
        PreparedStatement ps = null;
        try {
        	ps = conn.prepareStatement(sql);
            ps.setString(1, batchId);
            ps.executeUpdate();
        }
        finally {
        	IOUtil.closeQuietly(ps);
        }
    }

    public List<RmeBatchRequestItem> findItemsByBatchId(String batchId) throws SQLException {
        String sql = "SELECT * FROM rme_batch_request_item WHERE batch_request_id=? ORDER BY idx";
        List<RmeBatchRequestItem> list = new ArrayList<>();

        PreparedStatement ps = null;
        
        try {
        	ps = conn.prepareStatement(sql);
            ps.setString(1, batchId);
            ResultSet rs = null;
            try {
            	rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(mapBatchRequestItem(rs));
                }
            }
            finally {
            	IOUtil.closeQuietly(rs);
            }
        }
        finally {
        	IOUtil.closeQuietly(ps);
        }
        return list;
    }
    
    public List<RmeBatchRequest> getBatchRequests(int offset, int limit) throws SQLException {
        List<RmeBatchRequest> requests = new ArrayList<>();

        // ORDER BY is important so paging is stable
        String sql = "SELECT * FROM rme_batch_request " +
                     "ORDER BY created DESC " +
                     "LIMIT ? OFFSET ?";
                     //"OFFSET ? ROWS "+
                     //"FETCH NEXT ? ROWS ONLY";

        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(sql);
            ps.setInt(2, offset);
            ps.setInt(1, limit);
            

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    requests.add(mapBatchRequest(rs));
                }
            }
        } finally {
            IOUtil.closeQuietly(ps);
        }

        return requests;
    }

    public List<RmeBatchRequest> getBatchRequestsForUser(int offset, int limit, String userName) throws SQLException {
        List<RmeBatchRequest> requests = new ArrayList<>();

        // ORDER BY is important so paging is stable
        String sql = "SELECT * FROM rme_batch_request " +
                     "WHERE owner = ? " +
                     "ORDER BY created DESC " +
                     "LIMIT ? OFFSET ?";
                     //"OFFSET ? ROWS "+
                     //"FETCH NEXT ? ROWS ONLY";

        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(sql);
            ps.setString(1, userName);
            ps.setInt(2, limit);
            ps.setInt(3, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    requests.add(mapBatchRequest(rs));
                }
            }
        } finally {
            IOUtil.closeQuietly(ps);
        }

        return requests;
    }
    
    public int countBatchRequests() throws SQLException {
        String sql = "SELECT COUNT(*) FROM rme_batch_request";

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } finally {
            IOUtil.closeQuietly(rs);
            IOUtil.closeQuietly(ps);
        }
    }

    public int countBatchRequestsForUser(String userName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM rme_batch_request WHERE owner = ?";

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(sql);
            ps.setString(1, userName);
            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } finally {
            IOUtil.closeQuietly(rs);
            IOUtil.closeQuietly(ps);
        }
    }



    /* ======================= MAPPERS ======================= */

    private RmeBatchRequest mapBatchRequest(ResultSet rs) throws SQLException {
        RmeBatchRequest r = new RmeBatchRequest();
        r.setId(rs.getString("id"));
        r.setCreated(getLong(rs, "created"));
        r.setModified(getLong(rs, "modified"));
        r.setAssignedScopePath(rs.getString("assigned_scope_path"));
        r.setFileName(rs.getString("file_name"));
        r.setHeader(rs.getString("header"));
        r.setRunDate(getLong(rs, "run_date"));
        r.setCompletedDate(getLong(rs, "completed_date"));
        r.setRecordCount(getInt(rs, "record_count"));
        r.setCompletedCount(getInt(rs, "completed_count"));
        r.setErrorCount(getInt(rs, "error_count"));
        r.setInvalidCount(getInt(rs, "invalid_count"));
        r.setMessage(rs.getString("message"));
        r.setErrorMessage(rs.getString("error_message"));
        r.setFileContents(rs.getString("file_contents"));
        r.setStatus(rs.getString("status"));
        r.setRunConfig(rs.getString("run_config"));
        r.setOwner(rs.getString("owner"));
        r.setAssignedScope(rs.getString("assigned_scope"));
        r.setApprovalStatus(rs.getString("approval_status"));
        r.setApprovedBy(rs.getString("approved_by"));
        return r;
    }

    private RmeBatchRequestItem mapBatchRequestItem(ResultSet rs) throws SQLException {
        RmeBatchRequestItem i = new RmeBatchRequestItem();
        i.setId(rs.getString("id"));
        i.setCreated(getLong(rs, "created"));
        i.setModified(getLong(rs, "modified"));
        i.setAssignedScopePath(rs.getString("assigned_scope_path"));
        i.setRequestData(rs.getString("request_data"));
        i.setStatus(rs.getString("status"));
        i.setMessage(rs.getString("message"));
        i.setErrorMessage(rs.getString("error_message"));
        i.setRoleId(rs.getString("role_id"));
        i.setRoleName(rs.getString("role_name"));
        i.setBatchRequestId(rs.getString("batch_request_id"));
        i.setOwner(rs.getString("owner"));
        i.setOwnerId(rs.getString("owner_id"));
        i.setIdx(getInt(rs, "idx"));
        i.setDifferences(rs.getString("differences"));
        return i;
    }

    /* ======================= JSON helpers ======================= */

    private String jsonToString(JsonNode node) {
        if (node == null) return null;
        try {
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JSON", e);
        }
    }

    private JsonNode stringToJson(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return mapper.readTree(s);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }

    /* ======================= SQL null helpers ======================= */

    private void setInt(PreparedStatement ps, int idx, Integer val) throws SQLException {
        if (val == null) ps.setNull(idx, Types.INTEGER);
        else ps.setInt(idx, val);
    }

    private void setLong(PreparedStatement ps, int idx, Long val) throws SQLException {
        if (val == null) ps.setNull(idx, Types.BIGINT);
        else ps.setLong(idx, val);
    }

    private Integer getInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private Long getLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

	public void updateBatchRequestDecision(String batchId, String decision, String approvedBy) throws SQLException {
		String sql = "UPDATE rme_batch_request SET " +
                "modified=?, approval_status=?, approved_by=? WHERE id=?";
        PreparedStatement ps = null;
        try {
        	ps = conn.prepareStatement(sql);
            
            setLong(ps, 1, System.currentTimeMillis());
            ps.setString(2, decision);
            ps.setString(3, approvedBy);
            ps.setString(4, batchId);
            
            ps.executeUpdate();
        }
        finally {
        	IOUtil.closeQuietly(ps);
        }
		
	}

	public String getRoleDifferences(String itemId) throws SQLException {
		String sql = "SELECT differences FROM rme_batch_request_item where id=?";

		String differences = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(sql);
            ps.setString(1, itemId);
            rs = ps.executeQuery();
            if (rs.next()) {
            	differences =  rs.getString(1);
            }
            
        } finally {
            IOUtil.closeQuietly(rs);
            IOUtil.closeQuietly(ps);
        }
        return differences;
	}
}
