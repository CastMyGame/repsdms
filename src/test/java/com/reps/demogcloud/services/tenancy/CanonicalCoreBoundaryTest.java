package com.reps.demogcloud.services.tenancy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalCoreBoundaryTest {
    @Test void moduleIsAbsentByDefaultWithoutDatabaseOrActorDependencies(){
        new ApplicationContextRunner().withUserConfiguration(CanonicalCoreWorkflows.class).run(ctx->{
            assertNull(ctx.getStartupFailure());assertEquals(0,ctx.getBeansOfType(CanonicalCoreWorkflows.class).size());
        });
    }
}
