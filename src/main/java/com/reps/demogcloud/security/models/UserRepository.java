package com.reps.demogcloud.security.models;

import com.reps.demogcloud.security.models.UserModel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<UserModel,String> {
UserModel findByUsername(String username);
List<UserModel> findBySchoolName (String schoolName);
Boolean existsByUsername(String username);
}
