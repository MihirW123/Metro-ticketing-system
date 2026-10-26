package com.metro.ticketing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.metro.ticketing.entity.Role;
import com.metro.ticketing.entity.Station;
import com.metro.ticketing.entity.User;
import com.metro.ticketing.exception.GlobalExceptionHandler;
import com.metro.ticketing.repository.StationRepository;
import com.metro.ticketing.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StationRepository stationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void testCreateUserPassengerGeneratesPassPrefix() throws Exception {
        when(passwordEncoder.encode("Password@123")).thenReturn("hashed-pwd");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String payload = """
                {
                  "firstName": "Aarav",
                  "lastName": "Sharma",
                  "password": "Password@123",
                  "role": "PASSENGER",
                  "contact": "+919820011223"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(matchesPattern("^PASS-[0-9a-fA-F-]{36}$")))
                .andExpect(jsonPath("$.role").value("PASSENGER"))
                .andExpect(jsonPath("$.contact").value("+919820011223"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void testCreateUserAdminGeneratesAdminPrefix() throws Exception {
        when(passwordEncoder.encode("Password@123")).thenReturn("hashed-pwd");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String payload = """
                {
                  "firstName": "Admin",
                  "lastName": "User",
                  "password": "Password@123",
                  "role": "ADMIN",
                  "contact": "+919820011224"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(matchesPattern("^ADMIN-[0-9a-fA-F-]{36}$")))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void testCreateUserTicketAgentGeneratesEmpPrefix() throws Exception {
        when(passwordEncoder.encode("Password@123")).thenReturn("hashed-pwd");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String payload = """
                {
                  "firstName": "Ticket",
                  "lastName": "Agent",
                  "password": "Password@123",
                  "role": "TICKET_AGENT",
                  "contact": "+919820011225"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(matchesPattern("^EMP-[0-9a-fA-F-]{36}$")))
                .andExpect(jsonPath("$.role").value("TICKET_AGENT"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void testCreateUserMaintenanceStaffGeneratesEmpPrefix() throws Exception {
        when(passwordEncoder.encode("Password@123")).thenReturn("hashed-pwd");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String payload = """
                {
                  "firstName": "Maintenance",
                  "lastName": "Staff",
                  "password": "Password@123",
                  "role": "MAINTENANCE_STAFF",
                  "contact": "+919820011226"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(matchesPattern("^EMP-[0-9a-fA-F-]{36}$")))
                .andExpect(jsonPath("$.role").value("MAINTENANCE_STAFF"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void testCreateUserIgnoresClientSuppliedUserId() throws Exception {
        when(passwordEncoder.encode("Password@123")).thenReturn("hashed-pwd");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String payload = """
                {
                  "userId": "CLIENT-PROVIDED-ID",
                  "firstName": "Passenger",
                  "lastName": "Test",
                  "password": "Password@123",
                  "role": "PASSENGER",
                  "contact": "+919820011227"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(matchesPattern("^PASS-[0-9a-fA-F-]{36}$")))
                .andExpect(jsonPath("$.userId").value(not("CLIENT-PROVIDED-ID")));
    }

    @Test
    void testCreateUserWithIsActiveFalsePreserved() throws Exception {
        when(passwordEncoder.encode("Password@123")).thenReturn("hashed-pwd");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String payload = """
                {
                  "firstName": "Inactive",
                  "lastName": "User",
                  "password": "Password@123",
                  "role": "PASSENGER",
                  "contact": "+919820011228",
                  "isActive": false
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void testCreateUserWithStationMapping() throws Exception {
        Station station = new Station();
        station.setStationId("STN-VSV");
        station.setStationCode("VSV");
        when(stationRepository.findById("STN-VSV")).thenReturn(Optional.of(station));
        when(passwordEncoder.encode("Password@123")).thenReturn("hashed-pwd");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String payload = """
                {
                  "firstName": "Station",
                  "lastName": "Agent",
                  "password": "Password@123",
                  "role": "TICKET_AGENT",
                  "contact": "+919820011229",
                  "stationId": "STN-VSV"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.station.stationId").value("STN-VSV"));
    }

    @Test
    void testCreateUserWithInvalidStationReturnsBadRequest() throws Exception {
        when(stationRepository.findById("STN-UNKNOWN")).thenReturn(Optional.empty());

        String payload = """
                {
                  "firstName": "Station",
                  "lastName": "Agent",
                  "password": "Password@123",
                  "role": "TICKET_AGENT",
                  "contact": "+919820011230",
                  "stationId": "STN-UNKNOWN"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Station not found with id: STN-UNKNOWN"));
    }

    @Test
    void testCreateUserDuplicateContactReturnsBadRequest() throws Exception {
        User existing = new User();
        existing.setContact("+919820011231");
        when(userRepository.findByContact("+919820011231")).thenReturn(Optional.of(existing));

        String payload = """
                {
                  "firstName": "Duplicate",
                  "lastName": "User",
                  "password": "Password@123",
                  "role": "PASSENGER",
                  "contact": "+919820011231"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User with this contact already exists."));
    }

    @Test
    void testGetAllUsersReturnsList() throws Exception {
        User u1 = new User();
        u1.setUserId("PASS-1");
        u1.setFirstName("First");
        u1.setLastName("User");
        u1.setRole(Role.PASSENGER);
        u1.setContact("+919820011232");

        User u2 = new User();
        u2.setUserId("ADMIN-2");
        u2.setFirstName("Second");
        u2.setLastName("User");
        u2.setRole(Role.ADMIN);
        u2.setContact("+919820011233");

        when(userRepository.findAll()).thenReturn(List.of(u1, u2));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value("PASS-1"))
                .andExpect(jsonPath("$[1].userId").value("ADMIN-2"));
    }

    @Test
    void testGetUserByIdSuccess() throws Exception {
        User user = new User();
        user.setUserId("PASS-1234");
        user.setFirstName("Found");
        user.setLastName("User");
        user.setRole(Role.PASSENGER);
        user.setContact("+919820011234");

        when(userRepository.findById("PASS-1234")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/PASS-1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("PASS-1234"))
                .andExpect(jsonPath("$.firstName").value("Found"));
    }

    @Test
    void testGetUserByIdNotFound() throws Exception {
        when(userRepository.findById("MISSING-ID")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/MISSING-ID"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found with id: MISSING-ID"));
    }

    @Test
    void testUpdateUserPreservesIdAndUpdatesFields() throws Exception {
        User existing = new User();
        existing.setUserId("EMP-ORIGINAL-ID");
        existing.setFirstName("OldFirst");
        existing.setLastName("OldLast");
        existing.setPassword("original-hashed-password");
        existing.setRole(Role.TICKET_AGENT);
        existing.setContact("+919820011235");
        existing.setIsActive(false);

        when(userRepository.findById("EMP-ORIGINAL-ID")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String updatePayload = """
                {
                  "firstName": "NewFirst",
                  "isActive": true
                }
                """;

        mockMvc.perform(put("/api/users/EMP-ORIGINAL-ID")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("EMP-ORIGINAL-ID"))
                .andExpect(jsonPath("$.firstName").value("NewFirst"))
                .andExpect(jsonPath("$.lastName").value("OldLast"))
                .andExpect(jsonPath("$.isActive").value(true));

        assertEquals("original-hashed-password", existing.getPassword());
    }

    @Test
    void testUpdateUserWithNewPasswordHashesPassword() throws Exception {
        User existing = new User();
        existing.setUserId("PASS-UPDATE-PWD");
        existing.setPassword("old-hashed-password");
        existing.setRole(Role.PASSENGER);
        existing.setContact("+919820011236");

        when(userRepository.findById("PASS-UPDATE-PWD")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("NewPassword@123")).thenReturn("new-hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String updatePayload = """
                {
                  "password": "NewPassword@123"
                }
                """;

        mockMvc.perform(put("/api/users/PASS-UPDATE-PWD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("PASS-UPDATE-PWD"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("new-hashed-password", captor.getValue().getPassword());
    }

    @Test
    void testUpdateUserNotFoundReturns404() throws Exception {
        when(userRepository.findById("NON-EXISTENT")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/users/NON-EXISTENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"Test\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found with id: NON-EXISTENT"));
    }

    @Test
    void testDeleteUserSuccess() throws Exception {
        User user = new User();
        user.setUserId("PASS-DELETE-ME");
        when(userRepository.findById("PASS-DELETE-ME")).thenReturn(Optional.of(user));

        mockMvc.perform(delete("/api/users/PASS-DELETE-ME"))
                .andExpect(status().isNoContent());

        verify(userRepository).delete(user);
    }

    @Test
    void testDeleteUserNotFoundReturns404() throws Exception {
        when(userRepository.findById("NON-EXISTENT")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/users/NON-EXISTENT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found with id: NON-EXISTENT"));
    }

    @Test
    void testEntityPrePersistGeneratesIdAndDefaults() {
        User passenger = new User();
        passenger.setRole(Role.PASSENGER);
        passenger.onCreate();

        assertNotNull(passenger.getUserId());
        assertTrue(passenger.getUserId().startsWith("PASS-"));
        assertNotNull(passenger.getRegistrationDate());
        assertEquals(LocalDate.now(), passenger.getRegistrationDate());
        assertTrue(passenger.getIsActive());

        User admin = new User();
        admin.setRole(Role.ADMIN);
        admin.onCreate();
        assertTrue(admin.getUserId().startsWith("ADMIN-"));

        User agent = new User();
        agent.setRole(Role.TICKET_AGENT);
        agent.onCreate();
        assertTrue(agent.getUserId().startsWith("EMP-"));

        User maint = new User();
        maint.setRole(Role.MAINTENANCE_STAFF);
        maint.onCreate();
        assertTrue(maint.getUserId().startsWith("EMP-"));

        User preserved = new User();
        preserved.setUserId("EXISTING-ID");
        preserved.setIsActive(false);
        preserved.onCreate();
        assertEquals("EXISTING-ID", preserved.getUserId());
        assertFalse(preserved.getIsActive());
    }
}
