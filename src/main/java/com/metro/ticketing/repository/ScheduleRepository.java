package com.metro.ticketing.repository;

import com.metro.ticketing.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, String> {

    @Query("SELECT COUNT(s) > 0 FROM Schedule s WHERE s.train.trainId = :trainId AND " +
           "s.scheduledDeparture < :arrivalTime AND s.scheduledArrival > :departureTime AND " +
           "(:scheduleId IS NULL OR s.scheduleId != :scheduleId)")
    boolean hasOverlappingSchedule(
            @Param("trainId") String trainId, 
            @Param("departureTime") LocalDateTime departureTime, 
            @Param("arrivalTime") LocalDateTime arrivalTime,
            @Param("scheduleId") String scheduleId
    );
}
