package fleetrent.dto;

import com.google.gson.annotations.SerializedName;

/** One page of the Wikipedia search answer: its title, its search rank and its thumbnail. */
public class WikipediaPageDto {
    /** The small picture of a page. */
    public static class Thumbnail {
        @SerializedName("source")
        private String source;

        /** Returns the web address of the picture, or null when it is missing. */
        public String getSource() {
            return source;
        }
    }

    @SerializedName("title")
    private String title;
    @SerializedName("index")
    private Integer index;
    @SerializedName("thumbnail")
    private Thumbnail thumbnail;

    /** Returns the page title, or null when it is missing. */
    public String getTitle() {
        return title;
    }

    /** Returns the search rank (1 is the best hit), or null when it is missing. */
    public Integer getIndex() {
        return index;
    }

    /** Returns the thumbnail, or null when the page has none. */
    public Thumbnail getThumbnail() {
        return thumbnail;
    }
}
