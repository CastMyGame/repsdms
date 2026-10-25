package com.reps.demogcloud.services.email;

import java.util.Map;
import java.util.regex.*;
import org.springframework.web.util.HtmlUtils;

/** Render only template tokens, never re-interpret user text as a second template. */
public final class EmailContent {
    private EmailContent() {}
    private static final Pattern TOKEN=Pattern.compile("\\{\\{([^{}]+)}}");
    public static String render(String template, Map<String,String> values, boolean html) {
        var matcher=TOKEN.matcher(template);var result=new StringBuffer();
        while(matcher.find()) {
            String value=values.get(matcher.group(1));
            if(value==null || value.isBlank()) throw new IllegalArgumentException("Required email content is missing");
            if(html) value=HtmlUtils.htmlEscape(value,"UTF-8").replace("{","&#123;").replace("}","&#125;");
            matcher.appendReplacement(result,Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);return result.toString();
    }
    public static void requireRendered(String subject,String body) {
        if(subject==null || subject.isBlank() || body==null || body.isBlank()
                || subject.contains("\r") || subject.contains("\n") || TOKEN.matcher(subject).find() || TOKEN.matcher(body).find())
            throw new IllegalArgumentException("Email content is incomplete or unsafe");
    }
}
