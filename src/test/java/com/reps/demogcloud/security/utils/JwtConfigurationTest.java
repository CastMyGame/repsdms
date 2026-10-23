package com.reps.demogcloud.security.utils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.env.PropertySource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class JwtConfigurationTest {
 MockEnvironment profile(String profile)throws Exception{
  var env=new MockEnvironment();for(String file:List.of("application.properties","application-"+profile+".properties")) {
   var properties=PropertiesLoaderUtils.loadProperties(new org.springframework.core.io.FileSystemResource("src/main/resources/"+file));
   for(String name:properties.stringPropertyNames()) env.setProperty(name,properties.getProperty(name));
  }return env;
 }
 @Test void devAndProdUseSeparatePinnedSecretReferences()throws Exception{
  var dev=profile("dev");dev.setProperty("sm@reps-dev-jwt-signing-key/1",JwtTestSupport.key());assertEquals(JwtTestSupport.key(),dev.getProperty("reps.auth.jwt.signing-key"));
  var prod=profile("prod");prod.setProperty("REPS_JWT_SIGNING_KEY_VERSION","7");prod.setProperty("sm@reps-prod-jwt-signing-key/7",JwtTestSupport.key());assertEquals(JwtTestSupport.key(),prod.getProperty("reps.auth.jwt.signing-key"));
  assertNotEquals(dev.getPropertySources().get("mockProperties").getProperty("reps.auth.jwt.signing-key"),prod.getPropertySources().get("mockProperties").getProperty("reps.auth.jwt.signing-key"));
 }
 @Test void localOverrideDoesNotResolveSecretManagerFallback()throws Exception{
  var env=profile("dev");env.setProperty("REPS_JWT_SIGNING_KEY",JwtTestSupport.key());
  env.getPropertySources().addFirst(new PropertySource<Object>("forbid-cloud") {public Object getProperty(String name){if(name.startsWith("sm@"))throw new AssertionError("Secret Manager should not be consulted");return null;}});
  assertDoesNotThrow(()->new JwtUtils(env,JwtTestSupport.store()));
 }
 @Test void missingKeyPreventsSpringBeanStartupWithoutGeneratedFallback(){
  var context=new AnnotationConfigApplicationContext();context.registerBean(JwtUtils.class,()->new JwtUtils(new MockEnvironment(),JwtTestSupport.store()));
  try {assertThrows(org.springframework.beans.BeansException.class,context::refresh);}finally{context.close();}
 }
 @Test void mutableLatestVersionIsRejectedWithoutLeakingInput(){
  var env=JwtTestSupport.environment().withProperty("reps.auth.jwt.signing-key-version","latest");
  var error=assertThrows(IllegalStateException.class,()->new JwtUtils(env,JwtTestSupport.store()));assertNull(error.getCause());assertFalse(error.getMessage().contains(JwtTestSupport.key()));
 }
}
