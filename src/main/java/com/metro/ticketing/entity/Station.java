package com.metro.ticketing.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

@Entity
@Table(name = "\"Stations\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Station {

    @Id
    @Column(name = "\"StationID\"", length = 50)
    private String stationId;

    @NotBlank(message = "Station code is required")
    @Column(name = "\"StationCode\"", length = 20, nullable = false)
    private String stationCode;

    @Column(name = "\"Latitude\"")
    private Double latitude;

    @Column(name = "\"Longitude\"")
    private Double longitude;

    @Column(name = "\"Address\"")
    private String address;

    @Column(name = "\"OpenedDate\"")
    private LocalDate openedDate;

    @Column(name = "\"LineColor\"", length = 50)
    private String lineColor;

    @Column(name = "\"isActive\"")
    private Boolean isActive = true;

    public String getStationId() {
        return stationId;
    }

    public void setStationId(String stationId) {
        this.stationId = stationId;
    }

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDate getOpenedDate() {
        return openedDate;
    }

    public void setOpenedDate(LocalDate openedDate) {
        this.openedDate = openedDate;
    }

    public String getLineColor() {
        return lineColor;
    }

    public void setLineColor(String lineColor) {
        this.lineColor = lineColor;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
