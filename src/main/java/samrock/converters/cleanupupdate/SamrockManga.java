package samrock.converters.cleanupupdate;

import static sam.manga.samrock.mangas.MangasMeta.DIR_NAME;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_ID;
import static sam.manga.samrock.mangas.MangasMeta.LAST_UPDATE_TIME;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import sam.manga.samrock.SamrockDB;
import sam.manga.samrock.mangas.MangaUtils;

class SamrockManga {
    static List<SamrockManga> loadAll(SamrockDB db) throws SQLException{
        List<SamrockManga> list = new ArrayList<>();
        
        new MangaUtils(db).selectAll(rs -> list.add(new SamrockManga(rs)), MANGA_ID, DIR_NAME, LAST_UPDATE_TIME);
        return list;
    }
    
    private final int manga_id;
    private final long last_update_time;
    private final String dir_name;
    
    public SamrockManga(ResultSet rs) throws SQLException {
        this.manga_id = rs.getInt(MANGA_ID);
        this.last_update_time = rs.getLong(LAST_UPDATE_TIME);
        this.dir_name = rs.getString(DIR_NAME);
    }
    public int getMangaId() {
        return manga_id;
    }

    public long getLastUpdateTime() {
        return last_update_time;
    }
    public String getDirName() {
        return dir_name;
    }
}
