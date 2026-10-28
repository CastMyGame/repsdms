package com.reps.demogcloud.services.tenancy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantRootBoundaryTest {
    @Test void realDomainIntegrationIsAbsentByDefault(){
        new ApplicationContextRunner().withUserConfiguration(CanonicalDomainAccess.class).run(c->{assertNull(c.getStartupFailure());assertEquals(0,c.getBeansOfType(CanonicalDomainAccess.class).size());});
    }
    @Test void preparationCannotBeEnabledOutsideExplicitIsolatedProfile(){
        new ApplicationContextRunner().withUserConfiguration(CanonicalDomainAccess.class)
                .withPropertyValues("reps.tenancy.domain-preparation-enabled=true")
                .withBean(CanonicalAccountContext.class,()->mock(CanonicalAccountContext.class))
                .withBean(CanonicalCoreWorkflows.class,()->mock(CanonicalCoreWorkflows.class))
                .withBean(MongoTemplate.class,()->mock(MongoTemplate.class))
                .run(c->{assertNotNull(c.getStartupFailure());assertTrue(c.getStartupFailure().toString().contains("canonicalDomainAccess"));});
    }
    @Test void currentApplicationCannotReintroduceUnprotectedDirectReplacementWriters()throws Exception{
        try(var files=Files.walk(Path.of("src/main/java"))){
            for(Path file:files.filter(p->p.toString().endsWith(".java")).toList()){
                String text=Files.readString(file);
                if(!file.getFileName().toString().equals("CurrencyTransactions.java"))
                    assertFalse(java.util.regex.Pattern.compile("\\b(?:mongo|mongoTemplate)\\.save\\s*\\(").matcher(text).find(),file.toString());
                for(String unsupported:java.util.List.of(".replaceOne(",".findAndReplace(",".bulkWrite(",".bulkOps(","new BasicUpdate(","AggregationUpdate."))
                    assertFalse(text.contains(unsupported),file+": unsupported writer needs explicit review");
            }
        }
    }
}
