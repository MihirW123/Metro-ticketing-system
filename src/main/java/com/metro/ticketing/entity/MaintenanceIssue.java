package com.metro.ticketing.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

@Entity
@Table(name = "\"MaintenanceIssue\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class MaintenanceIssue {

    @Id
    @Column(name = "\"IssueID\"", length = 50)
    private String issueId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"TrainID\"", referencedColumnName = "\"TrainID\"")
    private Train train;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"StationID\"", referencedColumnName = "\"StationID\"")
    private Station station;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"ReportedId\"", referencedColumnName = "\"UserId\"")
    private User reportedBy;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String trainId;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String stationId;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String reportedById;

    @NotBlank(message = "Issue type is required")
    @Column(name = "\"IssueType\"", length = 100)
    private String issueType;

    @NotBlank(message = "Description is required")
    @Column(name = "\"Description\"", columnDefinition = "TEXT")
    private String description;

    @Column(name = "\"Status\"", length = 50)
    private String status = "Open";

    @Column(name = "\"Priority\"", length = 20)
    private String priority;

    @Column(name = "\"CreatedAt\"")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "\"ResolvedAt\"")
    private LocalDateTime resolvedAt;

    public String getIssueId() {
        return issueId;
    }

    public void setIssueId(String issueId) {
        this.issueId = issueId;
    }

    public Train getTrain() {
        return train;
    }

    public void setTrain(Train train) {
        this.train = train;
    }

    public Station getStation() {
        return station;
    }

    public void setStation(Station station) {
        this.station = station;
    }

    public User getReportedBy() {
        return reportedBy;
    }

    public void setReportedBy(User reportedBy) {
        this.reportedBy = reportedBy;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getTrainId() {
        return trainId;
    }

    public void setTrainId(String trainId) {
        this.trainId = trainId;
    }

    public String getStationId() {
        return stationId;
    }

    public void setStationId(String stationId) {
        this.stationId = stationId;
    }

    public String getReportedById() {
        return reportedById;
    }

    public void setReportedById(String reportedById) {
        this.reportedById = reportedById;
    }
}
