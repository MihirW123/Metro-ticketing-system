package com.metro.ticketing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void testUnauthenticatedRequestsToProtectedEndpointsReturn401() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/users/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/users/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/stations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/trains")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/routes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/schedules")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/fares")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tickets/TKT-1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/cards/CARD-SEED1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/payments/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/maintenance/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/employee/hire")).andExpect(status().isUnauthorized());
    }

    @Test
    void testInvalidCredentialsReturn401() throws Exception {
        mockMvc.perform(get("/api/stations").with(httpBasic("priya", "WrongPassword!")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users").with(httpBasic("admin", "WrongPassword!")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testForbiddenWhenNonAdminAccessesAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/users").with(httpBasic("priya", "Passw0rd!")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/users").with(httpBasic("priya", "Passw0rd!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"Test\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/1").with(httpBasic("priya", "Passw0rd!")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/users/1").with(httpBasic("priya", "Passw0rd!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"Test\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/users/1").with(httpBasic("priya", "Passw0rd!")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/stations").with(httpBasic("priya", "Passw0rd!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stationCode\": \"TST\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/employee/hire").with(httpBasic("priya", "Passw0rd!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"Test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAdminCanAccessUserManagement() throws Exception {
        mockMvc.perform(get("/api/users").with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk());

        String uniqueContact = "admin_managed_" + UUID.randomUUID().toString().substring(0, 8);
        String createPayload = """
                {
                  "firstName": "Managed",
                  "lastName": "User",
                  "contact": "%s",
                  "password": "Passw0rd!",
                  "role": "PASSENGER"
                }
                """.formatted(uniqueContact);

        String responseContent = mockMvc.perform(post("/api/users").with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.contact").value(uniqueContact))
                .andReturn().getResponse().getContentAsString();

        String createdUserId = com.jayway.jsonpath.JsonPath.read(responseContent, "$.userId");

        mockMvc.perform(get("/api/users/" + createdUserId).with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact").value(uniqueContact));

        String updatePayload = """
                {
                  "firstName": "ManagedUpdated"
                }
                """;
        mockMvc.perform(put("/api/users/" + createdUserId).with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("ManagedUpdated"));

        mockMvc.perform(delete("/api/users/" + createdUserId).with(httpBasic("admin", "admin123")))
                .andExpect(status().isNoContent());
    }

    @Test
    void testAuthenticatedPassengerCanAccessGeneralEndpoints() throws Exception {
        mockMvc.perform(get("/api/stations").with(httpBasic("priya", "Passw0rd!")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/trains").with(httpBasic("priya", "Passw0rd!")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("priya", "Passw0rd!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact").value("priya"));
    }

    @Test
    void testPublicPassengerRegistrationAccessibleWithoutCredentials() throws Exception {
        String uniqueContact = "passenger_" + UUID.randomUUID().toString().substring(0, 8);
        String payload = """
                {
                  "firstName": "Public",
                  "lastName": "User",
                  "contact": "%s",
                  "password": "Passw0rd!"
                }
                """.formatted(uniqueContact);

        mockMvc.perform(post("/api/auth/passenger/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.contact").value(uniqueContact))
                .andExpect(jsonPath("$.role").value("PASSENGER"));
    }

    @Test
    void testOpenApiSpecSecuritySchemeAndPublicEndpointExemption() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"))
                .andExpect(jsonPath("$.paths['/api/auth/passenger/register'].post.security", hasSize(0)))
                .andExpect(jsonPath("$.paths['/api/users'].get.security[0].basicAuth").exists())
                .andExpect(jsonPath("$.paths['/api/users'].post.security[0].basicAuth").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}'].get.security[0].basicAuth").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}'].put.security[0].basicAuth").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}'].delete.security[0].basicAuth").exists());
    }
}
