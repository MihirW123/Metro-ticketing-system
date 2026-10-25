package com.metro.ticketing.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

@Entity
@Table(name = "\"Tickets\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Ticket {

    @Id
    @Column(name = "\"TicketID\"", length = 50)
    private String ticketId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"PassengerID\"", referencedColumnName = "\"UserId\"", nullable = false)
    private User passenger;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"FareID\"", referencedColumnName = "\"FareID\"", nullable = false)
    private Fare fare;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"SourceStationID\"", referencedColumnName = "\"StationID\"", nullable = false)
    private Station sourceStation;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"DestStationID\"", referencedColumnName = "\"StationID\"", nullable = false)
    private Station destStation;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passengerId;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String sourceStationId;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String destStationId;

    @NotBlank(message = "Ticket type is required")
    @Column(name = "\"TicketType\"", length = 50)
    private String ticketType;

    @Column(name = "\"ValidUntil\"")
    private LocalDateTime validUntil;

    @Column(name = "\"isUsed\"")
    private Boolean isUsed = false;

    @Column(name = "\"IssueTime\"")
    private LocalDateTime issueTime = LocalDateTime.now();

    @Column(name = "\"CreatedAt\"")
    private LocalDateTime createdAt = LocalDateTime.now();

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public User getPassenger() {
        return passenger;
    }

    public void setPassenger(User passenger) {
        this.passenger = passenger;
    }

    public Fare getFare() {
        return fare;
    }

    public void setFare(Fare fare) {
        this.fare = fare;
    }

    public Station getSourceStation() {
        return sourceStation;
    }

    public void setSourceStation(Station sourceStation) {
        this.sourceStation = sourceStation;
    }

    public Station getDestStation() {
        return destStation;
    }

    public void setDestStation(Station destStation) {
        this.destStation = destStation;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public LocalDateTime getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDateTime validUntil) {
        this.validUntil = validUntil;
    }

    public Boolean getIsUsed() {
        return isUsed;
    }

    public void setIsUsed(Boolean isUsed) {
        this.isUsed = isUsed;
    }

    public LocalDateTime getIssueTime() {
        return issueTime;
    }

    public void setIssueTime(LocalDateTime issueTime) {
        this.issueTime = issueTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getPassengerId() {
        return passengerId;
    }

    public void setPassengerId(String passengerId) {
        this.passengerId = passengerId;
    }

    public String getSourceStationId() {
        return sourceStationId;
    }

    public void setSourceStationId(String sourceStationId) {
        this.sourceStationId = sourceStationId;
    }

    public String getDestStationId() {
        return destStationId;
    }

    public void setDestStationId(String destStationId) {
        this.destStationId = destStationId;
    }
}
