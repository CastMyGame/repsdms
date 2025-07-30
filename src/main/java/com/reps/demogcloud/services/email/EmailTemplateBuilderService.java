package com.reps.demogcloud.services.email;

import org.springframework.stereotype.Service;

@Service
public class EmailTemplateBuilderService {

    public String createEmailText(String studentFirstName, String studentLastName, String infractionLevel, String infractionName, String description, String studentEmail) {
        String emailText = " Hello, <br>" +
                " Your child, " + studentFirstName + " " + studentLastName +
                " has received offense number " + infractionLevel + " for " + infractionName + ". " + description +
                ".<br> " +
                "<br>" +
                " As a result they have received an assignment. The goal of the assignment is to provide " + studentFirstName + " " + studentLastName +
                " with information about the infraction and ways to make beneficial decisions in the future. If " + studentFirstName + " " + studentLastName + " does not complete the assignment by the end of the school day tomorrow they will receive a failure to comply with disciplinary action referral which is an office managed referral. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential. <br>" +
                "<br> " +
                " Your child’s login information is as follows at the website https://repsdiscipline.vercel.app/student-login:<br>" +
                " The username is their school email and their password is " + studentEmail + " unless they have changed their password using the forgot my password button on the login screen.<br>" +
                "<br> " +
                " If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment.";

        return replaceString(emailText);
    }

    public String createTextMessage(String studentFirstName, String studentLastName, String infractionLevel, String infractionName, String description) {
        String textMessage = " Your child, " + studentFirstName + " " + studentLastName +
                " has received offense number " + infractionLevel + " for " + infractionName + ". " + description +
                ". " +
                "They have an assignment which is due by the end of the school day tomorrow and if the assignment is not done they will receive a failure to comply with disciplinary action referral which is an office managed referral." +
                "Check your email for additional details, including login info. This is an automated text—please reply to the email or contact the school directly with any questions.";

        return replaceString(textMessage);
    }

    public String createCFRMessage(String studentFirstName, String studentLastName, String infractionName, String teacherEmail, String studentEmail) {
        String cfrMessage = " Hello," +
                "<br>" +
                " Your child, " + studentFirstName + " " + studentLastName +
                " has received another offense for " + infractionName + ". <br>" +
                "<br>" +
                " They currently have an assignment at the website https://repsdiscipline.vercel.app/student-login they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening. <br>" +
                "You may email the teacher directly at " + teacherEmail + " if there are any extenuating circumstances that may have led to this behavior, will prevent the completion of the assignment, or if you have any questions or concerns." +
                "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app/student-login :<br>" +
                "The username is their school email and their password is " + studentEmail + " unless they have changed their password using the forgot my password button on the login screen.<br>" +
                "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment.";

        return replaceString(cfrMessage);
    }

    public String replaceString(String input) {
        return input.replace("[,", "").replace(",]", "").trim();
    }

    public String adjustString(String input) {
        return input.replaceAll("(.*?)(\\d+)$", "$1 $2").trim();
    }
}