package com.tataplay.scripts.release;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PostReleaseSanity {
    private static Pattern pattern = Pattern.compile("videoready-tatasky/([^/]+)/");

    private static String releaseBranch = "release-30-12-2025-E";

    public static void main(String[] args) throws IOException, InterruptedException {
        Set<Service> uniqueServices = collectServices();
        Map<String, Data> MRState = checkMergeStatus(uniqueServices, releaseBranch);
        sentEmail(MRState, releaseBranch);
    }

    private static Set<Service> collectServices() throws FileNotFoundException {
        Set<Service> services = new HashSet<>();
        try (Scanner scanner = new Scanner(new File("/home/saurabh-mittal/Downloads/release2.csv"))) {
            if (scanner.hasNextLine()) scanner.nextLine(); // skip header

            while (scanner.hasNextLine()) {
                String[] parts = scanner.nextLine().trim().split(",");
                String applicationName = getValue(parts, 2);
                String serviceName = getValue(parts, 3);
                String tag = getValue(parts, 4);
                String MR = getValue(parts, 5);
                String serviceNameViaMatcher=null;
                if (StringUtils.isNotBlank(MR)) {
                    Matcher matcher = pattern.matcher(MR);
                    serviceNameViaMatcher = matcher.find() ? matcher.group(1) : null;
                }
                String candidate = StringUtils.isNotBlank(serviceName) ? serviceName
                        : StringUtils.isNotBlank(serviceNameViaMatcher) ? serviceNameViaMatcher
                        : StringUtils.isNotBlank(applicationName) ? applicationName
                        : null;
                Service service = new Service();
                service.setServiceName(candidate);
                service.setTag(tag);
                service.setMR(MR);
                if (candidate != null) services.add(service);
            }
        }
        return services;
    }

    private static String getValue(String[] arr, int index) {
        try {
            return arr[index].trim();
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    private static Map<String, Data> checkMergeStatus(Set<Service> services, String releaseBranch)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        Map<String, Data> states = new LinkedHashMap<>();
        String query = String.join("&",
                "source_branch=" + releaseBranch,
                "target_branch=master");
//                "state=merged");

        JSONParser parser = new JSONParser();

        for (Service service : services) {
            if (StringUtils.equalsIgnoreCase(service.getTag(),"restart")) {
                states.put(service.getServiceName(), new Data("NA", "MR not required as service was just restarted"));
                continue;
            }
            String projectPath = URLEncoder.encode("videoready-tatasky/" + service.getServiceName(), StandardCharsets.UTF_8);
            String url = "https://gitlab.intelligrape.net/api/v4/projects/" + projectPath + "/merge_requests?" + query;
            System.out.println(url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer 4JJv5shH4stPPykkC8aQ")
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                try {
                    JSONArray array = (JSONArray) parser.parse(response.body());
                    if (!array.isEmpty()) {
                        JSONObject firstObj = (JSONObject) array.get(0);
                        String state = (String) firstObj.get("state");
                        String webUrl = (String) firstObj.get("web_url");
                        states.put(service.getServiceName(), new Data(state, webUrl));
                    } else {
                        states.put(service.getServiceName(), new Data("missing", "MR is not created yet"));
                    }
                } catch (ParseException e) {
                    states.put(service.getServiceName(), new Data("exception", "Exception while fetching details"));
                }
            } else {
                states.put(service.getServiceName(), new Data("error", "Error while fetching details"));
            }

            Thread.sleep(1_000); // consider configurable delay
        }
        return states;
    }

    private static void sentEmail(Map<String, Data> MRState, String releaseBranch) {
        StringBuilder body = new StringBuilder("""
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="UTF-8">
                <title>MR Status Report</title>
                <style>
                    body {
                        font-family: Arial, Helvetica, sans-serif;
                        background-color: #fafafa;
                        margin: 20px;
                        color: #333;
                    }
                
                    h2 {
                        margin-bottom: 10px;
                        color: #2c3e50;
                    }
                
                    p {
                        margin-bottom: 15px;
                        font-size: 14px;
                    }
                
                    table {
                        border-collapse: collapse;
                        width: 100%;
                        margin-bottom: 20px;
                        box-shadow: 0 2px 6px rgba(0,0,0,0.1);
                    }
                
                    th, td {
                        border: 1px solid #ccc;
                        padding: 8px 12px;
                        text-align: left;
                        font-size: 13px;
                    }
                
                    th {
                        background-color: #f0f0f0;
                        font-weight: bold;
                    }
                
                    /* State colors */
                    .merged { background-color: #d9fdd3; }       /* soft green */
                    .opened { background-color: #F7C6C6; }       /* soft red */
                    .closed { background-color: #FDE0E0; }       /* very light red */
                    .locked { background-color: #FDE0E0; }
                    .missing { background-color: #E57373; }      /* muted red */
                    .exception { background-color: #fff9c4; }    /* soft yellow */
                    .error { background-color: #fff9c4; }
                    .na { background-color: #d9fdd3; }           /* soft green */
                
                    pre {
                        background-color: #f9f9f9;
                        border: 1px solid #ddd;
                        padding: 10px;
                        font-size: 13px;
                        border-radius: 4px;
                    }
                
                    .note {
                        background-color: #fdfdfd;
                        border-left: 4px solid #2196f3;
                        padding: 10px;
                        margin-top: 20px;
                        font-size: 13px;
                        color: #444;
                    }
                </style>
                </head>
                <body>
                    <h2>MR Status Report</h2>
                    <p>Providing the current state of merge requests (MRs) for jars and services referenced on the Confluence page, included in yesterday’s release with branch name <b>(""");
        body.append(releaseBranch);
        body.append("""
                )</b>.</p>
                
                    <table>
                        <tr>
                            <th>S.No</th>
                            <th>Service</th>
                            <th>MR State</th>
                            <th>MR Link</th>
                        </tr>
                """);
        int counter = 1;
        for (Map.Entry<String, Data> entry : MRState.entrySet()) {
            Data data = entry.getValue();
            body.append("<tr class='").append(data.getMRState().toLowerCase()).append("'><td>")
                    .append(counter++).append("</td><td>")
                    .append(entry.getKey()).append("</td><td>")
                    .append(data.getMRState()).append("</td>")
                    .append(data.getMRLink()).append("</td></tr>");
        }
        body.append("""
                </table>
                    <p><b>MR States:</b></p>
                    <pre>
                merged      : MR is merged into master
                opened      : MR is created but not merged
                closed      : MR was created but is closed
                locked      : MR was created but is locked
                missing     : MR is not created
                exception   : Exception while finding MR
                error       : Error while finding MR </pre>
                
                    <div class="note">
                        <b>Note:</b> MRs not listed on the Confluence page, especially for jars, are excluded from this report.
                    </div>
                </body>
                </html>""");
        System.out.println(body);
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.starttls.enable", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("saurabh.mittal@tothenew.com", "ccce ndpm zuti mcmf");
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress("saurabh.mittal@tothenew.com"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse("saurabh.mittal@tothenew.com"));
//            message.setRecipients(Message.RecipientType.CC, InternetAddress.parse("manish.jain@tothenew.com,ajay.yadav@tothenew.com,kapil.bajaj@tothenew.com,vagish.dixit@tothenew.com,pranshu.prashar@tothenew.com,pankaj.goyal@tothenew.com,praveen.kumar2@tothenew.com,prashant.rankawat@tothenew.com,vikash.negi@tothenew.com"));
            message.setSubject("MR Status Report (" + releaseBranch + ")");
            message.setContent(body.toString(), "text/html; charset=utf-8");
            Transport.send(message);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }


}
