package com.reps.demogcloud.security.services;

import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.services.WorkflowAccessService;
import com.reps.demogcloud.services.UserContextService;
import com.reps.demogcloud.services.email.EmailSenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Account setup uses one account-local ticket: atomic completion, bounded guesses, no raw secrets at rest. */
@Service @RequiredArgsConstructor
public class AccountAccessService {
    private final UserRepository users;
    private final MongoTemplate mongo;
    private final PasswordEncoder encoder;
    private final EmailSenderService sender;
    private final Environment environment;
    private final WorkflowAccessService access;
    private final UserContextService context;
    private static final SecureRandom RANDOM=new SecureRandom();
    public record Handoff(String username, String credential, Instant expiresAt, int maximumAttempts) { @Override public String toString() { return "Handoff[redacted]"; } }
    public static String unusablePassword() { byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException("Digest unavailable"); }
    }
    public String publicBaseUrl() {
        return com.reps.demogcloud.services.email.FrontendLinks.origin(environment.getProperty("reps.accounts.public-base-url", ""),
                Arrays.asList(environment.getActiveProfiles()).contains("prod"));
    }

    private boolean student(UserModel user) { return user.getRoles()!=null && user.getRoles().stream().anyMatch(r->r!=null && "STUDENT".equalsIgnoreCase(r.getRole())); }
    private UserModel enabled(UserModel user) { if(user==null || !user.isEnabled()) throw invalid(); return user; }
    private static ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid, expired or unavailable setup credential. Ask for a new one."); }
    private UserModel target(String id) {
        access.requireAdmin();
        var actor=users.findByUsername(context.getCurrentStaffEmail());
        var user=users.findById(id).orElseThrow(()->new AccessDeniedException("Access denied"));
        if(actor==null || actor.getSchool()==null || !actor.getSchool().equalsIgnoreCase(user.getSchool())) throw new AccessDeniedException("Access denied");
        return enabled(user);
    }
    public void inviteStaff(String id) { var user=target(id); if(student(user)) throw new AccessDeniedException("Use student setup controls"); sendInvitation(user); }
    /** Called only after both trusted account and profile have been persisted. Mail failure never rolls them back. */
    public void createdStaff(UserModel user) { if(user!=null && user.isEnabled()) sendInvitation(user); }
    public void requestReset(String email) {
        if(email==null) return;
        var user=users.findByUsername(email.trim().toLowerCase(Locale.ROOT));
        if(user==null || !user.isEnabled() || (student(user) && !user.isPrivateEmailSetupAllowed())) return;
        sendInvitation(user);
    }
    private UserModel issue(UserModel user, String credential, String purpose, int minutes, boolean assisted) {
        String base=publicBaseUrl();
        var ticket=new UserModel.AccessTicket(); ticket.setDigest(digest(credential)); ticket.setPurpose(purpose);
        ticket.setEnvironment(base); ticket.setIssueId(UUID.randomUUID().toString());ticket.setAttempts(5); ticket.setExpiresAt(Instant.now().plusSeconds(minutes*60L));
        var criteria=Criteria.where("_id").is(user.getId()).and("enabled").ne(false);
        // Public reset requests cannot rotate an outstanding ticket more than once a minute.
        if(!assisted) criteria.orOperator(Criteria.where("accessTicket").exists(false),Criteria.where("accessTicket").is(null),Criteria.where("accessTicket.expiresAt").lt(Instant.now().plusSeconds((minutes-1)*60L)));
        var update=new Update().set("accessTicket",ticket).set("invitationExpiresAt",ticket.getExpiresAt()).set("invitationStatus",assisted?"READY_TO_HANDOFF":"PENDING_SEND");
        if(assisted) update.set("passwordSetupRequired",true).set("password",encoder.encode(unusablePassword())).inc("credentialVersion",1);
        return mongo.findAndModify(Query.query(criteria),update,FindAndModifyOptions.options().returnNew(true),UserModel.class);
    }
    private void sendInvitation(UserModel user) {
        String credential=unusablePassword(); UserModel issued;
        try { issued=issue(user,credential,user.isPasswordSetupRequired()?"EMAIL_SETUP":"PASSWORD_RESET",1440,false); }
        catch(IllegalStateException badConfiguration) { mongo.updateFirst(Query.query(Criteria.where("_id").is(user.getId())),new Update().set("invitationStatus","CONFIGURATION_REQUIRED"),UserModel.class); return; }
        if(issued==null) return;
        String state="SENT";
        try {
            // System SMTP, direct recipient only. No Gmail borrowing, translation, CC, or password disclosure.
            sender.sendSystemBulkEmail(user.getUsername(),null,"Set up your REPS password",
                    "Use this single-use link within 24 hours to set your password: <a href=\""+publicBaseUrl()+"/set-password#"+credential+"\">Set password</a>. If you did not request this, contact your school.",null,null);
        } catch(Exception failure) { state=(failure instanceof org.springframework.mail.MailAuthenticationException || failure instanceof org.springframework.mail.MailParseException || failure instanceof org.springframework.mail.MailPreparationException)?"FAILED":"DELIVERY_UNCONFIRMED"; }
        // A late sender must not overwrite a newer ticket or successful completion.
        mongo.updateFirst(Query.query(Criteria.where("_id").is(user.getId()).and("accessTicket.issueId").is(issued.getAccessTicket().getIssueId())),new Update().set("invitationStatus",state),UserModel.class);
    }
    public Handoff studentHandoff(String email, boolean identityVerified, boolean privateEmailAllowed) {
        access.requireStaff();
        var actor=users.findByUsername(context.getCurrentStaffEmail());
        if(actor==null || actor.getRoles()==null || actor.getRoles().stream().noneMatch(r->r!=null && r.getRole()!=null && Set.of("ADMIN","TEACHER").contains(r.getRole().toUpperCase(Locale.ROOT)))) throw new AccessDeniedException("Access denied");
        access.requireStudentAccess(email);
        var user=enabled(users.findByUsername(email.trim().toLowerCase(Locale.ROOT)));
        if(!student(user) || user.getRoles().size()!=1 || actor.getSchool()==null || !actor.getSchool().equalsIgnoreCase(user.getSchool()) || !identityVerified) throw new AccessDeniedException("Verify the student in person before issuing access");
        String alphabet="23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; StringBuilder code=new StringBuilder();
        for(int i=0;i<12;i++)code.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        var issued=issue(user,code.toString(),"SCHOOL_ASSISTED",15,true);
        if(issued==null) throw invalid();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(user.getId()).and("accessTicket.issueId").is(issued.getAccessTicket().getIssueId())),new Update().set("privateEmailSetupAllowed",privateEmailAllowed),UserModel.class);
        String display=code.substring(0,4)+"-"+code.substring(4,8)+"-"+code.substring(8);
        return new Handoff(user.getUsername(),display,issued.getAccessTicket().getExpiresAt(),5);
    }
    public void inviteStudent(String email, boolean identityVerified, boolean privateEmailAllowed) {
        if(!privateEmailAllowed) throw new org.springframework.security.access.AccessDeniedException("A privately controlled mailbox must be confirmed");
        studentHandoff(email,identityVerified,true);
        requestReset(email);
    }
    public java.util.Map<String,Object> studentStatus(String email) {
        access.requireStaff(); access.requireStudentAccess(email);
        var actor=users.findByUsername(context.getCurrentStaffEmail());
        var user=users.findByUsername(email.trim().toLowerCase(Locale.ROOT));
        if(actor==null || actor.getRoles()==null || actor.getRoles().stream().noneMatch(r->r!=null && r.getRole()!=null && Set.of("ADMIN","TEACHER").contains(r.getRole().toUpperCase(Locale.ROOT)))
                || user==null || actor.getSchool()==null || !actor.getSchool().equalsIgnoreCase(user.getSchool())) throw new org.springframework.security.access.AccessDeniedException("Access denied");
        return java.util.Map.of("enabled",user.isEnabled(),"passwordSetupRequired",user.isPasswordSetupRequired(),"invitationStatus",java.util.Objects.toString(user.getInvitationStatus(),"LEGACY_ACCOUNT"));
    }
    public void complete(String username, String credential, String password) {
        if(credential==null || credential.length()>128 || password==null || password.length()<12 || password.getBytes(StandardCharsets.UTF_8).length>72) throw invalid();
        boolean assisted=username!=null && !username.isBlank();
        String value=assisted?credential.replace("-", "").replace(" ","").toUpperCase(Locale.ROOT):credential;
        var user=assisted?users.findByUsername(username.trim().toLowerCase(Locale.ROOT)):mongo.findOne(Query.query(Criteria.where("accessTicket.digest").is(digest(value))),UserModel.class);
        enabled(user); var ticket=user.getAccessTicket();
        if(ticket==null || ticket.getExpiresAt()==null || !ticket.getExpiresAt().isAfter(Instant.now()) || ticket.getAttempts()<=0 || !publicBaseUrl().equals(ticket.getEnvironment()) || assisted!= "SCHOOL_ASSISTED".equals(ticket.getPurpose())) throw invalid();
        // A guess charges only the ticket actually read, not a concurrent replacement.
        if(!MessageDigest.isEqual(ticket.getDigest().getBytes(StandardCharsets.US_ASCII),digest(value).getBytes(StandardCharsets.US_ASCII))) {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(user.getId()).and("accessTicket.issueId").is(ticket.getIssueId()).and("accessTicket.attempts").gt(0)),new Update().inc("accessTicket.attempts",-1),UserModel.class);
            throw invalid();
        }
        var query=Query.query(Criteria.where("_id").is(user.getId()).and("enabled").ne(false)
                .and("accessTicket.digest").is(ticket.getDigest()).and("accessTicket.issueId").is(ticket.getIssueId())
                .and("accessTicket.environment").is(publicBaseUrl()).and("accessTicket.expiresAt").gt(Instant.now()).and("accessTicket.attempts").gt(0));
        var update=new Update().set("password",encoder.encode(password)).set("passwordSetupRequired",false)
                .set("setupCompletedAt",Instant.now()).set("invitationStatus","SETUP_COMPLETE").unset("accessTicket").unset("invitationExpiresAt").inc("credentialVersion",1);
        if(mongo.findAndModify(query,update,FindAndModifyOptions.options().returnNew(true),UserModel.class)==null) throw invalid();
    }
    public void revoke(String id) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().unset("accessTicket").unset("invitationExpiresAt").set("invitationStatus","REVOKED").inc("credentialVersion",1),UserModel.class);
    }
    public void googleEstablished(UserModel user, boolean verifiedEmail) {
        enabled(user); if(!verifiedEmail) throw new AccessDeniedException("A verified Google email is required");
        if(user.isPasswordSetupRequired()) {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(user.getId()).and("enabled").ne(false)),new Update().set("passwordSetupRequired",false).set("setupCompletedAt",Instant.now()).set("invitationStatus","GOOGLE_ESTABLISHED").set("privateEmailSetupAllowed",true).unset("accessTicket").unset("invitationExpiresAt").inc("credentialVersion",1),UserModel.class);
        }
    }
}
