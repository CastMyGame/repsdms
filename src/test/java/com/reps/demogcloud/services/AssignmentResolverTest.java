package com.reps.demogcloud.services;

import com.reps.demogcloud.data.*;
import com.reps.demogcloud.models.assignments.*;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.school.School;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentResolverTest {
    AssignmentTemplateRepository templates=mock(AssignmentTemplateRepository.class);
    AssignmentTemplateBindingRepository bindings=mock(AssignmentTemplateBindingRepository.class);
    SchoolRepository schools=mock(SchoolRepository.class);
    EmployeeRepository employees=mock(EmployeeRepository.class);
    AssignmentResolver resolver=new AssignmentResolver(templates,bindings,schools,employees);
    AssignmentTemplate teacher, schoolDefault, system;
    AssignmentTemplateBinding own, schoolBinding;
    @BeforeEach void setup() {
        var school=new School();school.setSchoolName("School");school.setSchoolIdNumber("school-id");
        when(schools.findBySchoolNameIgnoreCase("School")).thenReturn(Optional.of(school));
        var employee=new Employee();employee.setEmail("teacher@example.test");employee.setSchool("School");
        when(employees.findByEmailIgnoreCase("teacher@example.test")).thenReturn(employee);
        teacher=template("teacher",AssignmentTemplate.Scope.TEACHER_DEFAULT);teacher.setCreatedByUserId("teacher@example.test");teacher.setSchoolId("school-id");
        schoolDefault=template("school",AssignmentTemplate.Scope.SCHOOL_DEFAULT);schoolDefault.setSchoolId("school-id");
        system=template("system",AssignmentTemplate.Scope.SYSTEM_DEFAULT);system.setCreatedBySystem(true);
        when(templates.findAll()).thenReturn(List.of(teacher,schoolDefault,system));
        when(templates.findById(anyString())).thenAnswer(i -> List.of(teacher,schoolDefault,system).stream().filter(t -> t.getId().equals(i.getArgument(0))).findFirst());
        own=binding("teacher@example.test","school-id","teacher");schoolBinding=binding(null,"school-id","school");
        when(bindings.findAll()).thenReturn(List.of(own,schoolBinding));
    }
    static AssignmentTemplate template(String id,AssignmentTemplate.Scope scope) {
        var t=new AssignmentTemplate();t.setId(id);t.setInfractionName("Horseplay");t.setLevel(1);t.setScope(scope);return t;
    }
    static AssignmentTemplateBinding binding(String teacher,String school,String id) {
        var b=new AssignmentTemplateBinding();b.setTeacherEmail(teacher);b.setSchoolId(school);b.setAssignmentTemplateId(id);
        b.setInfractionName("Horseplay");b.setLevel(1);b.setActive(true);return b;
    }
    AssignmentTemplate resolve(){return resolver.resolve(" TEACHER@example.test ","School"," horseplay ",1);}
    @Test void teacherBindingWinsOverSchoolAndSystem(){assertEquals("teacher",resolve().getId());}
    @Test void schoolBindingWinsWithoutTeacherBinding(){own.setActive(false);assertEquals("school",resolve().getId());}
    @Test void scopedSchoolDefaultRemainsAnImplicitSchoolBinding(){when(bindings.findAll()).thenReturn(List.of());assertEquals("school",resolve().getId());}
    @Test void systemDefaultIsLast(){when(bindings.findAll()).thenReturn(List.of());when(templates.findAll()).thenReturn(List.of(system));assertEquals("system",resolve().getId());}
    @Test void inactiveExplicitBindingNeverSilentlyFallsBack(){teacher.setActive(false);assertThrows(IllegalArgumentException.class,this::resolve);}
    @Test void missingExplicitBindingNeverSilentlyFallsBack(){own.setAssignmentTemplateId("missing");assertThrows(IllegalArgumentException.class,this::resolve);}
    @Test void crossSchoolTeacherBindingRejected(){own.setSchoolId("Other");assertThrows(IllegalArgumentException.class,this::resolve);}
    @Test void crossSchoolTemplateRejected(){teacher.setSchoolId("Other");assertThrows(IllegalArgumentException.class,this::resolve);}
    @Test void wrongLevelOrInfractionRejected(){teacher.setLevel(2);assertThrows(IllegalArgumentException.class,this::resolve);}
    @Test void competingActiveBindingsAreAnActionableConflict(){when(bindings.findAll()).thenReturn(List.of(own,binding("teacher@example.test","school-id","school")));assertThrows(IllegalArgumentException.class,this::resolve);}
    @Test void competingSystemDefaultsDoNotSelectAnArbitraryFirst(){when(bindings.findAll()).thenReturn(List.of());var second=template("other",AssignmentTemplate.Scope.SYSTEM_DEFAULT);second.setCreatedBySystem(true);when(templates.findAll()).thenReturn(List.of(system,second));assertThrows(IllegalArgumentException.class,this::resolve);}
    @Test void unknownOwnershipIsNotInferredToBeSystem(){when(bindings.findAll()).thenReturn(List.of());system.setCreatedBySystem(false);when(templates.findAll()).thenReturn(List.of(system));assertThrows(IllegalArgumentException.class,this::resolve);}
    @ParameterizedTest @ValueSource(strings={"Horseplay","Dress Code","Inappropriate Language"})
    void compatibleLegacyStructuredMetadataUsesReadDefaults(String infraction) throws Exception {
        var context=new org.springframework.data.mongodb.core.mapping.MongoMappingContext();
        var conversions=org.springframework.data.mongodb.core.convert.MongoCustomConversions.create(adapter -> {});
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());context.afterPropertiesSet();
        var converter=new org.springframework.data.mongodb.core.convert.MappingMongoConverter(org.springframework.data.mongodb.core.convert.NoOpDbRefResolver.INSTANCE,context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        var document=new org.bson.Document("_id","legacy").append("infractionName",infraction).append("level",1).append("createdBySystem",true);
        document.append("questions",List.of(new org.bson.Document("type","EXPLORATORY_OPEN").append("prompt","Reflect on your behavior").append("required",true)));
        var mapped=converter.read(AssignmentTemplate.class,document);
        assertTrue(mapped.isActive());assertEquals(AssignmentTemplate.Scope.SYSTEM_DEFAULT,mapped.getScope());
        when(bindings.findAll()).thenReturn(List.of());when(templates.findAll()).thenReturn(List.of(mapped));
        assertEquals("legacy",resolver.resolve("teacher@example.test","School",infraction.toLowerCase(Locale.ROOT),1).getId());
        assertEquals("q1",AssignmentWork.assigned(mapped).getTemplate().getQuestions().get(0).getId());
        assertFalse(document.containsKey("active"));assertNull(mapped.getQuestions().get(0).getId());
        verify(templates,never()).save(any());verify(bindings,never()).save(any());
    }
}
