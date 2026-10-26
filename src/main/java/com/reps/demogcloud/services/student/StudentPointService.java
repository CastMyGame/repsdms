package com.reps.demogcloud.services.student;
import com.reps.demogcloud.models.student.Student;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;
/** Retired legacy points. Historical values are retained; this is not REPS currency. */
@Service
public class StudentPointService {
    private ResponseStatusException retired() { return new ResponseStatusException(HttpStatus.GONE,"Legacy points mutations are retired"); }
    public Student addPoints(String email,Integer points) { throw retired(); }
    public Student deletePoints(String email,Integer points) { throw retired(); }
    public List<Student> transferPoints(String from,String to,Integer points) { throw retired(); }
}
