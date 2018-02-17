package samrock.converters.mangarock;
import static sam.properties.myconfig.MyConfig.MANGAROCK_DB_BACKUP;
import static sam.properties.myconfig.MyConfig.MANGAROCK_INPUT_DB;
import static samrock.converters.mangarock.MangarockManga.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import sam.console.ansi.ANSI;
import sam.sql.sqlite.SqliteManeger;
import sam.sql.sqlite.querymaker.QueryMaker;

public class MangarockDB implements AutoCloseable {
    private final SqliteManeger maneger;
    private final String path;

    public MangarockDB() throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        path = Files.exists(Paths.get(MANGAROCK_INPUT_DB)) ? MANGAROCK_INPUT_DB : Files.exists(Paths.get(MANGAROCK_DB_BACKUP)) ? MANGAROCK_DB_BACKUP : null;
        if(path == null)
            throw new SQLException("File not found: "+MANGAROCK_INPUT_DB + " |nor| "+MANGAROCK_DB_BACKUP);
        
        System.out.println(ANSI.yellow("manga_db: ")+path);
        maneger = new SqliteManeger(path, true);
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
        
        List<MangarockManga> list = new ArrayList<>();
        maneger.executeQueryIterate(sql, rs -> list.add(new MangarockManga(rs)));
        return list;
    }
    
    public List<MangarockManga> getMangas(int[] mangaIds) throws SQLException {
        return getMangas(mangaIds, "");
    }
    public ArrayList<MangarockDownloadTask> readDownloadTasks() throws SQLException {
        ArrayList<MangarockDownloadTask> list = new ArrayList<>();
        maneger.executeQueryIterate("SELECT chapter_name, dir_name, manga_id FROM DownloadTask", rs -> list.add(new MangarockDownloadTask(rs)));
        return list;
    }
    @Override
    public void close() throws Exception {
        maneger.close();
    }
    
}
