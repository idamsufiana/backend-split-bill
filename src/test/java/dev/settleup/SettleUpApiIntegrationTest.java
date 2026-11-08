package dev.settleup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end through HTTP, JSON and the database: a three-person weekend trip.
 * The username "a" gives a 7% service charge (code point 97 → 97 % 10 = 7).
 */
@SpringBootTest(properties = {"app.github-username=a", "spring.cache.type=simple"})
@AutoConfigureMockMvc
class SettleUpApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    long groupId;
    long alice;
    long bob;
    long carol;

    @BeforeEach
    void createTrip() throws Exception {
        JsonNode group = body(postJson("/api/groups", """
                {"name": "Bandung weekend", "currency": "USD", "participants": ["Alice", "Bob", "Carol"]}
                """).andExpect(status().isCreated()).andExpect(header().exists("Location")));
        groupId = group.get("id").asLong();
        alice = group.at("/participants/0/id").asLong();
        bob = group.at("/participants/1/id").asLong();
        carol = group.at("/participants/2/id").asLong();
    }

    @Test
    void fullTripLifecycle() throws Exception {
        // Alice: hotel 300.00 split by nights (Alice 1, Bob 1, Carol 1) → 100 each
        addExpense("""
                {"description": "Hotel", "amount": "300.00", "paid_by": %d, "category": "ACCOMMODATION",
                 "split_type": "SHARES",
                 "allocations": [{"participant_id": %d, "value": 1}, {"participant_id": %d, "value": 1},
                                 {"participant_id": %d, "value": 1}]}
                """.formatted(alice, alice, bob, carol))
                .andExpect(jsonPath("$.shares", hasSize(3)));

        // Bob: dinner 100.00 equal across everyone → 33.34 / 33.33 / 33.33
        addExpense("""
                {"description": "Dinner", "amount": 100, "paid_by": %d, "category": "FOOD"}
                """.formatted(bob))
                .andExpect(jsonPath("$.shares[0].amount").value("33.34"))
                .andExpect(jsonPath("$.split_type").value("EQUAL"));

        // Settlement. Totals: Alice paid 300 owes 133.34 → +166.66
        //                     Bob   paid 100 owes 133.33 → −33.33
        //                     Carol paid   0 owes 133.33 → −133.33
        getJson("/api/groups/" + groupId + "/settlement")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_expenses").value("400.00"))
                .andExpect(jsonPath("$.service_charge_pct").value(7))
                .andExpect(jsonPath("$.service_charge_amount").value("28.00"))
                .andExpect(jsonPath("$.balances[0].net_balance").value("166.66"))
                .andExpect(jsonPath("$.balances[1].net_balance").value("-33.33"))
                .andExpect(jsonPath("$.balances[2].net_balance").value("-133.33"))
                .andExpect(jsonPath("$.transactions", hasSize(2)))
                .andExpect(jsonPath("$.transactions[0].from.name").value("Bob"))
                .andExpect(jsonPath("$.transactions[0].to.name").value("Alice"))
                .andExpect(jsonPath("$.transactions[0].amount").value("33.33"))
                .andExpect(jsonPath("$.transactions[1].from.name").value("Carol"))
                .andExpect(jsonPath("$.transactions[1].amount").value("133.33"))
                .andExpect(jsonPath("$.settled").value(false));

        // Carol pays Alice in full, Bob pays part of his debt.
        recordPayment(carol, alice, "133.33");
        recordPayment(bob, alice, "20.00");

        getJson("/api/groups/" + groupId + "/settlement")
                .andExpect(jsonPath("$.transactions", hasSize(1)))
                .andExpect(jsonPath("$.transactions[0].from.name").value("Bob"))
                .andExpect(jsonPath("$.transactions[0].amount").value("13.33"))
                // payments do not change total spend or the service charge
                .andExpect(jsonPath("$.total_expenses").value("400.00"))
                .andExpect(jsonPath("$.service_charge_amount").value("28.00"));

        recordPayment(bob, alice, "13.33");
        getJson("/api/groups/" + groupId + "/settlement")
                .andExpect(jsonPath("$.settled").value(true))
                .andExpect(jsonPath("$.transactions", hasSize(0)));

        getJson("/api/groups/" + groupId + "/summary/categories")
                .andExpect(jsonPath("$.categories", hasSize(2)))
                .andExpect(jsonPath("$.categories[0].category").value("FOOD"))
                .andExpect(jsonPath("$.categories[0].percentage_of_total").value("25.00"))
                .andExpect(jsonPath("$.categories[1].category").value("ACCOMMODATION"))
                .andExpect(jsonPath("$.categories[1].total").value("300.00"));
    }

    @Test
    void voidedExpensesLeaveBalancesButStayAuditable() throws Exception {
        long id = body(addExpense("""
                {"description": "Taxi", "amount": "60.00", "paid_by": %d, "category": "TRANSPORT"}
                """.formatted(alice))).get("id").asLong();

        mvc.perform(delete("/api/groups/{g}/expenses/{e}", groupId, id)).andExpect(status().isNoContent());

        getJson("/api/groups/" + groupId + "/settlement")
                .andExpect(jsonPath("$.total_expenses").value("0.00"))
                .andExpect(jsonPath("$.settled").value(true));
        getJson("/api/groups/" + groupId + "/expenses").andExpect(jsonPath("$", hasSize(0)));
        getJson("/api/groups/" + groupId + "/expenses?include_voided=true")
                .andExpect(jsonPath("$[0].voided_at").exists());
    }

    @Test
    void domainRuleViolationsAre422() throws Exception {
        postJson("/api/groups/" + groupId + "/expenses", """
                {"description": "Bad split", "amount": "10.00", "paid_by": %d, "split_type": "PERCENTAGE",
                 "allocations": [{"participant_id": %d, "value": 60}, {"participant_id": %d, "value": 30}]}
                """.formatted(alice, alice, bob))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Percentages must sum to 100, got 90"));

        postJson("/api/groups/" + groupId + "/expenses", """
                {"description": "Sub-cent", "amount": "10.001", "paid_by": %d}
                """.formatted(alice))
                .andExpect(status().isUnprocessableEntity());

        postJson("/api/groups/" + groupId + "/payments", """
                {"from_participant_id": %d, "to_participant_id": %d, "amount": "5.00"}
                """.formatted(bob, bob))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void malformedRequestsAre400AndMissingResourcesAre404() throws Exception {
        postJson("/api/groups/" + groupId + "/expenses", """
                {"amount": "10.00"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.description").exists())
                .andExpect(jsonPath("$.errors.paid_by").exists());

        getJson("/api/groups/999999/settlement").andExpect(status().isNotFound());
    }

    @Test
    void participantNamesAreUniqueWithinAGroup() throws Exception {
        postJson("/api/groups/" + groupId + "/participants", """
                {"name": "alice"}
                """).andExpect(status().isUnprocessableEntity());
        postJson("/api/groups/" + groupId + "/participants", """
                {"name": "Dave"}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Dave"));
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions addExpense(String body) throws Exception {
        return postJson("/api/groups/" + groupId + "/expenses", body).andExpect(status().isCreated());
    }

    private void recordPayment(long from, long to, String amount) throws Exception {
        postJson("/api/groups/" + groupId + "/payments", """
                {"from_participant_id": %d, "to_participant_id": %d, "amount": "%s"}
                """.formatted(from, to, amount)).andExpect(status().isCreated());
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions getJson(String path) throws Exception {
        return mvc.perform(get(path).accept(MediaType.APPLICATION_JSON));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
