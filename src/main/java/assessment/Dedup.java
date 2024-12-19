package assessment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Dedup {
	
	public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.out.println("Usage: java Dedup <inputFilePath> <outputFilePath> <logFilePath>");
            return;
        }

        String inputFilePath = args[0];
        String outputFilePath = args[1];
        String logFilePath = args[2];

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root = objectMapper.readTree(new File(inputFilePath));

        if (!root.has("leads") || !root.get("leads").isArray()) {
            System.out.println("Input JSON should have a 'leads' array.");
            return;
        }

        ArrayNode inputArray = (ArrayNode) root.get("leads");
        ArrayNode outputArray = objectMapper.createArrayNode();
        FileWriter logWriter = new FileWriter(logFilePath);

        Map<String, JsonNode> idMap = new HashMap<>();
        Map<String, JsonNode> emailMap = new HashMap<>();
        Map<JsonNode, Integer> indexMap = new HashMap<>();

        for (int i = 0; i < inputArray.size(); i++) {
            JsonNode record = inputArray.get(i);
            String id = record.has("_id") ? record.get("_id").asText() : null;
            String email = record.has("email") ? record.get("email").asText() : null;
            String dateStr = record.has("entryDate") ? record.get("entryDate").asText() : "1970-01-01T00:00:00";
            LocalDateTime date = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);

            JsonNode existingRecord = null;
            String key = null;

            if (id != null && idMap.containsKey(id)) {
                existingRecord = idMap.get(id);
                key = id;
            } else if (email != null && emailMap.containsKey(email)) {
                existingRecord = emailMap.get(email);
                key = email;
            }

            if (existingRecord == null) {
                outputArray.add(record);
                indexMap.put(record, outputArray.size() - 1);
                if (id != null) idMap.put(id, record);
                if (email != null) emailMap.put(email, record);
            } else {
                String existingDateStr = existingRecord.has("entryDate") ? existingRecord.get("entryDate").asText() : "1970-01-01T00:00:00";
                LocalDateTime existingDate = LocalDateTime.parse(existingDateStr, DateTimeFormatter.ISO_DATE_TIME);

                if (date.isAfter(existingDate) || 
                		// if the dates are equal, then the current record takes precedence according to the requirements,
                    	// since it's the "last" of these two duplicates
                		date.isEqual(existingDate)) { 
                    logDifferences(logWriter, existingRecord, record, objectMapper);
                    
                    if (!indexMap.containsKey(existingRecord)) {
                    	System.out.println("error");
                    	return;
                    }
                    int existingIndex = indexMap.get(existingRecord);
                    outputArray.set(existingIndex, record);
                    indexMap.remove(existingRecord);
                    indexMap.put(record, existingIndex);
                    if (existingRecord.has("_id")) {
                    	idMap.remove(existingRecord.get("_id").asText());
                    }
                    if (existingRecord.has("email")) {
                    	emailMap.remove(existingRecord.get("email").asText());
                    }
                    if (id != null) idMap.put(key, record);
                    if (email != null) emailMap.put(key, record);
                } else {
                    logDifferences(logWriter, record, existingRecord, objectMapper);
                }
            }
        }

        ObjectNode outputRoot = objectMapper.createObjectNode();
        outputRoot.set("leads", outputArray);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputFilePath), outputRoot);
        logWriter.close();
    }

    private static void logDifferences(FileWriter logWriter, JsonNode source, JsonNode output, ObjectMapper objectMapper) throws IOException {
        logWriter.write("\nDuplicate Found:\n");
        logWriter.write("Source:\n" + prettyPrint(source, objectMapper) + "\n");
        logWriter.write("Output:\n" + prettyPrint(output, objectMapper) + "\n");

        Iterator<String> fieldNames = source.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!output.has(fieldName) || !source.get(fieldName).equals(output.get(fieldName))) {
                logWriter.write(String.format("Field: %s, Source Value: %s, Output Value: %s\n",
                        fieldName,
                        source.has(fieldName) ? source.get(fieldName).asText() : "<missing>",
                        output.has(fieldName) ? output.get(fieldName).asText() : "<missing>"));
            }
        }
        logWriter.write("\n");
    }
    
    private static String prettyPrint(JsonNode node, ObjectMapper objectMapper) throws IOException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }
}
