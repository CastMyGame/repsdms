package com.reps.demogcloud.services.currency;
import com.mongodb.MongoException;
import com.reps.demogcloud.services.UserContextService;
import com.reps.demogcloud.services.email.RequiredNotificationService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CurrencyTransactionsTest {
    @Test void standaloneAndMissingSessionsFailClosedBeforeReceiptOrMutation() {
        for(Document hello:List.of(new Document("maxWireVersion",17).append("logicalSessionTimeoutMinutes",30),new Document("setName","rs0").append("maxWireVersion",17))) {
            var mongo=mock(MongoTemplate.class);when(mongo.getMongoDatabaseFactory()).thenReturn(mock(MongoDatabaseFactory.class));
            when(mongo.executeCommand(any(Document.class))).thenReturn(hello);
            var users=mock(UserContextService.class);var notifications=mock(RequiredNotificationService.class);
            var service=new CurrencyTransactions(mongo,users,notifications);
            @SuppressWarnings("unchecked") Consumer<CurrencyOperation> work=mock(Consumer.class);
            assertEquals(503,assertThrows(ResponseStatusException.class,()->service.execute("AWARD",List.of(),work)).getStatusCode().value());
            verifyNoInteractions(work,users,notifications);verify(mongo,never()).insert(any(Object.class));
        }
    }
    @Test void unknownCommitIsDistinctFromTransientTransactionFailure() {
        var unknown=new MongoException(91,"synthetic");unknown.addLabel("UnknownTransactionCommitResult");
        assertTrue(CurrencyTransactions.label(new RuntimeException(unknown),"UnknownTransactionCommitResult"));
        assertFalse(CurrencyTransactions.label(unknown,"TransientTransactionError"));
    }
}
