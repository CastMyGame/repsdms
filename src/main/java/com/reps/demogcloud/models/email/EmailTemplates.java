package com.reps.demogcloud.models.email;

public class EmailTemplates {

    public static final String EMAIL_EN = """
        Hello,
        Your child, {{studentFullName}}, has received offense number {{infractionLevel}} for {{infractionName}}. {{description}}.
        As a result they have received an assignment. The goal of the assignment is to provide {{studentFullName}} with information about the infraction and ways to make beneficial decisions in the future. If {{studentFullName}} does not complete the assignment by the end of the school day tomorrow they will receive a failure to comply with disciplinary action referral which is an office managed referral. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential.
        Your child’s login information is as follows at the website https://repsdiscipline.vercel.app/student-login:
        The username is their school email and their password is {{studentEmail}} unless they have changed their password using the forgot my password button on the login screen.
        If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment.
        """;

    public static final String EMAIL_ES = """
        Hola,
        Su hijo, {{studentFullName}}, ha recibido la falta número {{infractionLevel}} por {{infractionName}}. {{description}}.<br><br>
        Como resultado, se le ha asignado una tarea. El objetivo de esta tarea es proporcionar a {{studentFullName}} información sobre la infracción y maneras de tomar decisiones beneficiosas en el futuro.
        Si {{studentFullName}} no completa la tarea antes del final del día escolar de mañana, recibirá una notificación por incumplimiento de las medidas disciplinarias, una notificación gestionada por la oficina.
        Enviaremos un correo electrónico confirmando la finalización de la tarea cuando la recibamos.
        Agradecemos su ayuda y seguiremos trabajando para ayudar a su hijo/a a alcanzar su máximo potencial.
        La información de inicio de sesión de su hijo es la siguiente en el sitio web https://repsdiscipline.vercel.app/student-login:
        El nombre de usuario es su correo electrónico escolar y su contraseña es {{studentEmail}} a menos que hayan cambiado su contraseña utilizando el botón “Olvidé mi contraseña” en la pantalla de inicio de sesión.
        Si tiene alguna pregunta o inquietud, puede contactar directamente al profesor que escribió la recomendación haciendo clic en "Responder a todos" y escribiendo una respuesta.
        Incluya cualquier circunstancia atenuante que pueda haber provocado este comportamiento o que impida completar la tarea.
        """;

    public static final String CFR_EN = """
        Hello,<br>
        Your child, {{studentFullName}}, has received another offense for {{infractionName}}. {{description}}.<br><br>

        They currently have an assignment at https://repsdiscipline.vercel.app/student-login they need to complete for this type of offense, so they will not be receiving another assignment.
        A record of this offense will be kept, and this email is to inform you of this happening.<br><br>

        You may email the teacher directly at {{teacherEmail}} if there are any extenuating circumstances that may have led to this behavior, will prevent completion of the assignment, or if you have any questions or concerns.<br><br>

        Your child’s login information is as follows at the website https://repsdiscipline.vercel.app/student-login:<br>
        The username is their school email and their password is {{studentEmail}} unless they have changed their password using the “Forgot my password” button on the login screen.<br><br>

        If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking “Reply all” to this message and typing a response.
        Please include any extenuating circumstances that may have led to this behavior, or will prevent completion of the assignment.
        """;

    public static final String CFR_ES = """
        Hola,<br>
        Su hijo/a, {{studentFullName}}, ha recibido otra falta por {{infractionName}}. {{description}}.<br><br>

        Actualmente tiene una tarea en https://repsdiscipline.vercel.app/student-login que debe completar para este tipo de falta, por lo que no recibirá otra.
        Se mantendrá un registro de esta falta, y este correo es para informarle de lo ocurrido.<br><br>

        Puede enviar un correo al profesor directamente a {{teacherEmail}} si existen circunstancias atenuantes que hayan provocado este comportamiento, que impidan completar la tarea, o si tiene alguna pregunta o inquietud.<br><br>

        La información de inicio de sesión de su hijo/a es la siguiente en el sitio web https://repsdiscipline.vercel.app/student-login:<br>
        El nombre de usuario es su correo electrónico escolar y su contraseña es {{studentEmail}} a menos que haya cambiado su contraseña usando el botón “Olvidé mi contraseña” en la pantalla de inicio de sesión.<br><br>

        Si tiene alguna pregunta o inquietud, puede contactar directamente al profesor que escribió la recomendación haciendo clic en “Responder a todos” y escribiendo una respuesta.
        Incluya cualquier circunstancia atenuante que pueda haber provocado este comportamiento o que impida completar la tarea.
        """;

    public static final String TEXT_EN = """
        Your child, {{studentFullName}}, has received offense number {{infractionLevel}} for {{infractionName}}. {{description}}.
        They have an assignment due by the end of the school day tomorrow. If not completed, they will receive a Failure to Comply with Disciplinary Action referral (office-managed).
        Check your email for details. This is an automated text—please reply to the email or contact the school with questions.
        """;

    public static final String TEXT_ES = """
        Su hijo/a, {{studentFullName}}, ha recibido la falta número {{infractionLevel}} por {{infractionName}}. {{description}}.
        Tiene una tarea que vence antes del final del día escolar de mañana. Si no se completa, recibirá una notificación por incumplimiento de las medidas disciplinarias (gestionada por la oficina).
        Revise su correo electrónico para más detalles. Este es un mensaje automático; responda al correo o contacte a la escuela si tiene preguntas.
        """;

    public static final String SUBJECT_REFERRAL_EN = "{{schoolName}} Referral for {{studentFullName}}";
    public static final String SUBJECT_REFERRAL_ES = "{{schoolName}} Referencia para {{studentFullName}}";

    public static final String SUBJECT_OFFICE_REFERRAL_EN = "{{schoolName}} Office Referral for {{studentFullName}}";
    public static final String SUBJECT_OFFICE_REFERRAL_ES = "{{schoolName}} Referencia de oficina para {{studentFullName}}";

    public static final String OFFICE_REFERRAL_MSG_EN = """
        Thank you for using the teacher managed referral. Because {{studentFullName}} has received their fourth or greater offense for {{infractionName}}, they must now receive an office referral.
        Please complete an office referral for Failure to Comply with Disciplinary Action.
        Summary:
        {{summary}}
        """;

    public static final String OFFICE_REFERRAL_MSG_ES = """
        Gracias por usar la referencia administrada por el maestro. Debido a que {{studentFullName}} ha recibido su cuarta o mayor infracción por {{infractionName}}, ahora debe recibir una referencia de oficina.
        Por favor complete una referencia de oficina por Incumplimiento de medidas disciplinarias.
        Resumen:
        {{summary}}
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
            "Level Three Answers Not Accepted for {{studentFullName}}";
    public static final String SUBJECT_L3_REJECT_ES =
            "Respuestas de nivel tres no aceptadas para {{studentFullName}}";

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
}
