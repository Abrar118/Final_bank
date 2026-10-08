package io.github.abrar118.matbank.service;

import io.github.abrar118.matbank.TestBank;
import io.github.abrar118.matbank.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportServiceTest {

    @TempDir
    Path dir;

    TestBank bank;
    SupportService support;
    User client;

    @BeforeEach
    void setUp() {
        bank = TestBank.create(dir);
        support = bank.ctx.support();
        client = bank.client("Rafi Chowdhury", "rafi@test.bank", 0, 0);
    }

    @Test
    void visitorsAndClientsCanMessageAdmins() {
        support.sendMessage(null, "Curious Visitor", "visitor@example.com", "Do you have student accounts?");
        support.sendMessage(client, null, null, "Please raise my limit");

        var messages = support.messages(bank.admin);
        assertThat(messages).hasSize(2);
        assertThat(messages).extracting(m -> m.senderName()).containsExactlyInAnyOrder("Curious Visitor", "Rafi Chowdhury");
        assertThat(support.unreadCount()).isEqualTo(2);

        support.markRead(bank.admin, messages.getFirst().id(), true);
        assertThat(support.unreadCount()).isEqualTo(1);
    }

    @Test
    void visitorsMustGiveAName() {
        assertThatThrownBy(() -> support.sendMessage(null, " ", null, "Hi")).hasMessageContaining("Your name");
        assertThatThrownBy(() -> support.sendMessage(null, "Rafi", "not-email", "Hi")).hasMessageContaining("email");
    }

    @Test
    void feedbackNeedsASignedInUserAndAValidRating() {
        assertThatThrownBy(() -> support.submitFeedback(null, 5, "Great")).hasMessageContaining("Sign in");
        assertThatThrownBy(() -> support.submitFeedback(client, 6, "Great")).hasMessageContaining("1 to 5");

        support.submitFeedback(client, 5, "Great");
        support.submitFeedback(client, 4, "Good");
        assertThat(support.averageRating()).hasValue(4.5);
        assertThat(support.feedback(bank.admin)).hasSize(2);
    }

    @Test
    void onlyAdminsReadTheInbox() {
        assertThatThrownBy(() -> support.messages(client)).hasMessageContaining("Only admins");
    }
}
