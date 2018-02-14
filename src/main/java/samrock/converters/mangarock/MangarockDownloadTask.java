package samrock.converters.mangarock;

import java.sql.ResultSet;
import java.sql.SQLException;

public class MangarockDownloadTask {
    private final int manga_id;
    private final String chapter_name;
    private final String dir_name;
    
    public MangarockDownloadTask(ResultSet rs) throws SQLException {
        this.manga_id = rs.getInt("manga_id");
        this.chapter_name = rs.getString("chapter_name");
        this.dir_name = rs.getString("dir_name");
    }
    int getMangaId() {
        return manga_id;
    }
    String getChapterName() {
        return chapter_name;
    }
    String getDirName() {
        return dir_name;
    }
}
