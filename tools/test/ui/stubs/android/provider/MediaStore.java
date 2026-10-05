package android.provider;

import android.net.Uri;

/**
 * Harness stand-in for the pieces of android.provider.MediaStore the app uses to
 * publish a finished recording into the public Downloads collection.
 */
public class MediaStore {

    public interface MediaColumns {
        String DISPLAY_NAME = "_display_name";
        String MIME_TYPE = "mime_type";
        String RELATIVE_PATH = "relative_path";
        String IS_PENDING = "is_pending";
        String SIZE = "_size";
        String DATE_ADDED = "date_added";
    }

    public static class Downloads implements MediaColumns {
        public static final Uri EXTERNAL_CONTENT_URI =
                Uri.parse("content://media/external/downloads");
        public static final Uri getContentUri(String volumeName) {
            return Uri.parse("content://media/" + volumeName + "/downloads");
        }
    }

    public static class Audio implements MediaColumns {
        public static final Uri EXTERNAL_CONTENT_URI =
                Uri.parse("content://media/external/audio/media");
    }

    public static class Media {
        public static final Uri EXTERNAL_CONTENT_URI =
                Uri.parse("content://media/external/file");
    }
}
