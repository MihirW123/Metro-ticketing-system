package com.metro.ticketing.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "\"Route\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Route {

    @Id
    @Column(name = "\"RouteID\"", length = 50)
    private String routeId;

    @NotBlank(message = "Route name is required")
    @Column(name = "\"RouteName\"", length = 150, nullable = false)
    private String routeName;

    @Column(name = "\"TotalDistance\"", precision = 10, scale = 2)
    private BigDecimal totalDistance;

    @Column(name = "\"EstimatedTime\"")
    private Integer estimatedTime;

    @Column(name = "\"LineColor\"", length = 50)
    private String lineColor;

    @Column(name = "\"CreatedAt\"")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "\"UpdatedAt\"")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"StartStationID\"", referencedColumnName = "\"StationID\"")
    private Station startStation;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"EndStationID\"", referencedColumnName = "\"StationID\"")
    private Station endStation;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String startStationId;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String endStationId;

    @Transient
    private List<String> stationIds;

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public BigDecimal getTotalDistance() {
        return totalDistance;
    }

    public void setTotalDistance(BigDecimal totalDistance) {
        this.totalDistance = totalDistance;
    }

    public Integer getEstimatedTime() {
        return estimatedTime;
    }

    public void setEstimatedTime(Integer estimatedTime) {
        this.estimatedTime = estimatedTime;
    }

    public String getLineColor() {
        return lineColor;
    }

    public void setLineColor(String lineColor) {
        this.lineColor = lineColor;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Station getStartStation() {
        return startStation;
    }

    public void setStartStation(Station startStation) {
        this.startStation = startStation;
    }

    public Station getEndStation() {
        return endStation;
    }

    public void setEndStation(Station endStation) {
        this.endStation = endStation;
    }

    public String getStartStationId() {
        return startStationId;
    }

    public void setStartStationId(String startStationId) {
        this.startStationId = startStationId;
    }

    public String getEndStationId() {
        return endStationId;
    }

    public void setEndStationId(String endStationId) {
        this.endStationId = endStationId;
    }

    public List<String> getStationIds() {
        return stationIds;
    }

    public void setStationIds(List<String> stationIds) {
        this.stationIds = stationIds;
    }
}
