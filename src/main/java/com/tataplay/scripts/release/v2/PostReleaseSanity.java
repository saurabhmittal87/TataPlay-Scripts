package com.tataplay.scripts.release.v2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tataplay.scripts.branches.Application;
import com.tataplay.scripts.branches.TataPlayUtil;
import com.tataplay.scripts.branches.enums.Environment;
import com.tataplay.scripts.release.Service;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import jakarta.mail.*;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PostReleaseSanity {
    private static Map<String, Map<String, List<Application>>> oldDependencyReport = new HashMap<>();
    private static Map<String, Map<String, List<Application>>> newDependencyReport = new HashMap<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE_DIRECTORY = "temp";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter reportFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    ;
    private static Pattern pattern = Pattern.compile("videoready-tatasky/([^/]+)/");
    private static String releaseBranch = "release-23-12-2025-E";

    public static Map<String, Map<String, List<Application>>> readTree(File file) throws Exception {
        return MAPPER.readValue(file, new TypeReference<>() {
        });
    }

    public static void main(String[] args) throws Exception {
        readOldDependency();
        readNewDependency();
        Set<Service> jarsUpdated = getUpdatedJars();
        Set<Service> uniqueServicesFromConfluence = collectServices();
        jarsUpdated.addAll(uniqueServicesFromConfluence);
        Map<String, Service> report = checkMergeStatus(jarsUpdated, releaseBranch);
        sentEmail(report, releaseBranch);
    }

    private static void readOldDependency() throws Exception {
        String homeDir = System.getProperty("user.home");
        File baseFolder = new File(homeDir, BASE_DIRECTORY);
        if (!baseFolder.exists()) {
            baseFolder.mkdir();
        }
        File file = new File(homeDir, BASE_DIRECTORY + "/dependencyTree_" + "26122025" + ".json");
        if (file.exists()) {
            oldDependencyReport = readTree(file);
        }
    }

    private static void readNewDependency() throws Exception {
        String homeDir = System.getProperty("user.home");
        File file = new File(homeDir, BASE_DIRECTORY + "/dependencyTree_" + LocalDate.now().format(formatter) + ".json");
        if (!file.exists()) {
            newDependencyReport = TataPlayUtil.fetchLatestDependencyMap(Environment.PRODUCTION, null);
            writeTree(newDependencyReport, file);
        } else {
            newDependencyReport = readTree(file);
        }
    }

    private static List<String> commonServicesJars = Arrays.asList("common-constants", "common-pojo", "common-sql-domains", "pubnub-router-client", "search-utils", "third-party-utils");

    private static Set<Service> getUpdatedJars() {
        Set<Service> updatedJars = new HashSet<>();
        for (String jar : newDependencyReport.keySet()) {
            Map<String, List<Application>> newVersionsMap = newDependencyReport.get(jar);
            Map<String, List<Application>> oldVersionsMap = oldDependencyReport.get(jar);

            Set<String> newVersions = newVersionsMap.keySet();
            Set<String> oldVersions = oldVersionsMap.keySet();
            newVersions.removeAll(oldVersions);
            if (!newVersions.isEmpty()) {
                String serviceName = commonServicesJars.contains(jar) ? "common-services" : jar;
                updatedJars.add(new Service(null, serviceName, null, null, false, ServiceType.JAR));
            }
        }
        return updatedJars;
    }

    private static Set<Service> collectServices() throws FileNotFoundException {
        Set<Service> services = new HashSet<>();
        try (Scanner scanner = new Scanner(new File("/home/saurabh-mittal/Downloads/release.csv"))) {
            if (scanner.hasNextLine()) scanner.nextLine(); // skip header

            while (scanner.hasNextLine()) {
                String[] parts = scanner.nextLine().trim().split(",");
                String applicationName = getValue(parts, 1);
                String serviceName = getValue(parts, 2);
                String tag = getValue(parts, 3);
                String MR = getValue(parts, 6);
                String serviceNameViaMatcher = null;
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

    private static Map<String, Service> checkMergeStatus(Set<Service> services, String releaseBranch)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        Map<String, Service> states = new LinkedHashMap<>();
        String query = String.join("&",
                "source_branch=" + releaseBranch,
                "target_branch=master");
//                "state=merged");

        JSONParser parser = new JSONParser();

        for (Service service : services) {
            if (StringUtils.equalsIgnoreCase(service.getTag(), "restart")) {
                service.setMRState("NA");
                service.setMRLink("MR not required as service was just restarted");
                states.put(service.getServiceName(), service);
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
                        service.setMRState(state);
                        service.setMRLink(webUrl);
                        states.put(service.getServiceName(), service);
                    } else {
                        service.setMRState("missing");
                        service.setMRLink("MR is not created yet");
                        states.put(service.getServiceName(), service);
                    }
                } catch (ParseException e) {
                    service.setMRState("exception");
                    service.setMRLink("Exception while fetching details");
                    states.put(service.getServiceName(), service);
                }
            } else {
                service.setMRState("error");
                service.setMRLink("Error while fetching details");
            }
            Thread.sleep(1_000); // consider configurable delay
        }
        return states;
    }

    private static void sentEmail(Map<String, Service> MRState, String releaseBranch) {
        StringBuilder body = new StringBuilder("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Release Summary(");
        body.append(LocalDate.now().format(reportFormatter));
        body.append("""
                    <title>Release Summary(27 December 2025</title>
                    <style> body {
                                  font-family: "Segoe UI", Tahoma, Geneva, Verdana, sans-serif;
                                  margin: 40px;
                                  background-color: #f4f6f9;
                                  color: #333;
                                }
                            h1 {
                                color: #2c3e50;
                                margin-bottom: 10px;
                              }
                            h2 {
                                color: #34495e;
                                margin-top: 40px;
                                margin-bottom: 10px;
                              }
                            table {
                                border-spacing: 0;
                                margin-top: 15px;
                                background-color: #fff;
                                border-radius: 8px;
                                overflow: hidden;
                                box-shadow: 0 2px 6px rgba(0,0,0,0.1);
                                width: 100%;
                                border-collapse: collapse;
                                table-layout: fixed;
                            }
                            th, td {
                                padding: 14px 16px;
                                text-align: left;
                                word-wrap: break-word;
                              }
                            th {
                              background-color: #2c3e50;
                              color: #fff; font-weight: 600;
                            }
                            tr:hover {
                                background-color: #eef3f7;
                            }
                            a {
                              color: #2980b9;
                              text-decoration: none;
                              font-weight: 500;
                            } a:hover {
                            text-decoration: underline;
                            }
                            th:nth-child(1),
                            td:nth-child(1) { width: 10%; }
                            th:nth-child(2),
                            td:nth-child(2) { width: 30%; }
                            th:nth-child(3),
                            td:nth-child(3) { width: 30%; }
                            th:nth-child(4),
                            td:nth-child(4) { width: 30%; }
                            .status-success { color: #27ae60; font-weight: bold; }
                            .status-pending { color: #f39c12; font-weight: bold; }
                            .status-failed { color: #e74c3c; font-weight: bold; }
                            .report-date { font-style: italic; color: #555; }\s
                            .merged { background-color: #d9fdd3; }       /* soft green */
                            .opened { background-color: #F7C6C6; }       /* soft red */
                            .closed { background-color: #FDE0E0; }       /* very light red */
                            .locked { background-color: #FDE0E0; }
                            .missing { background-color: #E57373; }      /* muted red */
                            .exception { background-color: #fff9c4; }    /* soft yellow */
                            .error { background-color: #fff9c4; }
                            .na { background-color: #d9fdd3; }           /* soft green */
                                </style>
                </head>
                
                <body>
                    <h1>Release Summary</h1>
                    <p class="report-date">Date:
                """);
        body.append(LocalDate.now().format(reportFormatter));
        body.append("</p><h2>Jar Updates</h2><table><tr><th>S.No</th><th>Jar Name</th><th>MR Status</th><th>MR Link</th></tr>");
        int counter = 1;
        for (Map.Entry<String, Service> entry : MRState.entrySet()) {
            Service data = entry.getValue();
            if (ServiceType.JAR.equals(data.getServiceType())) {
                body.append("<tr class='").append(data.getMRState().toLowerCase()).append("'>")
                        .append("<td>").append(counter++).append("</td>")
                        .append("<td>").append(entry.getKey()).append("</td>")
                        .append("<td>").append(data.getMRState()).append("</td>")
                        .append("<td>").append("<a href='").append(data.getMRLink()).append("' target='_blank'>View MR</a></td>");
                body.append("</tr>");
            }
        }
        body.append("</table>");
        body.append("<h2>Services Deployed</h2><table><tr><th>S.No</th><th>Service Name</th><th>MR Status</th><th>MR Link</th></tr>");
        counter = 1;
        for (Map.Entry<String, Service> entry : MRState.entrySet()) {
            Service data = entry.getValue();
            if (!ServiceType.JAR.equals(data.getServiceType())) {
                body.append("<tr class='").append(data.getMRState().toLowerCase()).append("'>")
                        .append("<td>").append(counter++).append("</td>")
                        .append("<td>").append(entry.getKey()).append("</td>")
                        .append("<td>").append(data.getMRState()).append("</td>")
                        .append("<td>").append("<a href='").append(data.getMRLink()).append("' target='_blank'>View MR</a></td>");
                body.append("</tr>");
            }
        }
        body.append("</table></body></html>");
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
            message.setSubject("Release Summary( (" + LocalDate.now().format(reportFormatter) + ")");
            message.setContent(body.toString(), "text/html; charset=utf-8");
            Transport.send(message);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    private static String getValue(String[] arr, int index) {
        try {
            return arr[index].trim();
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    private static void writeTree(Map<String, Map<String, List<Application>>> dependencyTree, File file) throws Exception {
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, dependencyTree);
    }
}
