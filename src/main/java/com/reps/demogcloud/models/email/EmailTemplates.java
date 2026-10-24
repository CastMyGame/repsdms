package com.reps.demogcloud.models.email;

public class EmailTemplates {

    public static final String EMAIL_EN = """
        <p>Hello, {{studentFullName}} received referral level {{infractionLevel}} for {{infractionName}}.</p><p>{{description}}</p><p>Review the assigned work and school instructions at {{loginUrl}}. Sign-in identifier: {{studentEmail}}. Use your established password, Google sign-in, or ask school staff for secure setup assistance. No password is included in this message.</p><p>Reply to contact the responsible staff member.</p>
        """;

    public static final String EMAIL_ES = """
        <p>Hola, {{studentFullName}} recibió una referencia de nivel {{infractionLevel}} por {{infractionName}}.</p><p>{{description}}</p><p>Consulte la tarea y las instrucciones de la escuela en {{loginUrl}}. Identificador de acceso: {{studentEmail}}. Use su contraseña establecida, inicie sesión con Google o solicite ayuda a la escuela para configurar el acceso de forma segura. Este mensaje no contiene ninguna contraseña.</p><p>Responda para contactar al personal responsable.</p>
        """;

    public static final String CFR_EN = """
        <p>Hello, {{studentFullName}} received another referral for {{infractionName}}.</p><p>{{description}}</p><p>No new assignment was issued for this repeated referral. Review existing work and school instructions at {{loginUrl}}. Contact {{teacherEmail}} with questions.</p>
        """;

    public static final String CFR_ES = """
        <p>Hola, {{studentFullName}} recibió otra referencia por {{infractionName}}.</p><p>{{description}}</p><p>No se asignó una nueva tarea por esta referencia repetida. Consulte el trabajo existente y las instrucciones de la escuela en {{loginUrl}}. Contacte a {{teacherEmail}} si tiene preguntas.</p>
        """;

    public static final String TEXT_EN = """
        Your child, {{studentFullName}}, has received offense number {{infractionLevel}} for {{infractionName}}. {{description}}.
        Review current assigned work and school instructions in REPS, or contact the school for help.
        Check your email for details. This is an automated text—please reply to the email or contact the school with questions.
        """;

    public static final String TEXT_ES = """
        Su hijo/a, {{studentFullName}}, ha recibido la falta número {{infractionLevel}} por {{infractionName}}. {{description}}.
        Consulte el trabajo asignado y las instrucciones actuales en REPS o comuníquese con la escuela para obtener ayuda.
        Revise su correo electrónico para más detalles. Este es un mensaje automático; responda al correo o contacte a la escuela si tiene preguntas.
        """;

    public static final String SUBJECT_REFERRAL_EN = "{{schoolName}} Referral for {{studentFullName}}";
    public static final String SUBJECT_REFERRAL_ES = "{{schoolName}} Referencia para {{studentFullName}}";

    public static final String SUBJECT_OFFICE_REFERRAL_EN = "{{schoolName}} Office Referral for {{studentFullName}}";
    public static final String SUBJECT_OFFICE_REFERRAL_ES = "{{schoolName}} Referencia de oficina para {{studentFullName}}";

    public static final String OFFICE_REFERRAL_MSG_EN = """
        <p>The referral history for {{studentFullName}} regarding {{infractionName}} requires office review. The principal will determine the appropriate response individually.</p><p>{{summary}}</p>
        """;

    public static final String OFFICE_REFERRAL_MSG_ES = """
        <p>El historial de referencias de {{studentFullName}} sobre {{infractionName}} requiere revisión de la dirección. El director determinará la respuesta apropiada de forma individual.</p><p>{{summary}}</p>
        """;

    public static final String SUBJECT_COMPLETION_EN = "Referral Completion for {{studentFullName}}";
    public static final String SUBJECT_COMPLETION_ES = "Confirmación de finalización para {{studentFullName}}";
    public static final String SUBJECT_COMPLETION_WITH_SCHOOL_EN = "{{schoolName}} assignment completion for {{studentFullName}}";
    public static final String SUBJECT_COMPLETION_WITH_SCHOOL_ES = "{{schoolName}} finalización de tarea para {{studentFullName}}";

    public static final String COMPLETION_MSG_EN = """
        Hello,
        Your child, {{studentFullName}}, has successfully completed the assignment for the infraction: {{infractionName}}.
        No further action is required. Thank you for your support and your child’s effort.

        If you have any questions, feel free to contact the teacher at {{teacherEmail}}.
        """;

    public static final String COMPLETION_MSG_ES = """
        Hola,
        Su hijo/a, {{studentFullName}}, ha completado exitosamente la tarea por la infracción: {{infractionName}}.
        No se requiere ninguna acción adicional. Gracias por su apoyo y por el esfuerzo de su hijo/a.

        Si tiene alguna pregunta, puede comunicarse con el maestro en {{teacherEmail}}.
        """;

    public static final String SUBJECT_L3_REJECT_EN =
            "Assignment revision requested for {{studentFullName}}";
    public static final String SUBJECT_L3_REJECT_ES =
            "Revisión de tarea solicitada para {{studentFullName}}";

    public static final String L3_REJECT_MSG_EN = """
    Hello,
    Unfortunately your answers were not acceptable. You must resubmit with better responses.

    Feedback:
    {{feedback}}

    You may reply to this message with any questions.
    """;

    public static final String L3_REJECT_MSG_ES = """
    Hola,
    Lamentablemente, sus respuestas no fueron aceptables. Debe volver a enviarlas con mejores respuestas.

    Comentarios:
    {{feedback}}

    Puede responder a este mensaje si tiene alguna pregunta.
    """;

    public static final String SUBJECT_PUNISHMENT_DELETED_EN =
            "{{schoolName}} Punishment Deleted for {{studentFullName}}";
    public static final String SUBJECT_PUNISHMENT_DELETED_ES =
            "{{schoolName}} Notificación eliminada para {{studentFullName}}";

    public static final String PUNISHMENT_DELETED_MSG_EN = """
    Hello,
    The referral for your child, {{studentFullName}}, was entered in error and has been removed.
    The infraction was for {{infractionName}} (Level {{infractionLevel}}).

    Explanation:
    {{explanation}}

    Thank you for your patience.
    """;

    public static final String PUNISHMENT_DELETED_MSG_ES = """
    Hola,
    La notificación para su hijo/a, {{studentFullName}}, se ingresó por error y ha sido eliminada.
    La infracción fue por {{infractionName}} (Nivel {{infractionLevel}}).

    Explicación:
    {{explanation}}

    Gracias por su paciencia.
    """;

    public static final String SUBJECT_PUNISHMENT_RESTORED_EN =
            "{{schoolName}} Punishment Restored for {{studentFullName}}";
    public static final String SUBJECT_PUNISHMENT_RESTORED_ES =
            "{{schoolName}} Notificación restaurada para {{studentFullName}}";

    public static final String PUNISHMENT_RESTORED_MSG_EN = """
        <p>The referral for {{studentFullName}} was restored. Review its current status and school instructions at {{loginUrl}}.</p>
        """;

    public static final String PUNISHMENT_RESTORED_MSG_ES = """
        <p>La referencia de {{studentFullName}} fue restaurada. Consulte su estado actual y las instrucciones de la escuela en {{loginUrl}}.</p>
        """;

    public static final String POSITIVE_EN = """
        <p>Positive recognition for {{studentFullName}}!</p><p>{{description}}</p><p>Thank you for contributing positively to our school community. Keep up the great work!</p>
        """;
    public static final String POSITIVE_ES = """
        <p>¡Reconocimiento positivo para {{studentFullName}}!</p><p>{{description}}</p><p>Gracias por contribuir positivamente a nuestra comunidad escolar. ¡Sigue así!</p>
        """;
    public static final String REFERRAL_CREATED_EN = """
        <p>Hello, {{studentFullName}} received referral level {{infractionLevel}} for {{infractionName}}.</p><p>{{description}}</p><p>Review the case at {{loginUrl}}. Use established access or ask school staff for secure setup assistance. No password is included.</p>
        """;
    public static final String REFERRAL_CREATED_ES = """
        <p>Hola, {{studentFullName}} recibió una referencia de nivel {{infractionLevel}} por {{infractionName}}.</p><p>{{description}}</p><p>Consulte el caso en {{loginUrl}}. Use su acceso establecido o solicite ayuda a la escuela para configurar el acceso de forma segura. No se incluye ninguna contraseña.</p>
        """;
    public static final String OFFICE_CREATED_EN = """
        <p>An office referral for {{studentFullName}} regarding {{infractionName}} was recorded.</p><p>{{description}}</p><p>The principal will review this individually. No student assignment is created by this office referral. Contact the school with questions.</p>
        """;
    public static final String OFFICE_CREATED_ES = """
        <p>Se registró una referencia a la dirección para {{studentFullName}} sobre {{infractionName}}.</p><p>{{description}}</p><p>El director la revisará de forma individual. Esta referencia no crea una tarea para el estudiante. Comuníquese con la escuela si tiene preguntas.</p>
        """;
    public static final String SCHOOL_UPDATE_EN = """
        <p>A school update for {{studentFullName}} was recorded: {{infractionName}}.</p><p>{{description}}</p><p>Contact the responsible school staff member for next steps.</p>
        """;
    public static final String SCHOOL_UPDATE_ES = """
        <p>Se registró una actualización escolar para {{studentFullName}}: {{infractionName}}.</p><p>{{description}}</p><p>Comuníquese con el personal responsable de la escuela para conocer los próximos pasos.</p>
        """;
    public static final String SUBMITTED_EN = """
        <p>{{studentFullName}} submitted assignment work. It is saved and awaiting school review; submission does not mean it has been accepted or the case closed.</p>
        """;
    public static final String SUBMITTED_ES = """
        <p>{{studentFullName}} entregó su tarea. Está guardada y pendiente de revisión escolar; la entrega no significa que se haya aceptado ni que el caso esté cerrado.</p>
        """;
    public static final String REMINDER_EN = """
        <p>This is a school reminder for {{studentFullName}} about incomplete assignment work. Review the current case and school instructions at {{loginUrl}} or contact the school. This reminder does not change the case.</p>
        """;
    public static final String REMINDER_ES = """
        <p>Este es un recordatorio escolar para {{studentFullName}} sobre una tarea incompleta. Consulte el caso actual y las instrucciones de la escuela en {{loginUrl}} o comuníquese con la escuela. Este recordatorio no modifica el caso.</p>
        """;
}
