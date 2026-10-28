package com.reps.demogcloud.services.currency;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class CurrencyReplayBoundaryTest {
    @Test void absentByDefault(){new ApplicationContextRunner().withUserConfiguration(CanonicalCurrencyTransactions.class).run(c->{assertNull(c.getStartupFailure());assertTrue(c.getBeansOfType(CanonicalCurrencyTransactions.class).isEmpty());});}
    @Test void directUseCannotBypassIsolatedProfile(){var mongo=org.mockito.Mockito.mock(org.springframework.data.mongodb.core.MongoTemplate.class);org.mockito.Mockito.when(mongo.getMongoDatabaseFactory()).thenReturn(org.mockito.Mockito.mock(org.springframework.data.mongodb.MongoDatabaseFactory.class));var service=new CanonicalCurrencyTransactions(mongo,null,null,new MockEnvironment());assertThrows(IllegalStateException.class,()->service.execute("AWARD",null,null));}
}
