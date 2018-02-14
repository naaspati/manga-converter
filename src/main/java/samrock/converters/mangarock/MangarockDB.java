package samrock.converters.mangarock;

import static sam.properties.myconfig.MyConfig.MANGAROCK_DB_BACKUP;
import static sam.properties.myconfig.MyConfig.MANGAROCK_INPUT_DB;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;

import sam.console.ansi.ANSI;
import sam.sql.sqlite.SqliteManeger;

public class MangarockDB implements AutoCloseable {
    private SqliteManeger maneger;
    private final String path;

    public MangarockDB() throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        path = Files.exists(Paths.get(MANGAROCK_INPUT_DB)) ? MANGAROCK_INPUT_DB : Files.exists(Paths.get(MANGAROCK_DB_BACKUP)) ? MANGAROCK_DB_BACKUP : null;
        if(path == null)
            throw new SQLException("File not found: "+MANGAROCK_INPUT_DB + " |nor| "+MANGAROCK_DB_BACKUP);
        
        System.out.println(ANSI.yellow("manga_db: ")+path);
        setManeger(new SqliteManeger(path, true));
    }

    public String getPath() {
        return path;
    }
    public ArrayList<MangarockDownloadTask> readDownloadTasks() throws SQLException {
        ArrayList<MangarockDownloadTask> list = new ArrayList<>();
        getManeger().executeQueryIterate("SELECT chapter_name, dir_name, manga_id FROM DownloadTask", rs -> list.add(new MangarockDownloadTask(rs)));
        return list;
    }
    @Override
    public void close() throws Exception {
        getManeger().close();
    }

    public SqliteManeger getManeger() {
        return maneger;
    }

    public void setManeger(SqliteManeger maneger) {
        this.maneger = maneger;
    }
}
