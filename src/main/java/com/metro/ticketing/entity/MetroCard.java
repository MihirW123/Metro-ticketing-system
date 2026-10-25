package com.metro.ticketing.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "\"MetroCards\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class MetroCard {

    @Id
    @Column(name = "\"CardID\"", length = 50)
    private String cardId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"PassengerID\"", referencedColumnName = "\"UserId\"", nullable = false)
    private User passenger;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passengerId;

    @Column(name = "\"Balance\"", precision = 10, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "\"IssueDate\"")
    private LocalDate issueDate = LocalDate.now();

    @Column(name = "\"ExpiryDate\"")
    private LocalDate expiryDate;

    @Column(name = "\"CreatedAt\"")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "\"isActive\"")
    private Boolean isActive = true;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public User getPassenger() {
        return passenger;
    }

    public void setPassenger(User passenger) {
        this.passenger = passenger;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
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

    public String getPassengerId() {
        return passengerId;
    }

    public void setPassengerId(String passengerId) {
        this.passengerId = passengerId;
    }
}
