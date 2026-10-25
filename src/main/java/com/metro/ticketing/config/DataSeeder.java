package com.metro.ticketing.config;

import com.metro.ticketing.entity.*;
import com.metro.ticketing.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String DEMO_PASSWORD = "Passw0rd!";

    private final StationRepository stationRepository;
    private final TrainRepository trainRepository;
    private final UserRepository userRepository;
    private final RouteRepository routeRepository;
    private final RouteStationRepository routeStationRepository;
    private final ScheduleRepository scheduleRepository;
    private final FareRepository fareRepository;
    private final MetroCardRepository metroCardRepository;
    private final TicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;
    private final MaintenanceIssueRepository maintenanceIssueRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin.contact:admin}")
    private String adminContact;

    @Value("${app.bootstrap.admin.password:admin123}")
    private String adminPassword;

    @Value("${app.bootstrap.admin.first-name:System}")
    private String adminFirstName;

    @Value("${app.bootstrap.admin.last-name:Administrator}")
    private String adminLastName;

    public DataSeeder(StationRepository stationRepository,
                      TrainRepository trainRepository,
                      UserRepository userRepository,
                      RouteRepository routeRepository,
                      RouteStationRepository routeStationRepository,
                      ScheduleRepository scheduleRepository,
                      FareRepository fareRepository,
                      MetroCardRepository metroCardRepository,
                      TicketRepository ticketRepository,
                      PaymentRepository paymentRepository,
                      MaintenanceIssueRepository maintenanceIssueRepository,
                      PasswordEncoder passwordEncoder) {
        this.stationRepository = stationRepository;
        this.trainRepository = trainRepository;
        this.userRepository = userRepository;
        this.routeRepository = routeRepository;
        this.routeStationRepository = routeStationRepository;
        this.scheduleRepository = scheduleRepository;
        this.fareRepository = fareRepository;
        this.metroCardRepository = metroCardRepository;
        this.ticketRepository = ticketRepository;
        this.paymentRepository = paymentRepository;
        this.maintenanceIssueRepository = maintenanceIssueRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureAdminExists();

        if (stationRepository.count() > 0) {
            log.info("DataSeeder: sample data already present, skipping.");
            return;
        }
        log.info("DataSeeder: loading demo data set...");
        seedUsers();
        seedStations();
        seedTrains();
        User priya = userRepository.findById("PASS-SEED1").orElseThrow();
        User arjun = userRepository.findById("PASS-SEED2").orElseThrow();
        User meera = userRepository.findById("PASS-SEED3").orElseThrow();
        User agent = userRepository.findById("STAFF-01").orElseThrow();
        User maint = userRepository.findById("STAFF-02").orElseThrow();

        seedRoutes();
        seedSchedules();
        seedFares();
        seedCards(priya, arjun, meera);
        seedTickets(priya, arjun);
        seedPayments();
        seedMaintenance(agent, maint);
        log.info("DataSeeder: demo data loaded successfully.");
    }

    private void ensureAdminExists() {
        if (userRepository.findByContact(adminContact).isPresent()) {
            return;
        }

        User admin = new User();
        admin.setUserId("ADMIN-" + UUID.randomUUID());
        admin.setFirstName(adminFirstName);
        admin.setLastName(adminLastName);
        admin.setContact(adminContact);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setRegistrationDate(LocalDate.now());
        admin.setIsActive(true);
        userRepository.save(admin);
        log.info("DataSeeder: bootstrap admin '{}' created.", adminContact);
    }

    private void seedUsers() {
        userRepository.save(user("STAFF-01", "Rahul", "Mehta", Role.TICKET_AGENT, "agent1", LocalDate.now().minusYears(4)));
        userRepository.save(user("STAFF-02", "Suresh", "Kumar", Role.MAINTENANCE_STAFF, "maint1", LocalDate.now().minusYears(6)));
        userRepository.save(user("PASS-SEED1", "Priya", "Sharma", Role.PASSENGER, "priya", null));
        userRepository.save(user("PASS-SEED2", "Arjun", "Rao", Role.PASSENGER, "arjun", null));
        userRepository.save(user("PASS-SEED3", "Meera", "Singh", Role.PASSENGER, "meera", null));
    }

    private User user(String id, String first, String last, Role role, String contact, LocalDate hireDate) {
        User user = new User();
        user.setUserId(id);
        user.setFirstName(first);
        user.setLastName(last);
        user.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
        user.setRole(role);
        user.setContact(contact);
        user.setRegistrationDate(role == Role.PASSENGER ? LocalDate.now().minusMonths(6) : LocalDate.now());
        user.setHireDate(hireDate);
        user.setIsActive(true);
        return user;
    }

    private void seedStations() {
        // Mumbai Metro Line 1 (Blue) - Versova to Ghatkopar
        station("STN-VSV", "VSV", "Versova", 19.1354, 72.8209, "JP Road, Andheri West", "Blue");
        station("STN-AND", "AND", "Andheri", 19.1100, 72.8637, "Western Express Highway, Andheri East", "Blue");
        station("STN-WEH", "WEH", "Western Express Highway", 19.1147, 72.8567, "WEH, Vile Parle East", "Blue");
        station("STN-APR", "APR", "Airport Road", 19.0906, 72.8680, "Airport Road, Marol, Santacruz East", "Blue");
        station("STN-MRL", "MRL", "Marol Naka", 19.1188, 72.8951, "Marol Naka, Andheri East", "Interchange");
        station("STN-SKN", "SKN", "Saki Naka", 19.1056, 72.8957, "Saki Naka Junction, Saki Vihar Road", "Blue");
        station("STN-GHP", "GHP", "Ghatkopar", 19.0880, 72.9096, "Ghatkopar West, Station Road Junction", "Blue");

        // Mumbai Metro Line 3 (Aqua) - Aarey to CSMT
        station("STN-ARY", "ARY", "Aarey", 19.1507, 72.8938, "Aarey Colony, Goral, Goregaon East", "Aqua");
        station("STN-SEP", "SEP", "SEEPZ", 19.1376, 72.8845, "SEEPZ, Andheri East", "Aqua");
        station("STN-BKC", "BKC", "Bandra Kurla Complex", 19.0686, 72.8663, "Bandra Kurla Complex, Bandra East", "Aqua");
        station("STN-DDR", "DDR", "Dadar", 19.0176, 72.8453, "Dadar TT Chowk, Eastern Express Highway", "Aqua");
        station("STN-MCU", "MCU", "Mumbai Central", 18.9693, 72.8196, "Mumbai Central, Delisle Road", "Aqua");
        station("STN-CSM", "CSM", "CSMT", 18.9402, 72.8355, "Chhatrapati Shivaji Maharaj Terminus, Fort", "Aqua");

        // Mumbai Metro Line 7 (Red) - Dahisar East to Andheri East
        station("STN-DHE", "DHE", "Dahisar East", 19.2466, 72.8649, "Dahisar East, Western Express Highway", "Red");
        station("STN-DVP", "DVP", "Devipada", 19.2208, 72.8587, "Devipada, Borivali East", "Red");
        station("STN-POI", "POI", "Poisar", 19.2087, 72.8528, "Poisar, Borivali East", "Red");
        station("STN-GDU", "GDU", "Gundavali", 19.1412, 72.8499, "Gundavali, Andheri East", "Red");
        station("STN-AEE", "AEE", "Andheri East", 19.1136, 72.8690, "Andheri East, Western Express Highway", "Red");
    }

    private void station(String id, String code, String name, double lat, double lng, String address, String color) {
        Station station = new Station();
        station.setStationId(id);
        station.setStationCode(code);
        station.setAddress(name + " - " + address);
        station.setLatitude(lat);
        station.setLongitude(lng);
        station.setOpenedDate(LocalDate.of(2010, 1, 1).plusDays(id.hashCode() % 2000));
        station.setLineColor(color);
        station.setIsActive(true);
        stationRepository.save(station);
    }

    private void seedTrains() {
        train("TRN-101", "M1", "Blue Line", 1800, 8, 2014);
        train("TRN-102", "M2", "Blue Line", 1800, 8, 2016);
        train("TRN-103", "M3", "Blue Line", 1800, 8, 2019);
        train("TRN-201", "A1", "Aqua Line", 2400, 8, 2022);
        train("TRN-202", "A2", "Aqua Line", 2400, 8, 2023);
        train("TRN-203", "A3", "Aqua Line", 2400, 8, 2024);
        train("TRN-301", "R1", "Red Line", 1800, 8, 2020);
        train("TRN-302", "R2", "Red Line", 1800, 8, 2021);
    }

    private void train(String id, String number, String line, int capacity, int coaches, int year) {
        Train train = new Train();
        train.setTrainId(id);
        train.setTrainNumber(number);
        train.setCapacity(capacity);
        train.setTotalCoaches(coaches);
        train.setManufactureYear(year);
        train.setCreatedAt(LocalDateTime.now().minusMonths(1));
        train.setIsActive(true);
        trainRepository.save(train);
    }

    private void seedRoutes() {
        route("RT-L1", "Mumbai Metro Line 1 - Blue (Versova to Ghatkopar)",
                new BigDecimal("11.10"), 21, "Blue", "STN-VSV", "STN-GHP",
                "STN-AND", "STN-WEH", "STN-APR", "STN-MRL", "STN-SKN");
        route("RT-L2", "Mumbai Metro Line 3 - Aqua (Aarey to CSMT)",
                new BigDecimal("18.50"), 33, "Aqua", "STN-ARY", "STN-CSM",
                "STN-SEP", "STN-MRL", "STN-BKC", "STN-DDR", "STN-MCU");
        route("RT-L3", "Mumbai Metro Line 7 - Red (Dahisar East to Andheri East)",
                new BigDecimal("12.30"), 22, "Red", "STN-DHE", "STN-AEE",
                "STN-DVP", "STN-POI", "STN-GDU");
    }

    private void route(String id, String name, BigDecimal distance, int minutes, String color,
                       String startId, String endId, String... intermediateIds) {
        Route route = new Route();
        route.setRouteId(id);
        route.setRouteName(name);
        route.setTotalDistance(distance);
        route.setEstimatedTime(minutes);
        route.setLineColor(color);
        route.setCreatedAt(LocalDateTime.now().minusWeeks(2));
        route.setUpdatedAt(LocalDateTime.now().minusWeeks(2));
        route.setStartStation(stationRepository.findById(startId).orElseThrow());
        route.setEndStation(stationRepository.findById(endId).orElseThrow());
        routeRepository.save(route);

        int seq = 2;
        for (String stationId : intermediateIds) {
            RouteStation rs = new RouteStation();
            rs.setRouteStationId("RS-" + UUID.randomUUID());
            rs.setRoute(route);
            rs.setStation(stationRepository.findById(stationId).orElseThrow());
            rs.setSequenceNumber(seq++);
            routeStationRepository.save(rs);
        }
    }

    private void seedSchedules() {
        LocalDate today = LocalDate.now();
        schedule("SCH-001", "TRN-101", "RT-L1", "DAILY", today.atTime(6, 0), today.atTime(6, 46));
        schedule("SCH-002", "TRN-102", "RT-L1", "DAILY", today.atTime(7, 0), today.atTime(7, 46));
        schedule("SCH-003", "TRN-103", "RT-L1", "MONDAY", today.atTime(8, 30), today.atTime(9, 16));
        schedule("SCH-004", "TRN-201", "RT-L2", "DAILY", today.atTime(6, 10), today.atTime(7, 8));
        schedule("SCH-005", "TRN-202", "RT-L2", "DAILY", today.atTime(6, 55), today.atTime(7, 53));
        schedule("SCH-006", "TRN-203", "RT-L2", "SATURDAY", today.atTime(9, 15), today.atTime(10, 13));
        schedule("SCH-007", "TRN-301", "RT-L3", "DAILY", today.atTime(6, 5), today.atTime(6, 39));
        schedule("SCH-008", "TRN-302", "RT-L3", "DAILY", today.atTime(6, 35), today.atTime(7, 9));
    }

    private void schedule(String id, String trainId, String routeId, String day,
                          LocalDateTime dep, LocalDateTime arr) {
        Schedule schedule = new Schedule();
        schedule.setScheduleId(id);
        schedule.setTrain(trainRepository.findById(trainId).orElseThrow());
        schedule.setRoute(routeRepository.findById(routeId).orElseThrow());
        schedule.setDayOfWeek(day);
        schedule.setScheduledDeparture(dep);
        schedule.setScheduledArrival(arr);
        schedule.setValidFrom(LocalDate.now().minusDays(1));
        schedule.setValidTo(LocalDate.now().plusYears(1));
        schedule.setIsActive(true);
        schedule.setCreatedAt(LocalDateTime.now().minusWeeks(1));
        scheduleRepository.save(schedule);
    }

    private void seedFares() {
        // Line 1 (Blue) - Versova to Ghatkopar
        fare("STN-VSV", "STN-AND", "15.00");  fare("STN-AND", "STN-VSV", "15.00");
        fare("STN-VSV", "STN-WEH", "20.00");  fare("STN-WEH", "STN-VSV", "20.00");
        fare("STN-VSV", "STN-APR", "25.00");  fare("STN-APR", "STN-VSV", "25.00");
        fare("STN-VSV", "STN-MRL", "30.00");  fare("STN-MRL", "STN-VSV", "30.00");
        fare("STN-VSV", "STN-SKN", "35.00");  fare("STN-SKN", "STN-VSV", "35.00");
        fare("STN-VSV", "STN-GHP", "40.00");  fare("STN-GHP", "STN-VSV", "40.00");
        fare("STN-AND", "STN-WEH", "10.00");  fare("STN-WEH", "STN-AND", "10.00");
        fare("STN-AND", "STN-APR", "15.00");  fare("STN-APR", "STN-AND", "15.00");
        fare("STN-AND", "STN-GHP", "25.00");  fare("STN-GHP", "STN-AND", "25.00");
        fare("STN-WEH", "STN-APR", "10.00");  fare("STN-APR", "STN-WEH", "10.00");
        fare("STN-WEH", "STN-MRL", "15.00");  fare("STN-MRL", "STN-WEH", "15.00");
        fare("STN-APR", "STN-MRL", "10.00");  fare("STN-MRL", "STN-APR", "10.00");
        fare("STN-APR", "STN-GHP", "20.00");  fare("STN-GHP", "STN-APR", "20.00");
        fare("STN-MRL", "STN-SKN", "10.00");  fare("STN-SKN", "STN-MRL", "10.00");
        fare("STN-MRL", "STN-GHP", "15.00");  fare("STN-GHP", "STN-MRL", "15.00");
        fare("STN-SKN", "STN-GHP", "10.00");  fare("STN-GHP", "STN-SKN", "10.00");

        // Line 3 (Aqua) - Aarey to CSMT
        fare("STN-ARY", "STN-SEP", "15.00");  fare("STN-SEP", "STN-ARY", "15.00");
        fare("STN-ARY", "STN-MRL", "20.00");  fare("STN-MRL", "STN-ARY", "20.00");
        fare("STN-ARY", "STN-BKC", "40.00");  fare("STN-BKC", "STN-ARY", "40.00");
        fare("STN-ARY", "STN-DDR", "45.00");  fare("STN-DDR", "STN-ARY", "45.00");
        fare("STN-ARY", "STN-CSM", "60.00");  fare("STN-CSM", "STN-ARY", "60.00");
        fare("STN-SEP", "STN-MRL", "10.00");  fare("STN-MRL", "STN-SEP", "10.00");
        fare("STN-SEP", "STN-BKC", "30.00");  fare("STN-BKC", "STN-SEP", "30.00");
        fare("STN-SEP", "STN-CSM", "50.00");  fare("STN-CSM", "STN-SEP", "50.00");
        fare("STN-BKC", "STN-DDR", "15.00");  fare("STN-DDR", "STN-BKC", "15.00");
        fare("STN-BKC", "STN-MCU", "30.00");  fare("STN-MCU", "STN-BKC", "30.00");
        fare("STN-BKC", "STN-CSM", "50.00");  fare("STN-CSM", "STN-BKC", "50.00");
        fare("STN-DDR", "STN-MCU", "15.00");  fare("STN-MCU", "STN-DDR", "15.00");
        fare("STN-DDR", "STN-CSM", "35.00");  fare("STN-CSM", "STN-DDR", "35.00");
        fare("STN-MCU", "STN-CSM", "20.00");  fare("STN-CSM", "STN-MCU", "20.00");

        // Line 7 (Red) - Dahisar East to Andheri East
        fare("STN-DHE", "STN-DVP", "10.00");  fare("STN-DVP", "STN-DHE", "10.00");
        fare("STN-DHE", "STN-POI", "15.00");  fare("STN-POI", "STN-DHE", "15.00");
        fare("STN-DHE", "STN-GDU", "25.00");  fare("STN-GDU", "STN-DHE", "25.00");
        fare("STN-DHE", "STN-AEE", "30.00");  fare("STN-AEE", "STN-DHE", "30.00");
        fare("STN-DVP", "STN-POI", "10.00");  fare("STN-POI", "STN-DVP", "10.00");
        fare("STN-DVP", "STN-GDU", "15.00");  fare("STN-GDU", "STN-DVP", "15.00");
        fare("STN-POI", "STN-GDU", "10.00");  fare("STN-GDU", "STN-POI", "10.00");
        fare("STN-POI", "STN-AEE", "20.00");  fare("STN-AEE", "STN-POI", "20.00");
        fare("STN-GDU", "STN-AEE", "10.00");  fare("STN-AEE", "STN-GDU", "10.00");
    }

    private void fare(String sourceId, String destId, String amount) {
        Fare fare = new Fare();
        fare.setFareId("FARE-" + sourceId + "-" + destId);
        fare.setSourceStation(stationRepository.findById(sourceId).orElseThrow());
        fare.setDestStation(stationRepository.findById(destId).orElseThrow());
        fare.setBaseFare(new BigDecimal(amount));
        fareRepository.save(fare);
    }

    private void seedCards(User priya, User arjun, User meera) {
        card("CARD-SEED1", priya, "250.00", true, LocalDate.now().plusYears(3));
        card("CARD-SEED2", priya, "0.00", false, LocalDate.now().plusMonths(2));
        card("CARD-SEED3", arjun, "120.00", true, LocalDate.now().plusYears(2));
        card("CARD-SEED4", meera, "75.50", true, LocalDate.now().plusYears(4));
    }

    private void card(String id, User user, String balance, boolean active, LocalDate expiry) {
        MetroCard card = new MetroCard();
        card.setCardId(id);
        card.setPassenger(user);
        card.setBalance(new BigDecimal(balance));
        card.setIssueDate(LocalDate.now().minusMonths(8));
        card.setExpiryDate(expiry);
        card.setCreatedAt(LocalDateTime.now().minusMonths(8));
        card.setIsActive(active);
        metroCardRepository.save(card);
    }

    private void seedTickets(User priya, User arjun) {
        ticket("TKT-SEED1", priya, "STN-VSV", "STN-WEH", "SINGLE", true,
                LocalDateTime.now().plusHours(2), LocalDateTime.now().minusDays(1), "STN-VSV-STN-WEH");
        ticket("TKT-SEED2", priya, "STN-BKC", "STN-CSM", "ROUND_TRIP", true,
                LocalDateTime.now().plusDays(7), LocalDateTime.now().minusDays(3), "STN-BKC-STN-CSM");
        ticket("TKT-SEED3", priya, "STN-SKN", "STN-GHP", "SINGLE", false,
                LocalDateTime.now().plusHours(20), LocalDateTime.now().minusHours(2), "STN-SKN-STN-GHP");
        ticket("TKT-SEED4", priya, "STN-MRL", "STN-GHP", "DAILY", false,
                LocalDateTime.now().plusHours(30), LocalDateTime.now().minusHours(5), "STN-MRL-STN-GHP");
        ticket("TKT-SEED5", arjun, "STN-ARY", "STN-BKC", "WEEKLY", false,
                LocalDateTime.now().plusDays(6), LocalDateTime.now().minusDays(1), "STN-ARY-STN-BKC");
    }

    private void ticket(String id, User user, String src, String dst, String type,
                        boolean used, LocalDateTime validUntil, LocalDateTime issued, String fareSrcDest) {
        Ticket ticket = new Ticket();
        ticket.setTicketId(id);
        ticket.setPassenger(user);
        ticket.setFare(fareRepository.findById("FARE-" + fareSrcDest).orElseThrow());
        ticket.setSourceStation(stationRepository.findById(src).orElseThrow());
        ticket.setDestStation(stationRepository.findById(dst).orElseThrow());
        ticket.setTicketType(type);
        ticket.setIsUsed(used);
        ticket.setValidUntil(validUntil);
        ticket.setIssueTime(issued);
        ticket.setCreatedAt(issued);
        ticketRepository.save(ticket);
    }

    private void seedPayments() {
        payment("PAY-SEED1", "TKT-SEED1", "20.00", LocalDateTime.now().minusDays(1));
        payment("PAY-SEED2", "TKT-SEED2", "50.00", LocalDateTime.now().minusDays(3));
        payment("PAY-SEED3", "TKT-SEED3", "10.00", LocalDateTime.now().minusHours(2));
    }

    private void payment(String id, String ticketId, String amount, LocalDateTime createdAt) {
        Payment payment = new Payment();
        payment.setPaymentId(id);
        payment.setTicket(ticketRepository.findById(ticketId).orElseThrow());
        payment.setAmount(new BigDecimal(amount));
        payment.setCreatedAt(createdAt);
        paymentRepository.save(payment);
    }

    private void seedMaintenance(User agent, User maint) {
        issue("ISS-SEED1", "TRN-101", null, maint, "TRAIN",
                "Door sensor on coach 3 intermittently failing to close during peak hours.", "OPEN", "HIGH",
                LocalDateTime.now().minusDays(2), null);
        issue("ISS-SEED2", null, "STN-AND", agent, "LIFT_ESCALATOR",
                "Escalator 2 at Andheri is making a loud grinding noise.", "IN_PROGRESS", "MEDIUM",
                LocalDateTime.now().minusHours(5), null);
        issue("ISS-SEED3", "TRN-202", null, maint, "SIGNAL",
                "Signal interlocking fault reported near Bandra Kurla Complex; auto-reset. Awaiting inspection.", "RESOLVED", "HIGH",
                LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(9));
        issue("ISS-SEED4", null, "STN-CSM", agent, "FACILITY",
                "Passenger waiting shelter light is out on platform 1.", "OPEN", "LOW",
                LocalDateTime.now().minusDays(1), null);
    }

    private void issue(String id, String trainId, String stationId, User reporter, String type,
                       String description, String status, String priority, LocalDateTime createdAt, LocalDateTime resolvedAt) {
        MaintenanceIssue issue = new MaintenanceIssue();
        issue.setIssueId(id);
        if (trainId != null) {
            issue.setTrain(trainRepository.findById(trainId).orElseThrow());
        }
        if (stationId != null) {
            issue.setStation(stationRepository.findById(stationId).orElseThrow());
        }
        issue.setReportedBy(reporter);
        issue.setIssueType(type);
        issue.setDescription(description);
        issue.setStatus(status);
        issue.setPriority(priority);
        issue.setCreatedAt(createdAt);
        issue.setResolvedAt(resolvedAt);
        maintenanceIssueRepository.save(issue);
    }
}