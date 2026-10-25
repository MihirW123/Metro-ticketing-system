package com.metro.ticketing.repository;

import com.metro.ticketing.entity.RouteStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteStationRepository extends JpaRepository<RouteStation, String> {
    List<RouteStation> findByRoute_RouteIdOrderBySequenceNumberAsc(String routeId);
    void deleteByRoute_RouteId(String routeId);
}
