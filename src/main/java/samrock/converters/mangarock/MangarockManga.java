package samrock.converters.mangarock;

import static samrock.converters.extras.Utils.qm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MangarockManga {
    public static final String AUTHOR = "author";
    public static final String CATEGORIES = "categories";
    public static final String DESCRIPTION = "description";
    public static final String NAME = "name";
    public static final String TOTAL_CHAPTERS = "totalChapters";
    public static final String LAST_UPDATE = "lastUpdate";
    public static final String MANGA_ID = "_id";
    public static final String STATUS = "status";
    public static final String STRING  = "String ";
    public static final String RANK = "rank";
    
    public static final String TABLE_NAME = "Manga";
    
    private final String author;
    private final String categories;
    private final String description;
    private final String name;
    private final int totalChapters;
    private final long lastUpdate;
    private final int _id;
    private final int status;
    private final int rank;
    
    public static List<MangarockManga> getMangas(MangarockDB db, int[] mangaIds, String extraCondition) throws SQLException {
        String sql = qm().select(AUTHOR,CATEGORIES,DESCRIPTION,NAME,TOTAL_CHAPTERS,LAST_UPDATE,MANGA_ID,STATUS,STRING,RANK )
                .from(TABLE_NAME)
                .where(w -> w.in(MANGA_ID, mangaIds)).build() + " "+extraCondition;
        
        List<MangarockManga> list = new ArrayList<>();
        db.getManeger().executeQueryIterate(sql, rs -> list.add(new MangarockManga(rs)));
        return list;
    }
    
    public static List<MangarockManga> getMangas(MangarockDB db, int[] mangaIds) throws SQLException {
        return getMangas(db, mangaIds, "");
    }
    
    public MangarockManga(ResultSet rs)  throws SQLException {
        this.author = rs.getString(AUTHOR);
        this.categories = rs.getString(CATEGORIES);
        this.description = rs.getString(DESCRIPTION);
        this.name = rs.getString(NAME);
        this.totalChapters = rs.getInt(TOTAL_CHAPTERS);
        this.lastUpdate = rs.getLong(LAST_UPDATE);
        this._id = rs.getInt(MANGA_ID);
        this.status = rs.getInt(STATUS);
        this.rank = rs.getInt(RANK);
    }
    public String getAuthor() {
        return author;
    }
    public String getCategories() {
        return categories;
    }
    public String getDescription() {
        return description;
    }
    public String getName() {
        return name;
    }
    public int getTotalChapters() {
        return totalChapters;
    }
    public long getLastUpdate() {
        return lastUpdate;
    }
    public int getMangaId() {
        return _id;
    }
    public int getStatus() {
        return status;
    }
    public int getRank() {
        return rank;
    }

    
}
