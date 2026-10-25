package com.metro.ticketing.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

/**
 * One stop on a route, with its order number.
 *
 * A generated RouteStationID is the primary key, while the unique constraint
 * on (RouteID, StationID) ensures the same station cannot appear twice on a route.
 */
@Entity
@Table(name = "\"RouteStations\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RouteStation {

    @Id
    @Column(name = "\"RouteStationID\"", length = 50)
    private String routeStationId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"RouteID\"")
    private Route route;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"StationID\"")
    private Station station;

    @Column(name = "\"SequenceNumber\"", nullable = false)
    private Integer sequenceNumber;

    public String getRouteStationId() {
        return routeStationId;
    }

    public void setRouteStationId(String routeStationId) {
        this.routeStationId = routeStationId;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public Station getStation() {
        return station;
    }

    public void setStation(Station station) {
        this.station = station;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }
}
