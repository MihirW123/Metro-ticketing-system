package com.metro.ticketing.repository;

import com.metro.ticketing.entity.MaintenanceIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceIssueRepository extends JpaRepository<MaintenanceIssue, String> {
    List<MaintenanceIssue> findByTrain_TrainId(String trainId);
    List<MaintenanceIssue> findByStation_StationId(String stationId);
    List<MaintenanceIssue> findByStatus(String status);
}