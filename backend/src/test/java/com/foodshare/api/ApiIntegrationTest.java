package com.foodshare.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

// The whole application (security filter chain, controllers, services, in-memory H2) through HTTP calls.
// Covers: role authorization, registration endpoint + its IP throttle, and the address privacy rule.
// Every test registers its OWN users with unique names and its OWN client IP, so the tests do not disturb each other
// (the registration limit is per IP).
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:apitest;DB_CLOSE_DELAY=-1",
        "app.rate-limit.register-per-ip-per-hour=3"
})
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired MockMvc mvc;

    private static final AtomicInteger counter = new AtomicInteger();

    // ---------- helpers ----------

    private static String uniqueName(String prefix) { return prefix + counter.incrementAndGet(); }

    private static String ipFor(int n) { return "10.1." + (n / 250) + "." + (n % 250); }

    private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder b, String ip) {
        return b.with(r -> { r.setRemoteAddr(ip); return r; });
    }

    private ResultActions register(String username, String role, String ip) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"secret123\",\"fullName\":\"Test " + username
                + "\",\"role\":\"" + role + "\"}";
        return mvc.perform(from(post("/api/auth/register"), ip).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // registers a user (from a fresh IP) and returns its token
    private String tokenOf(String prefix, String role) throws Exception {
        String ip = ipFor(counter.incrementAndGet() + 1000);
        String json = register(uniqueName(prefix), role, ip).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token);
    }

    private String donationJson(String address, String area, String notes) {
        Instant now = Instant.now();
        return "{\"foodName\":\"Dal\",\"category\":\"COOKED_MEALS\",\"quantity\":5,\"storage\":\"REFRIGERATED\","
                + "\"area\":\"" + area + "\",\"address\":\"" + address + "\",\"notes\":\"" + notes + "\","
                + "\"allergens\":\"milk\",\"preparedAt\":\"" + now.minus(1, ChronoUnit.HOURS) + "\","
                + "\"expiresAt\":\"" + now.plus(5, ChronoUnit.HOURS) + "\"}";
    }

    private long createDonation(String donorToken, String address, String area, String notes) throws Exception {
        String json = mvc.perform(auth(post("/api/donations"), donorToken).contentType(MediaType.APPLICATION_JSON)
                        .content(donationJson(address, area, notes)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    // ---------- authentication ----------

    @Test
    void protectedUrlsNeedALogin() throws Exception {
        mvc.perform(get("/api/donations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/donations/claim-history")).andExpect(status().isUnauthorized());
    }

    @Test
    void aGarbageTokenIsTreatedLikeNoToken() throws Exception {
        mvc.perform(auth(get("/api/donations"), "not.a.real.token")).andExpect(status().isUnauthorized());
    }

    // ---------- registration ----------

    @Test
    void registrationWorksAndRejectsDuplicatesAndAdmin() throws Exception {
        String name = uniqueName("reg");
        String ip = ipFor(counter.incrementAndGet() + 2000);
        register(name, "DONOR", ip).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("DONOR"));
        register(name, "RECIPIENT", ip).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username already taken"));
        register(uniqueName("reg"), "ADMIN", ip).andExpect(status().isBadRequest());   // nobody can make themselves admin
    }

    @Test
    void registrationIsThrottledPerIpAndOtherIpsAreNotAffected() throws Exception {
        String ip = ipFor(counter.incrementAndGet() + 3000);
        for (int i = 0; i < 3; i++) register(uniqueName("flood"), "DONOR", ip).andExpect(status().isOk());

        register(uniqueName("flood"), "DONOR", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("registration")));

        register(uniqueName("flood"), "DONOR", ipFor(counter.incrementAndGet() + 4000)).andExpect(status().isOk());
    }

    // ---------- role authorization ----------

    @Test
    void donorCannotUseRecipientEndpoints() throws Exception {
        String donor = tokenOf("donor", "DONOR");
        mvc.perform(auth(get("/api/donations/claimed"), donor)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/donations/claim-history"), donor)).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/donations/1/claim"), donor)).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/donations/1/complete"), donor)).andExpect(status().isForbidden());
    }

    @Test
    void recipientCannotUseDonorEndpoints() throws Exception {
        String recipient = tokenOf("recip", "RECIPIENT");
        mvc.perform(auth(post("/api/donations"), recipient).contentType(MediaType.APPLICATION_JSON)
                .content(donationJson("a", "b", "c"))).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/donations/mine"), recipient)).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/donations/1/cancel"), recipient)).andExpect(status().isForbidden());
    }

    @Test
    void nobodyButAdminsCanUseAdminEndpoints() throws Exception {
        String donor = tokenOf("donor", "DONOR");
        String recipient = tokenOf("recip", "RECIPIENT");
        for (String token : new String[]{donor, recipient}) {
            mvc.perform(auth(get("/api/admin/dashboard"), token)).andExpect(status().isForbidden());
            mvc.perform(auth(get("/api/admin/users"), token)).andExpect(status().isForbidden());
            mvc.perform(auth(get("/api/admin/donations"), token)).andExpect(status().isForbidden());
        }
    }

    // ---------- address privacy ----------

    @Test
    void publicListingShowsTheAreaButNeverTheExactAddressOrNotes() throws Exception {
        String donor = tokenOf("donor", "DONOR");
        String recipient = tokenOf("recip", "RECIPIENT");
        String address = "SecretLane" + counter.incrementAndGet();
        long id = createDonation(donor, address, "Vijay Nagar, Indore", "RingBellAtFlat3");

        String body = mvc.perform(auth(get("/api/donations?size=50"), recipient))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Vijay Nagar, Indore");
        assertThat(body).doesNotContain(address).doesNotContain("RingBellAtFlat3").doesNotContain("\"address\"").doesNotContain("\"notes\"");
        assertThat((Iterable<?>) JsonPath.read(body, "$.items[?(@.id==" + id + ")]")).hasSize(1);
    }

    @Test
    void theExactAddressIsGivenOnlyToTheDonorAndToTheRecipientWhoClaimed() throws Exception {
        String donor = tokenOf("donor", "DONOR");
        String winner = tokenOf("winner", "RECIPIENT");
        String other = tokenOf("other", "RECIPIENT");
        String address = "SecretLane" + counter.incrementAndGet();
        long id = createDonation(donor, address, "Palasia, Indore", "RingBellAtFlat3");

        // the donor sees their own full listing
        mvc.perform(auth(get("/api/donations/mine"), donor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].address").value(address));

        // before claiming nobody else has it
        mvc.perform(auth(get("/api/donations/claimed"), winner)).andExpect(jsonPath("$.items").isEmpty());

        // the claiming recipient gets it in the claim response and in "my pickups"
        mvc.perform(auth(post("/api/donations/" + id + "/claim"), winner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.address").value(address))
                .andExpect(jsonPath("$.notes").value("RingBellAtFlat3"));
        mvc.perform(auth(get("/api/donations/claimed"), winner))
                .andExpect(jsonPath("$.items[0].address").value(address));

        // another recipient still cannot get it: not in "my pickups", not in the public list, cannot claim it again
        mvc.perform(auth(get("/api/donations/claimed"), other)).andExpect(jsonPath("$.items").isEmpty());
        String publicBody = mvc.perform(auth(get("/api/donations?size=50"), other)).andReturn().getResponse().getContentAsString();
        assertThat(publicBody).doesNotContain(address);
        mvc.perform(auth(post("/api/donations/" + id + "/claim"), other)).andExpect(status().isConflict());
    }

    @Test
    void creatingADonationWithoutAnAreaIsRejected() throws Exception {
        String donor = tokenOf("donor", "DONOR");
        mvc.perform(auth(post("/api/donations"), donor).contentType(MediaType.APPLICATION_JSON)
                        .content(donationJson("12 Some Street", "", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void claimGeneratesOtpAndAllowsVerificationAndFeedback() throws Exception {
        String donor = tokenOf("dnr", "DONOR");
        String recipient = tokenOf("rec", "RECIPIENT");
        long id = createDonation(donor, "123 Main St", "Vijay Nagar", "Call on arrival");

        // Recipient claims food -> gets 6-digit OTP
        String claimJson = mvc.perform(auth(post("/api/donations/" + id + "/claim"), recipient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickupOtp").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String otp = JsonPath.read(claimJson, "$.pickupOtp");
        assertThat(otp).matches("^[0-9]{6}$");

        // Wrong OTP is rejected
        mvc.perform(auth(post("/api/donations/" + id + "/verify-pickup-otp"), donor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"000000\"}"))
                .andExpect(status().isBadRequest());

        // Correct OTP succeeds and marks COMPLETED
        mvc.perform(auth(post("/api/donations/" + id + "/verify-pickup-otp"), donor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"" + otp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Recipient can submit rating and feedback
        mvc.perform(auth(post("/api/donations/" + id + "/feedback"), recipient)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"feedback\":\"Delicious meal, thank you so much!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.feedback").value("Delicious meal, thank you so much!"));

        // Impact endpoint reflects activity
        mvc.perform(auth(get("/api/donations/impact"), recipient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMealsRescued").isNumber());

        // Map endpoint returns listing coordinates
        mvc.perform(auth(get("/api/donations/map"), recipient))
                .andExpect(status().isOk());
    }
}
