package com.reps.demogcloud.services.currency;

import com.reps.demogcloud.services.UserContextService;
import com.reps.demogcloud.services.email.RequiredNotificationService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pins the deployed v1 algorithm. No v2 receipt generation or migration is enabled. */
class CurrencyLegacyReceiptCompatibilityTest {
    static String hash(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void historicalReceiptReplaysWithoutExecutingWalletOrNotificationWork() throws Exception {
        var mongo=mock(MongoTemplate.class);var users=mock(UserContextService.class);
        var notifications=mock(RequiredNotificationService.class);
        when(mongo.getMongoDatabaseFactory()).thenReturn(mock(MongoDatabaseFactory.class));
        when(mongo.executeCommand(any(Document.class))).thenReturn(new Document("setName","synthetic").append("logicalSessionTimeoutMinutes",30).append("maxWireVersion",17));
        when(users.getCurrentStaffEmail()).thenReturn("Teacher@example.test");
        when(users.getCurrentUserSchool()).thenReturn("Same School");when(notifications.actionId()).thenReturn("original-action");
        String originalId=hash("teacher@example.test:same school:AWARD:original-action");
        var receipt=new CurrencyOperation();receipt.setId(originalId);receipt.setPayloadHash(hash("{\"amount\":3}"));
        receipt.setCaseIds(List.of("original-case"));receipt.setTeacherBalance(17);
        when(mongo.findById(originalId,CurrencyOperation.class)).thenReturn(receipt);
        var service=new CurrencyTransactions(mongo,users,notifications);
        @SuppressWarnings("unchecked") Consumer<CurrencyOperation> work=mock(Consumer.class);
        assertSame(receipt,service.execute("AWARD",Map.of("amount",3),work));
        assertSame(receipt,service.execute("AWARD",Map.of("amount",3),work));
        assertEquals(List.of("original-case"),receipt.getCaseIds());assertEquals(17,receipt.getTeacherBalance());
        verifyNoInteractions(work);verify(mongo,never()).insert(any(Object.class));verify(mongo,never()).save(any(Object.class));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.execute("AWARD",Map.of("amount",4),work)).getStatusCode().value());
        verifyNoInteractions(work);
    }
}
