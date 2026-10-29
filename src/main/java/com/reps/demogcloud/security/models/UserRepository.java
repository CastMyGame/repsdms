package com.reps.demogcloud.security.models;

import com.reps.demogcloud.security.models.UserModel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends UserInsertOnlyRepository<UserModel>, MongoRepository<UserModel,String> {
    @org.springframework.data.mongodb.repository.Query("{'_id': ?0, 'stripeCustomerId': ?1, 'stripeSubscriptionId': ?2, 'enabled': {'$ne': false}}")
    @org.springframework.data.mongodb.repository.Update("{'$set': {'paid': true, 'stripeStatus': 'active', 'lastPaymentAt': ?3}}")
    long confirmExistingBilling(String id, String customerId, String subscriptionId, java.time.Instant paidAt);

    @org.springframework.data.mongodb.repository.Query("{'_id': ?0, 'tenantSchoolId': ?1, 'stripeCustomerId': ?2, 'stripeSubscriptionId': ?3, 'enabled': {'$ne': false}}")
    @org.springframework.data.mongodb.repository.Update("{'$set': {'paid': true, 'stripeStatus': 'active', 'lastPaymentAt': ?4}}")
    long confirmCanonicalBilling(String id,String tenant,String customer,String subscription,java.time.Instant paidAt);
    List<UserModel> findBySchool(String school);
UserModel findByUsername(String username);
UserModel findByUsernameIgnoreCase(String username);
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
