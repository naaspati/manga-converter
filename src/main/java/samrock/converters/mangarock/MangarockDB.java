package samrock.converters.mangarock;
import static sam.config.MyConfig.MANGAROCK_DB_BACKUP;
import static sam.config.MyConfig.MANGAROCK_INPUT_DB;
import static sam.console.ANSI.yellow;
import static sam.sql.querymaker.QueryMaker.qm;
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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import sam.console.ANSI;
import sam.logging.MyLoggerFactory;
import sam.manga.samrock.Renamer;
import sam.manga.samrock.mangas.MinimalManga;
import sam.sql.sqlite.SQLiteDB;
import samrock.converters.cleanupupdate.MangaDirs.Dir;
public class MangarockDB implements AutoCloseable {
	public final SQLiteDB maneger;
	private final String path;

	public MangarockDB() throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException {
		path = Files.exists(Paths.get(MANGAROCK_INPUT_DB)) ? MANGAROCK_INPUT_DB : Files.exists(Paths.get(MANGAROCK_DB_BACKUP)) ? MANGAROCK_DB_BACKUP : null;
		if(path == null)
			throw new SQLException("File not found: "+MANGAROCK_INPUT_DB + " |nor| "+MANGAROCK_DB_BACKUP);

		MyLoggerFactory.logger(MangarockDB.class).info(yellow("manga_db: ")+path);
		maneger = new SQLiteDB(path);
	}

	public String getPath() {
		return path;
	}
	public List<MangarockDownloadTask> readDownloadTasks() throws SQLException {
		ArrayList<MangarockDownloadTask> list = maneger.collect("SELECT chapter_name, dir_name, manga_id FROM DownloadTask",new ArrayList<>(), MangarockDownloadTask::new);
		return list;
	}
	@Override
	public void close() throws Exception {
		maneger.close();
	}

	public Map<Dir, MangarockManga> load(final Map<Dir, MinimalManga> map, String extraCondition) throws SQLException {
		Map<Integer, Dir> ids = new HashMap<>();
		Map<String, Dir> nulla = new HashMap<>();

		map.forEach((s,t) -> {
			if(t == null)
				nulla.put(s.name, s);
			else
				ids.put(t.getMangaId(), s);
		});

		if(!nulla.isEmpty()) {
			System.out.println(ANSI.yellow("finding ids by dirname"));
			System.out.println(ANSI.cyan("  searching in favorites"));
			System.out.println("    "+String.join("\n    ", nulla.keySet()));
			System.out.println();

			maneger.iterateStoppable("SELECT manga_name,manga_id from Favorites", rs -> {
				String name = rs.getString("manga_name");
				int id = rs.getInt("manga_id");

				Dir d = nulla.remove(Renamer.mangaDirName(name));
				if(d != null)
					ids.put(id, d);

				return !nulla.isEmpty();
			});

			if(!nulla.isEmpty()) {
				System.out.println(ANSI.cyan("  searching in Manga"));
				System.out.println("    "+String.join("\n    ", nulla.keySet()));
				System.out.println();
				
				maneger.iterateStoppable("SELECT name,_id from Manga", rs -> {
					String name = rs.getString("name");
					int id = rs.getInt("_id");

					Dir d = nulla.remove(Renamer.mangaDirName(name));
					if(d != null)
						ids.put(id, d);
					
					return !nulla.isEmpty();
				});	
			}
			
			if(!nulla.isEmpty())
				throw new IllegalMonitorStateException("no Manga found for dirname"+nulla.keySet());
		}

		String sql = qm().select(AUTHOR,CATEGORIES,DESCRIPTION,NAME,TOTAL_CHAPTERS,LAST_UPDATE,MANGA_ID,STATUS,RANK )
				.from(TABLE_NAME)
				.where(w -> w.in(MANGA_ID, ids.keySet())).build() + " "+extraCondition;

		Map<Dir, MangarockManga> result = new IdentityHashMap<>();

		maneger.iterate(sql, rs -> {
			MangarockManga m = new MangarockManga(rs);
			result.put(Objects.requireNonNull(ids.get(m.getMangaId())), m);
		});
		
		if(result.size() != map.size())
			throw new IllegalStateException(String.format("mangas not found for ids: result.size(%s) != map.size(%s)", result.size(), map.size()));
			
		return result;
	}

}
