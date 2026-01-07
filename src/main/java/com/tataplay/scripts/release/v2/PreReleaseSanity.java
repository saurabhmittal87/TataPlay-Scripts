package com.tataplay.scripts.release.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tataplay.scripts.branches.Application;
import com.tataplay.scripts.branches.TataPlayUtil;
import com.tataplay.scripts.branches.enums.Environment;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
public class PreReleaseSanity {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final String BASE_DIRECTORY = "temp";

    public static void main(String[] args) throws Exception {
        String homeDir = System.getProperty("user.home");
        File baseFolder = new File(homeDir, BASE_DIRECTORY);
        if (!baseFolder.exists()) {
            baseFolder.mkdir();
        }
        File file = new File(homeDir, BASE_DIRECTORY + "/dependencyTree_" + LocalDate.now().format(formatter) + ".json");
        if (!file.exists()) {
            Map<String, Map<String, List<Application>>> dependencyTree = TataPlayUtil.fetchLatestDependencyMap(Environment.PRODUCTION, null);
            writeTree(dependencyTree, file);
        }
    }

    public static void writeTree(Map<String, Map<String, List<Application>>> dependencyTree, File file) throws Exception {
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, dependencyTree);
    }
}