package com.metro.ticketing.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

@Entity
@Table(name = "\"Trains\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Train {

    @Id
    @Column(name = "\"TrainID\"", length = 50)
    private String trainId;

    @NotBlank(message = "Train number is required")
    @Column(name = "\"TrainNumber\"", length = 50, nullable = false)
    private String trainNumber;

    @Column(name = "\"Capacity\"")
    private Integer capacity;

    @Column(name = "\"TotalCoaches\"")
    private Integer totalCoaches;

    @Column(name = "\"ManufactureYear\"")
    private Integer manufactureYear;

    @Column(name = "\"CreatedAt\"")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "\"isActive\"")
    private Boolean isActive = true;

    public String getTrainId() {
        return trainId;
    }

    public void setTrainId(String trainId) {
        this.trainId = trainId;
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public void setTrainNumber(String trainNumber) {
        this.trainNumber = trainNumber;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Integer getTotalCoaches() {
        return totalCoaches;
    }

    public void setTotalCoaches(Integer totalCoaches) {
        this.totalCoaches = totalCoaches;
    }

    public Integer getManufactureYear() {
        return manufactureYear;
    }

    public void setManufactureYear(Integer manufactureYear) {
        this.manufactureYear = manufactureYear;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
