package com.reps.demogcloud.security.models;

import com.reps.demogcloud.security.models.UserModel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<UserModel,String> {
    List<UserModel> findBySchool(String school);
UserModel findByUsername(String username);
Boolean existsByUsername(String username);

    @org.springframework.data.mongodb.repository.Query("{'_id': ?0, '$or': [{'school': ?1}, {'school': null, 'schoolName': ?1}]}")
    @org.springframework.data.mongodb.repository.Update("{'$set': {'roles': ?2}}")
    void updateRolesInSchool(String id, String school, java.util.Set<RoleModel> roles);

    @org.springframework.data.mongodb.repository.Query("{'_id': ?0, '$or': [{'school': ?1}, {'school': null, 'schoolName': ?1}]}")
    @org.springframework.data.mongodb.repository.Update("{'$set': {'enabled': ?2}}")
    void updateEnabledInSchool(String id, String school, boolean enabled);

    @org.springframework.data.mongodb.repository.Query("{'_id': ?0, 'enabled': {'$ne': false}}")
    @org.springframework.data.mongodb.repository.Update("{'$set': {'password': ?1}}")
    long updatePasswordIfEnabled(String id, String encodedPassword);

    @org.springframework.data.mongodb.repository.Query("{'_id': ?0, 'enabled': {'$ne': false}, '$or': [{'school': ?1}, {'school': null, 'schoolName': ?1}]}")
    @org.springframework.data.mongodb.repository.Update("{'$set': {'paid': true, 'stripeCustomerId': ?2, 'stripeSubscriptionId': ?3, 'stripePriceId': ?4, 'stripeStatus': 'active', 'lastPaymentAt': ?5}}")
    void updateBillingIfEnabled(String id, String school, String customer, String subscription, String price, java.time.Instant at);
}
