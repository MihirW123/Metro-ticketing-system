package com.metro.ticketing.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Entity
@Table(name = "\"Fares\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Fare {

    @Id
    @Column(name = "\"FareID\"", length = 50)
    private String fareId;

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
    private String sourceStationId;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String destStationId;

    @NotNull(message = "Base fare is required")
    @DecimalMin(value = "0.01", message = "Base fare must be greater than zero")
    @Column(name = "\"BaseFare\"", precision = 10, scale = 2, nullable = false)
    private BigDecimal baseFare;

    public String getFareId() {
        return fareId;
    }

    public void setFareId(String fareId) {
        this.fareId = fareId;
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

    public BigDecimal getBaseFare() {
        return baseFare;
    }

    public void setBaseFare(BigDecimal baseFare) {
        this.baseFare = baseFare;
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
