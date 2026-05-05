package sailpoint.plugin.rolemanagement.modal;


public class RoleChangeDetail {

    private String changeId;
    private String requestId;
    private String fieldName;
    private String oldValue;
    private String newValue;

    public RoleChangeDetail() {}

    public RoleChangeDetail(String changeId, String requestId, String fieldName,
                             String oldValue, String newValue) {
        this.changeId = changeId;
        this.requestId = requestId;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    // Getters and Setters

    public String getChangeId() {
        return changeId;
    }

    public void setChangeId(String changeId) {
        this.changeId = changeId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    @Override
    public String toString() {
        return "RoleChangeDetail{" +
                "changeId='" + changeId + '\'' +
                ", requestId='" + requestId + '\'' +
                ", fieldName='" + fieldName + '\'' +
                ", oldValue='" + oldValue + '\'' +
                ", newValue='" + newValue + '\'' +
                '}';
    }
}

