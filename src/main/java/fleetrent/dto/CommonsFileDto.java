package fleetrent.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * One picture file of the Wikimedia Commons search answer: its title, its search rank and the
 * address of a smaller copy of the picture.
 */
public class CommonsFileDto {
    /** The picture itself: where a copy of the asked width is, and what kind of file it is. */
    public static class ImageInfo {
        @SerializedName("thumburl")
        private String thumbUrl;
        @SerializedName("mime")
        private String mime;

        /** Returns the web address of the smaller copy, or null when it is missing. */
        public String getThumbUrl() {
            return thumbUrl;
        }

        /** Returns the kind of file (for example image/jpeg), or null when it is missing. */
        public String getMime() {
            return mime;
        }
    }

    @SerializedName("title")
    private String title;
    @SerializedName("index")
    private Integer index;
    @SerializedName("imageinfo")
    private List<ImageInfo> imageInfo;

    /** Returns the file title (for example "File:Toyota Camry red.jpg"), or null. */
    public String getTitle() {
        return title;
    }

    /** Returns the search rank (1 is the best hit), or null when it is missing. */
    public Integer getIndex() {
        return index;
    }

    /** Returns the first picture entry of the file, or null when the file has none. */
    public ImageInfo getImage() {
        return imageInfo == null || imageInfo.isEmpty() ? null : imageInfo.get(0);
    }
}
