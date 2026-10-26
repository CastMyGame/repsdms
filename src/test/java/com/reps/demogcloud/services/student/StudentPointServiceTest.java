package com.reps.demogcloud.services.student;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StudentPointServiceTest {
    final StudentPointService service=new StudentPointService();
    @Test void allAmountsAndMissingIdentitiesAreRetired() {
        for(Integer amount:new Integer[]{null,-1,0,1,Integer.MAX_VALUE}) {
            assertEquals(410,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.addPoints(null,amount)).getStatusCode().value());
            assertEquals(410,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.deletePoints(null,amount)).getStatusCode().value());
            assertEquals(410,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.transferPoints(null,null,amount)).getStatusCode().value());
        }
    }
    @Test void concurrentAndDuplicateRequestsCannotChangeAnyBalance() {
        java.util.stream.IntStream.range(0,100).parallel().forEach(i -> assertEquals(410,
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.transferPoints("synthetic-a","synthetic-b",2)).getStatusCode().value()));
    }
}
