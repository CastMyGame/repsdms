package com.reps.demogcloud.services.tenancy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
class IsolatedCanonicalActivationGuardTest {
 @Test void hostedMarkersRejectEveryPreparationSwitchAndProfile(){
  for(String marker:java.util.List.of("GAE_ENV","GAE_APPLICATION","GAE_SERVICE","K_SERVICE","K_REVISION")){
   for(String part:java.util.List.of("core","domain","currency","email"))assertThrows(IllegalStateException.class,()->new TenantPreparationGuard(new MockEnvironment().withProperty(marker,"synthetic-host").withProperty("reps.tenancy."+part+"-preparation-enabled","true")).rejectPrematureActivation());
   var env=new MockEnvironment().withProperty(marker,"synthetic-host");env.setActiveProfiles("tenant-isolated-verification");assertThrows(IllegalStateException.class,()->new TenantPreparationGuard(env).rejectPrematureActivation());
  }
 }
 @Test void ordinaryDeploymentProfilesCannotActivatePreparation(){
  for(String profile:java.util.List.of("dev","prod","production")){var env=new MockEnvironment().withProperty("reps.tenancy.core-preparation-enabled","true");env.setActiveProfiles(profile,"tenant-isolated-verification");assertThrows(IllegalStateException.class,()->new TenantPreparationGuard(env).rejectPrematureActivation());}
 }
 @Test void productionGateRemainsFalseOnly(){
  for(String value:java.util.List.of("true","TRUE","invalid",""))assertThrows(IllegalStateException.class,()->new TenantPreparationGuard(new MockEnvironment().withProperty(TenantPreparationGuard.PROPERTY,value)).rejectPrematureActivation());
  assertDoesNotThrow(()->new TenantPreparationGuard(new MockEnvironment()).rejectPrematureActivation());
 }
}
