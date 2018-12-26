package samrock.converters.cleanupupdate;

import static sam.manga.samrock.mangas.MangasMeta.*;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_ID;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_NAME;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;

import sam.manga.samrock.chapters.MinimalChapter;
import sam.manga.samrock.mangas.MinimalManga;
import sam.nopkg.Junk;

class SamrockManga implements MinimalManga {
	public static String[] columns() {
		return new String[]{MANGA_ID, MANGA_NAME, DIR_NAME, LAST_UPDATE_TIME};
	}
    public final int manga_id;
    public final String dir_name, manga_name;
    public final long last_update_time;
    
    public SamrockManga(ResultSet rs) throws SQLException {
        this.manga_id = rs.getInt(MANGA_ID);
        this.manga_name = rs.getString(MANGA_NAME);
        this.dir_name = rs.getString(DIR_NAME);
        this.last_update_time = rs.getLong(LAST_UPDATE_TIME);
    }
    public int getMangaId() {
        return manga_id;
    }
    public String getDirName() {
        return dir_name;
    }
    
	@Override
	public Path getDirPath() {
		return Junk.notYetImplemented();
	}
	@Override
	public String getMangaName() {
		return manga_name;
	}
	@Override
	public Iterable<? extends MinimalChapter> getChapterIterable() {
		return Junk.notYetImplemented();
	}
}
