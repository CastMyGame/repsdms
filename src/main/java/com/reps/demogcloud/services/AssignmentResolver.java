package com.reps.demogcloud.services;

import com.reps.demogcloud.data.*;
import com.reps.demogcloud.models.assignments.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Predicate;

/** One selection policy for issuing work. Existing cases use their pinned snapshot/ID. */
@Service @RequiredArgsConstructor
public class AssignmentResolver {
    private final AssignmentTemplateRepository templates;
    private final AssignmentTemplateBindingRepository bindings;
    private final SchoolRepository schools;
    private final EmployeeRepository employees;

    private com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess canonical;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCanonicalWorkflowAccess(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess value){canonical=value;}
    @org.springframework.beans.factory.annotation.Autowired(required=false) private org.springframework.data.mongodb.core.MongoTemplate mongo;
    private static String key(String s) { return s==null?"":s.trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT); }
    private boolean school(String candidate, String school) {
        if (key(candidate).isEmpty() || key(school).isEmpty()) return false;
        return key(candidate).equals(key(school)) || schools.findBySchoolNameIgnoreCase(school)
                .map(s -> key(candidate).equals(key(s.getSchoolIdNumber()))).orElse(false);
    }
    public boolean belongs(AssignmentTemplate t, String school) {
        if(canonical!=null)return canonical.templateOwner(t);
        if (t.isCreatedBySystem() && key(t.getCreatedByUserId()).isEmpty() && key(t.getSchoolId()).isEmpty()
                && t.getScope()==AssignmentTemplate.Scope.SYSTEM_DEFAULT) return true;
        if (!key(t.getSchoolId()).isEmpty()) return school(t.getSchoolId(),school);
        if (key(t.getCreatedByUserId()).isEmpty()) return false;
        var employee=employees.findByEmailIgnoreCase(t.getCreatedByUserId());
        return employee!=null && !employee.isArchived() && school(employee.getSchool(),school);
    }
    public AssignmentTemplate pinned(String id, String school) {
        AssignmentTemplate t;
        try{t=canonical==null?templates.findById(id).orElseThrow(()->new IllegalArgumentException("The assigned template is unavailable; staff must review this case")):canonical.template(id);}
        catch(org.springframework.security.access.AccessDeniedException unavailable){throw new IllegalArgumentException("The assigned template is unavailable; staff must review this case");}
        if (!t.isActive() || !belongs(t,school)) throw new IllegalArgumentException("The assigned template is unavailable; staff must review this case");
        return t;
    }
    private AssignmentTemplate unique(List<AssignmentTemplate> matches) {
        if(matches.size()!=1) throw new IllegalArgumentException("Multiple applicable assignment templates require staff review");
        return matches.get(0);
    }
    public AssignmentTemplate resolve(String teacher, String school, String infraction, int level) {
        if(key(teacher).isEmpty() || key(school).isEmpty() || key(infraction).isEmpty() || level<1)
            throw new IllegalArgumentException("Assignment selection requires teacher, school, infraction and level");
        Predicate<AssignmentTemplateBinding> match=b -> b.isActive() && key(b.getInfractionName()).equals(key(infraction)) && b.getLevel()==level;
        if(canonical!=null){
            canonical.employee(teacher);
            var legacy=org.springframework.data.mongodb.core.query.Query.query(org.springframework.data.mongodb.core.query.Criteria.where("tenantSchoolId").exists(false).and("teacherEmail").regex(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess.exact(teacher)).and("active").ne(false));
            if(mongo.find(legacy,AssignmentTemplateBinding.class).stream().anyMatch(match))throw new IllegalArgumentException("Legacy assignment binding needs reviewed ownership before selection");
        }
        var allBindings=canonical==null?bindings.findAll():mongo.find(canonical.query(),AssignmentTemplateBinding.class);
        var teacherBindings=allBindings.stream().filter(match).filter(b -> key(b.getTeacherEmail()).equals(key(teacher))).toList();
        var schoolBindings=allBindings.stream().filter(match).filter(b -> key(b.getTeacherEmail()).isEmpty() && (canonical!=null?canonical.owns(b):school(b.getSchoolId(),school))).toList();
        var selected=!teacherBindings.isEmpty()?teacherBindings:schoolBindings;
        if(!selected.isEmpty()) {
            var ids=selected.stream().map(AssignmentTemplateBinding::getAssignmentTemplateId).distinct().toList();
            if(ids.size()!=1) throw new IllegalArgumentException("Conflicting active assignment bindings require staff review");
            for(var b:selected) if(!key(b.getSchoolId()).isEmpty() && !(canonical!=null?canonical.owns(b):school(b.getSchoolId(),school)))
                throw new IllegalArgumentException("Assignment binding school mismatch");
            var t=pinned(ids.get(0),school);
            if(!key(t.getInfractionName()).equals(key(infraction)) || t.getLevel()!=level)
                throw new IllegalArgumentException("Assignment binding infraction/level mismatch");
            if(!t.isCreatedBySystem() && !key(t.getCreatedByUserId()).equals(key(teacher))
                    && t.getVisibility()!=AssignmentTemplate.Visibility.SCHOOL && t.getScope()!=AssignmentTemplate.Scope.SCHOOL_DEFAULT)
                throw new IllegalArgumentException("Assignment binding is not shared with this teacher");
            return t;
        }
        // Read-side compatibility: Java defaults cover absent legacy active/scope fields;
        // an exact Mongo active=true/scope query cannot match those absent fields.
        var candidates=(canonical==null?templates.findAll():canonicalTemplates()).stream().filter(t -> t.isActive() && t.getLevel()==level
                && key(t.getInfractionName()).equals(key(infraction)) && belongs(t,school)).toList();
        var schoolDefaults=candidates.stream().filter(t -> t.getScope()==AssignmentTemplate.Scope.SCHOOL_DEFAULT).toList();
        if(!schoolDefaults.isEmpty()) return unique(schoolDefaults);
        var defaults=candidates.stream().filter(t -> t.getScope()==AssignmentTemplate.Scope.SYSTEM_DEFAULT
                && t.isCreatedBySystem() && key(t.getSchoolId()).isEmpty() && key(t.getCreatedByUserId()).isEmpty()).toList();
        if(!defaults.isEmpty()) return unique(defaults);
        // Do not guess ownership for malformed structured templates or migrate legacy records here.
        throw new IllegalArgumentException("No applicable assignment template; staff must review template status, ownership and bindings");
    }    private List<AssignmentTemplate> canonicalTemplates(){
        var global=new org.springframework.data.mongodb.core.query.Criteria().andOperator(org.springframework.data.mongodb.core.query.Criteria.where("tenantSchoolId").exists(false),org.springframework.data.mongodb.core.query.Criteria.where("createdBySystem").is(true),org.springframework.data.mongodb.core.query.Criteria.where("scope").is("SYSTEM_DEFAULT"));
        return mongo.find(org.springframework.data.mongodb.core.query.Query.query(new org.springframework.data.mongodb.core.query.Criteria().orOperator(org.springframework.data.mongodb.core.query.Criteria.where("tenantSchoolId").is(canonical.tenant()),global)),AssignmentTemplate.class).stream().filter(canonical::templateOwner).toList();
    }

}
