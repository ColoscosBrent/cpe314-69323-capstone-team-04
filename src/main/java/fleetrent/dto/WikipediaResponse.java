package fleetrent.dto;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * The outer shape of the Wikipedia search answer. The pages are read later into
 * {@link WikipediaPageDto}. Wikipedia leaves out "query" when nothing matched.
 */
public class WikipediaResponse {
    /** The "query" part of the answer. */
    public static class Query {
        @SerializedName("pages")
        private Map<String, JsonElement> pages;

        /** Returns the raw pages by page id, or null when there are none. */
        public Map<String, JsonElement> getPages() {
            return pages;
        }
    }

    @SerializedName("query")
    private Query query;

    /** Returns the query part, or null when nothing matched. */
    public Query getQuery() {
        return query;
    }
}
