package io.jenkins.plugins.model;

import java.util.Objects;

public class DevOpsChangeRequestDetails {
    String changeState;
    String changeAssignmentGroup;
    String changeApprovers;
    String changeStartDate;
    String changeEndDate;

    public DevOpsChangeRequestDetails() {}
    public DevOpsChangeRequestDetails(String changeState, String changeAssignmentGroup, String changeApprovers, String changeStartDate, String changeEndDate) {
        this.changeState = changeState;
        this.changeAssignmentGroup = changeAssignmentGroup;
        this.changeApprovers = changeApprovers;
        this.changeStartDate = changeStartDate;
        this.changeEndDate = changeEndDate;
    }
    public String getChangeState() {
        return changeState;
    }

    public void setChangeState(String changeState) {
        this.changeState = changeState;
    }

    public String getChangeAssignmentGroup() {
        return changeAssignmentGroup;
    }

    public void setChangeAssignmentGroup(String changeAssignmentGroup) {
        this.changeAssignmentGroup = changeAssignmentGroup;
    }

    public String getChangeApprovers() {
        return changeApprovers;
    }

    public void setChangeApprovers(String changeApprovers) {
        this.changeApprovers = changeApprovers;
    }

    public String getChangeStartDate() {
        return changeStartDate;
    }

    public void setChangeStartDate(String changeStartDate) {
        this.changeStartDate = changeStartDate;
    }

    public String getChangeEndDate() {
        return changeEndDate;
    }

    public void setChangeEndDate(String changeEndDate) {
        this.changeEndDate = changeEndDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DevOpsChangeRequestDetails)) return false;
        DevOpsChangeRequestDetails that = (DevOpsChangeRequestDetails) o;
        return Objects.equals(getChangeState(), that.getChangeState()) && Objects.equals(getChangeAssignmentGroup(), that.getChangeAssignmentGroup()) && Objects.equals(getChangeApprovers(), that.getChangeApprovers()) && Objects.equals(getChangeStartDate(), that.getChangeStartDate()) && Objects.equals(getChangeEndDate(), that.getChangeEndDate());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getChangeState(), getChangeAssignmentGroup(), getChangeApprovers(), getChangeStartDate(), getChangeEndDate());
    }
}
