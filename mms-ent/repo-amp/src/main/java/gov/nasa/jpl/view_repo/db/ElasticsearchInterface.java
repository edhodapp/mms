package gov.nasa.jpl.view_repo.db;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

public interface ElasticsearchInterface {
    void init(String elasticHost);

    void close();

    void createIndex(String index) throws IOException;

    void deleteIndex(String index) throws IOException;

    JsonArray getCommitHistory(String sysmlid, String index) throws IOException;

    JsonArray getElementsFromElasticIds(List<String> ids, String index) throws IOException;

    ElasticResult indexElement(JsonObject j, String index, String eType) throws IOException;

    boolean refreshIndex() throws IOException;

    boolean bulkIndexElements(JsonArray bulkElements, String operation, boolean refresh, String index, String type) throws IOException;

    boolean bulkUpdateElements(Set<String> elements, String payload, String index, String type) throws IOException;

    JsonObject search(JsonObject queryJson) throws IOException;

    JsonObject bulkDeleteByType(Set<String> ids, String index, String type);

    JsonObject getElementsLessThanOrEqualTimestamp(String sysmlId, String timestamp, List<String> refsCommitIds, String index);

    Map<String, String> getDeletedElementsFromCommits(List<String> commitIds, String index);
}
