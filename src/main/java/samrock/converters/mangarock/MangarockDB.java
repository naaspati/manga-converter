package samrock.converters.mangarock;
import static sam.config.MyConfig.MANGAROCK_DB_BACKUP;
import static sam.config.MyConfig.MANGAROCK_INPUT_DB;
import static samrock.converters.mangarock.MangarockManga.AUTHOR;
import static samrock.converters.mangarock.MangarockManga.CATEGORIES;
import static samrock.converters.mangarock.MangarockManga.DESCRIPTION;
import static samrock.converters.mangarock.MangarockManga.LAST_UPDATE;
import static samrock.converters.mangarock.MangarockManga.MANGA_ID;
import static samrock.converters.mangarock.MangarockManga.NAME;
import static samrock.converters.mangarock.MangarockManga.RANK;
import static samrock.converters.mangarock.MangarockManga.STATUS;
import static samrock.converters.mangarock.MangarockManga.TABLE_NAME;
import static samrock.converters.mangarock.MangarockManga.TOTAL_CHAPTERS;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import sam.console.ANSI;
import sam.sql.querymaker.QueryMaker;
import sam.sql.sqlite.SQLiteDB;

public class MangarockDB implements AutoCloseable {
    public final SQLiteDB maneger;
    private final String path;

    public MangarockDB() throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        path = Files.exists(Paths.get(MANGAROCK_INPUT_DB)) ? MANGAROCK_INPUT_DB : Files.exists(Paths.get(MANGAROCK_DB_BACKUP)) ? MANGAROCK_DB_BACKUP : null;
        if(path == null)
            throw new SQLException("File not found: "+MANGAROCK_INPUT_DB + " |nor| "+MANGAROCK_DB_BACKUP);
        
        Logger.getLogger(MangarockDB.class.getName()).info(ANSI.yellow("manga_db: ")+path);
        maneger = new SQLiteDB(path);
    }

    public String getPath() {
        return path;
    }
    private QueryMaker qm() {
        return QueryMaker.getInstance();
    }
    public List<MangarockManga> getMangas(int[] mangaIds, String extraCondition) throws SQLException {
        String sql = qm().select(AUTHOR,CATEGORIES,DESCRIPTION,NAME,TOTAL_CHAPTERS,LAST_UPDATE,MANGA_ID,STATUS,RANK )
                .from(TABLE_NAME)
                .where(w -> w.in(MANGA_ID, mangaIds)).build() + " "+extraCondition;
        
        List<MangarockManga> list = maneger.collect(sql, new ArrayList<>(), MangarockManga::new);
        return list;
    }
    
    public List<MangarockManga> getMangas(int[] mangaIds) throws SQLException {
        return getMangas(mangaIds, "");
    }
    public List<MangarockDownloadTask> readDownloadTasks() throws SQLException {
        ArrayList<MangarockDownloadTask> list = maneger.collect("SELECT chapter_name, dir_name, manga_id FROM DownloadTask",new ArrayList<>(), MangarockDownloadTask::new);
        return list;
    }
    @Override
    public void close() throws Exception {
        maneger.close();
    }
    
}
