package samrock.converters.cleanupupdate;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static sam.console.ANSI.green;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.manga.samrock.mangas.MangasMeta.AUTHOR;
import static sam.manga.samrock.mangas.MangasMeta.CATEGORIES;
import static sam.manga.samrock.mangas.MangasMeta.CHAP_COUNT_MANGAROCK;
import static sam.manga.samrock.mangas.MangasMeta.CHAP_COUNT_PC;
import static sam.manga.samrock.mangas.MangasMeta.DESCRIPTION;
import static sam.manga.samrock.mangas.MangasMeta.DIR_NAME;
import static sam.manga.samrock.mangas.MangasMeta.LAST_UPDATE_TIME;
import static sam.manga.samrock.mangas.MangasMeta.MANGAS_TABLE_NAME;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_ID;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_NAME;
import static sam.manga.samrock.mangas.MangasMeta.RANK;
import static sam.manga.samrock.mangas.MangasMeta.STATUS;
import static sam.myutils.Checker.isEmpty;
import static sam.myutils.Checker.isEmptyTrimmed;
import static sam.sql.ResultSetHelper.getInt;
import static sam.sql.querymaker.QueryMaker.qm;
import static sam.string.StringUtils.splitStream;
import static samrock.converters.extras.Utils.APP_DATA;
import static samrock.converters.extras.Utils.MANGAROCK_DB_BACKUP;
import static samrock.converters.extras.Utils.MANGAROCK_INPUT_DB;
import static samrock.converters.extras.Utils.MANGAROCK_INPUT_DIR;
import static samrock.converters.extras.Utils.SAMROCK_DB;
import static samrock.converters.extras.Utils.SELF_DIR;
import static samrock.converters.extras.Utils.confirm;
import static samrock.converters.extras.Utils.subpath;

import java.awt.geom.IllegalPathStateException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import sam.collection.CollectionUtils;
import sam.collection.Iterables;
import sam.config.MyConfig;
import sam.console.ANSI;
import sam.io.serilizers.ObjectReader;
import sam.io.serilizers.ObjectWriter;
import sam.io.serilizers.StringWriter2;
import sam.logging.MyLoggerFactory;
import sam.manga.samrock.SamrockDB;
import sam.manga.samrock.chapters.Chapter;
import sam.manga.samrock.chapters.ChapterUpdate;
import sam.manga.samrock.chapters.ChapterUtils;
import sam.manga.samrock.chapters.ChapterUtils.MangaLog;
import sam.manga.samrock.chapters.MinimalChapter;
import sam.manga.samrock.mangas.MangaUtils;
import sam.manga.samrock.mangas.MinimalManga;
import sam.manga.samrock.meta.TagsMeta;
import sam.manga.samrock.meta.VersioningMeta;
import sam.manga.samrock.thumb.ThumbUtils;
import sam.manga.samrock.thumb.ThumbUtils.ExtraAndMissing;
import sam.myutils.MyUtilsException;
import sam.myutils.System2;
import sam.sql.JDBCHelper;
import sam.sql.querymaker.InserterBatch;
import sam.string.StringBuilder2;
import samrock.converters.cleanupupdate.MangaDirs.Dir;
import samrock.converters.mangarock.MangarockDB;
import samrock.converters.mangarock.MangarockManga;

public class CheckupsAndUpdates implements Callable<Boolean> {
	private static final Logger LOGGER = MyLoggerFactory.logger(CheckupsAndUpdates.class);

	private final Path BACKUP_PATH;
	private final String coloredStart = "\u001b[94;102m Start \u001b[0m";
	private final String coloredEnd = "\u001b[94;102m End \u001b[0m\r\n";

	private final List<String> tags = new ArrayList<>();

	private final MangaDirs mangaDirs;
	private final Map<String, MinimalManga> idmangas;

	public CheckupsAndUpdates(MangaDirs mangaDirs, Map<String, MinimalManga> idmangas) throws IOException {
		this.mangaDirs = mangaDirs;
		this.idmangas = idmangas;
		if(!idmangas.isEmpty()) {
			idmangas.forEach((s,t) -> {
				if(!Objects.equals(s, t.getDirName()))
					throw new IllegalArgumentException(String.format("!Objects.equals(\"%s\", \"%s\")", s, t.getDirName()));
			});
		}
		this.BACKUP_PATH = mangaDirs.createBackupFolder(CheckupsAndUpdates.class);
	}

	private class Dir2 implements MinimalManga {
		final Dir dir;
		SamrockManga _samrock;
		MangarockManga _mangarock;
		MinimalManga _idmanga;

		public Dir2(Dir dir) {
			this.dir = dir;
		}
		public SamrockManga samrock() {
			return _samrock;
		}
		public void samrock(SamrockManga samrock) {
			this._samrock = check(samrock);
			this.dir.current = samrock.last_update_time;
		}
		private <E extends MinimalManga> E check(E e) {
			if(e == null)
				return e;

			int id = e.getMangaId();
			if(_samrock != null && _samrock.manga_id != id)
				throw new IllegalArgumentException();
			if(_mangarock != null && _mangarock.getMangaId() != id)
				throw new IllegalArgumentException();
			if(_idmanga != null && _idmanga.getMangaId() != id)
				throw new IllegalArgumentException();

			return e;
		}
		public MangarockManga mangarock() {
			return _mangarock;
		}
		public void mangarock(MangarockManga mangarock) {
			this._mangarock = check(mangarock);
		}
		public MinimalManga idmanga() {
			return _idmanga;
		}
		public void idmanga(MinimalManga idmanga) {
			this._idmanga = check(idmanga);
		}
		@Override
		public int getMangaId() {
			if(samrock() != null)
				return samrock().manga_id;
			if(mangarock() != null)
				return mangarock().getMangaId();
			if(idmanga() != null)
				return idmanga().getMangaId();

			throw new IllegalStateException();
		}
		@Override
		public String getDirName() {
			return dir.name;
		}
		@Override
		public Path getDirPath() {
			return dir.file.toPath();
		}
		@Override
		public String getMangaName() {
			if(samrock() != null)
				return samrock().getMangaName();
			if(mangarock() != null)
				return mangarock().getMangaName();
			if(idmanga() != null)
				return idmanga().getMangaName();

			throw new IllegalStateException();
		}
		@Override
		public Iterable<? extends MinimalChapter> getChapterIterable() {
			return idmanga() == null ? Iterables.empty() : idmanga().getChapterIterable();
		}
	}

	Map<String, Dir2> dirs = new HashMap<>();
	private SamrockDB samrockDB;
	private MangarockDB mangarockDB;

	@Override
	public Boolean call() throws Exception {
		//input clean up
		setTitle("input clean up");
		cleanInputs();

		mangaDirs.getDirs(null)
		.forEach(d -> dirs.put(d.name, new Dir2(d)));;

		idmangas.forEach((dir, manga) -> {
			Dir2 d = dirs.get(manga.getDirName());
			if(d != null)
				d.idmanga(manga);
			else 
				LOGGER.warning("no dir found for dirname: "+manga.getDirName()+", idManga: "+manga);
		});

		try (SamrockDB samrock = new SamrockDB();
				MangarockDB mangarock = new MangarockDB();) {

			this.samrockDB = samrock;
			this.mangarockDB = mangarock;
			List<SamrockManga> delete = new ArrayList<>();

			samrock.iterate(JDBCHelper.selectSQL(MANGAS_TABLE_NAME, SamrockManga.columns()).toString(), rs -> {
				Dir2 d = dirs.get(rs.getString(DIR_NAME));
				if(d != null)
					d.samrock(new SamrockManga(rs));
				else 
					delete.add(new SamrockManga(rs));
			});

			if(dirs.values().stream().anyMatch(d -> d.samrock() == null)) {
				setTitle("proceessing New Mangas");
				if(!processNewMangas())
					return false;
			} 

			if(!delete.isEmpty()) {
				setTitle("Processing modified chapters");
				deleteMissingMangas(delete);                
			}
			increaseBy1();

			if(!Files.isSameFile(Paths.get(mangarock.getPath()), Paths.get(MyConfig.MANGAROCK_DB_BACKUP))) {
				setTitle("performing total Update");
				performTotalUpdate();
			}
			increaseBy1();

			//report manga not listed in database
			setTitle("Report missing mangas in Database");
			remainingChecks();
			increaseBy1();

			samrock.commit();

			List<MinimalManga> updates = dirs.values().stream()
					.filter(d -> d.dir.isModified() || d.mangarock() != null || d.idmanga() != null)
					.collect(Collectors.toList());

			if(!updates.isEmpty()) {
				List<MangaLog> result =  new ChapterUtils(samrock).completeUpdate(updates);
				StringBuilder2 sb = new StringBuilder2();
				String countFormat = "currentTotal:%s, chapCountPc:%s \nread:%s,  unread:%s \ndelete:%s";

				result.forEach(m -> {
					sb.yellow(m.manga.getMangaId()).append(": ").yellow(m.manga.getDirName()).ln()
					.format(countFormat, m.currentTotal, m.chapCountPc, m.read, m.unread, m.delete).ln();
					m.chapters.forEach(c -> {
						if(c.update != ChapterUpdate.NO_UPDATE)
							sb.append("  ").cyan("["+c.update+"] ").append(c.chapter.getNumber()).append(": ").append(c.chapter.getFileName()).append('\n');
					});
				});
				LOGGER.info(sb.ln().toString());
			}

			if(!tags.isEmpty())
				processTags();

			samrock.commit();
			//preparing mangalist
			setTitle("Preparing manga lists");
			//TODO preparingMangalist(samrock);
			increaseBy1();
		} catch (Exception e) {
			LOGGER.log(SEVERE, "Cleanups AND updates Error", e);
			return false;
		}

		try {
			Path p = SELF_DIR.resolve("db_backups");
			Files.createDirectories(p);
			Files.copy(SAMROCK_DB, p.resolve(SAMROCK_DB.getFileName().toString().replaceFirst(".db$", "") + "_"+LocalDate.now().getDayOfMonth()+ ".db"), REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.log(SEVERE, "samrock database: failed to backup", e);
		}
		return true;
	}

	private void increaseBy1() {
		// TODO Auto-generated method stub

	}

	private void setTitle(String string) {
		System.out.println(yellow(string));
	}
	private void processTags() throws SQLException {
		int[] sam = samrockDB.stream(qm().select(TagsMeta.ID).from(TagsMeta.TABLE_NAME).build(), rs -> rs.getInt(1)).mapToInt(Integer::intValue).toArray();
		Arrays.sort(sam);

		int[] array = tags.stream()
				.flatMapToInt(MangaUtils::tagsToIntStream)
				.filter(i -> Arrays.binarySearch(sam, i) < 0)
				.distinct().toArray();

		if(array.length == 0) return;

		StringBuilder sb = new StringBuilder();
		sb.append(yellow("New Tags\n"));
		samrockDB.prepareStatementBlock(qm().insertInto(TagsMeta.TABLE_NAME).placeholders(TagsMeta.ID, TagsMeta.NAME), ps -> {
			mangarockDB.maneger.iterate(qm().select("distinct categoryId,categoryName").from("SourceCategoryMap").where(w -> w.in("categoryId", array)).build(), rs -> {
				int n;
				String s;
				ps.setInt(1, n = rs.getInt("categoryId"));
				ps.setString(2, s = rs.getString("categoryName"));
				ps.addBatch();
				sb.append(n).append("  ").append(s).append('\n');
			});
			ps.executeBatch();
			return null;
		});
		LOGGER.info(sb.append('\n').toString());
	}

	private String coloredMsgMaker(String s) { return "\u001b[97;104m"+s+"\u001b[0m";}

	private void cleanInputs() {
		if(Files.notExists(MANGAROCK_INPUT_DB) && Files.notExists(MANGAROCK_INPUT_DIR))
			return;

		LOGGER.info(coloredMsgMaker("starting input cleanups  ") + coloredStart);

		if(Files.exists(MANGAROCK_INPUT_DIR)){
			File mangarockInputFolderFile = MANGAROCK_INPUT_DIR.toFile();
			if (!mangarockInputFolderFile.delete()) {
				for (File f : mangarockInputFolderFile.listFiles()) f.delete();
				if(!mangarockInputFolderFile.delete())
					LOGGER.info("Cleanup: com.notabasement.mangarock.android.titan is non empty");
			}
			if(Files.notExists(MANGAROCK_INPUT_DIR))
				LOGGER.info("\t delete: " + mangarockInputFolderFile.getName());
		}
		if(Files.exists(MANGAROCK_INPUT_DB)){
			try {
				Files.copy(MANGAROCK_INPUT_DB, MANGAROCK_DB_BACKUP, REPLACE_EXISTING);
				LOGGER.info("\t backup: " + MANGAROCK_INPUT_DB);
			} catch (IOException e2) {
				LOGGER.log(WARNING, "Error while moving moving mangarock.db", e2);
			}
		}
		LOGGER.info(coloredEnd);
	}
	private boolean processNewMangas() throws InstantiationException, IllegalAccessException, ClassNotFoundException, Exception {
		StringBuilder2 sb = new StringBuilder2();
		sb.append(coloredMsgMaker("Processing new Mangas: ")).append(coloredStart).ln();
		int executes = -1;
		int foundCount = -1;

		try {
			String format1 = "  %-10s%s\n";
			sb.green(String.format(format1, "manga_id", "manga_name"));
			Map<Dir, Dir2> dirs2 = dirs.values().stream().filter(d -> d.samrock() == null).collect(Collectors.toMap(d -> d.dir, d -> d, (o, n) -> {throw new IllegalPathStateException();}, IdentityHashMap::new));
			Map<Dir, MinimalManga> temp = new IdentityHashMap<>();
			dirs2.forEach((s,t) -> temp.put(s, t.idmanga()));

			Map<Dir, MangarockManga> temp2 = mangarockDB.load(temp, "");
			foundCount = temp2.size();
			Map<Dir2, MangarockManga> mangas = new IdentityHashMap<>();
			temp2.forEach((s,t) -> mangas.put(dirs2.get(s), t));

			if(!newMangaConflicts(mangas))
				return false;

			InserterBatch<MangarockManga> insert = new InserterBatch<>(MANGAS_TABLE_NAME);

			insert.setInt(MANGA_ID, MangarockManga::getMangaId);
			insert.setString(DIR_NAME, MangarockManga::getDirName);
			insert.setString(MANGA_NAME, MangarockManga::getName);
			insert.setString(AUTHOR, MangarockManga::getAuthor);
			insert.setString(DESCRIPTION, MangarockManga::getDescription);
			insert.setInt(CHAP_COUNT_MANGAROCK, MangarockManga::getTotalChapters);
			insert.setString(CATEGORIES, MangarockManga::getCategories);
			insert.setInt(STATUS, MangarockManga::getStatus);
			insert.setInt(RANK, MangarockManga::getRank);

			mangas.values().forEach(m -> {
				sb.format(format1, m.getMangaId(), m.getName());
				tags.add(m.getCategories());
			});
			executes = insert.execute(samrockDB, mangas.values());

			return true;
		} finally {
			if(executes != -1)
				sb.yellow("Found: " +foundCount+", execute: "+executes).ln();

			LOGGER.info(sb.ln().append(coloredEnd).ln().toString());    
		}
	}

	private boolean newMangaConflicts(Map<Dir2, MangarockManga> mangas) {
		if(mangas.isEmpty())
			return true;

		Map<Integer, MangarockManga> mangarocks = mangas.values().stream().collect(Collectors.toMap(MangarockManga::getMangaId, s -> s));
		Map<Integer, SamrockManga> samrocks = new HashMap<>();

		dirs.forEach((dirname, d) -> {
			if(d.samrock() != null && mangarocks.containsKey(d.samrock().getMangaId()))
				samrocks.put(d.samrock().getMangaId(), d.samrock());
		});

		if(samrocks.isEmpty())
			return true;

		StringBuilder2 sb = new StringBuilder2()
				.append(ANSI.red(ANSI.createUnColoredBanner("NEW MANGA CONFLICT")))
				.ln();

		samrocks.forEach((id, samrock) -> {
			MangarockManga mangarock = mangarocks.get(id);
			char[] indent = {' ', ' '};

			sb.yellow(id).ln()
			.append(indent).cyan("dir_name").ln()
			.append(indent).append(indent).append("samrock:   ").append(samrock.getDirName()).ln()
			.append(indent).append(indent).append("mangarock: ").append(mangarock.getDirName()).ln()
			.append(indent).cyan("manga_name").ln()
			.append(indent).append(indent).append("samrock:   ").append(samrock.getMangaName()).ln()
			.append(indent).append(indent).append("mangarock: ").append(mangarock.getMangaName()).ln().ln();
		});

		LOGGER.info(sb.toString());

		if(!confirm("continue?"))
			return false;

		dirs.forEach((dirname, dir) -> {
			MangarockManga m = mangas.get(dir);
			if(m == null)
				return;
			SamrockManga s = samrocks.get(m.getMangaId());

			if(s != null) {
				dir.samrock(s);
				dir.mangarock(null);
				mangas.remove(dir);

				Path mp = mangaDirs.getMangaDir().resolve(m.getDirName());
				Path sp = mangaDirs.getMangaDir().resolve(s.getDirName());

				try {
					if(!Files.isSameFile(mp, sp)) {
						Iterator<Path> itr = Files.list(mp).iterator();
						while (itr.hasNext()) {
							Path path = itr.next();
							Path target = sp.resolve(path.getFileName());

							Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
							LOGGER.info(green("moved: ")+subpath(path)+yellow(" -> ")+subpath(target));
						}
						if(Files.deleteIfExists(mp))
							LOGGER.info(green("deleted: ")+subpath(mp));
					}
				} catch (IOException e) {
					LOGGER.log(Level.SEVERE, red("failed-moved: ")+subpath(mp)+yellow(" -> ")+subpath(sp), e);
				}
			}
		});

		dirs.forEach((dirname, d) -> {
			MangarockManga m = mangas.get(d);
			if(m != null && !samrocks.containsKey(m.getMangaId()))
				d.mangarock(m);
		});

		return true;
	}

	private void deleteMissingMangas(List<SamrockManga> delete) throws SQLException {
		LOGGER.info(coloredMsgMaker("Checking Updated Mangas: ")+coloredStart);

		if(!delete.isEmpty()){
			StringBuilder2 sb = new StringBuilder2();
			sb.red("These Manga(s) will be deleted from database");
			String format = "%-12s%s\n";
			sb.format(yellow(format), "manga_id", "dir_name");

			delete.forEach(d -> sb.format("%-12s%s", d.getMangaId(), d.getDirName()));
			sb.ln();

			LOGGER.info(sb.toString());

			if(confirm("wish to delete?"))
				samrockDB.executeUpdate(qm().deleteFrom(MANGAS_TABLE_NAME).where(w -> w.in(MANGA_ID, delete.stream().mapToInt(SamrockManga::getMangaId).iterator())).build());
			else
				LOGGER.info(red("DELETE Cancelled"));
		}
		LOGGER.info(coloredEnd);
	}


	private void performTotalUpdate() throws SQLException, IOException, ClassNotFoundException {
		LOGGER.info(coloredMsgMaker("Total updates: ")+coloredStart);

		Path p = APP_DATA.resolve("previousIdTime.dat");
		ArrayList<int[]> updatedIds = mangarockDB.maneger.collectToList(qm().select("_id, time").from("MangaUpdate").where(w -> w.in("_id", MyUtilsException.noError(() -> samrockDB.iterator(qm().select(VersioningMeta.MANGA_ID).from(VersioningMeta.TABLE_NAME).build(), rs -> rs.getInt(1))))).build(), rs -> new int[] {rs.getInt("_id"), rs.getInt("time")});

		Map<Integer, Integer> previousIdTime = Files.notExists(p) ? new HashMap<>() : ObjectReader.readMap(p, DataInputStream::readInt, DataInputStream::readInt);

		updatedIds.removeIf(s -> s[1] == previousIdTime.getOrDefault(s[0], -1));
		updatedIds.forEach(s -> previousIdTime.put(s[0], s[1]));
		ObjectWriter.writeMap(p, previousIdTime, DataOutputStream::writeInt, DataOutputStream::writeInt);

		if(updatedIds.isEmpty()) {
			LOGGER.info(red("nothing to update"));
			return;
		}

		Entry3 dummy = new Entry3(null);
		Map<Integer, Entry3> samrockmap = samrockDB.collectToMap(qm().select(MANGA_ID,AUTHOR,DESCRIPTION,CHAP_COUNT_MANGAROCK,STATUS,RANK,CATEGORIES).from(MANGAS_TABLE_NAME).where(w -> w.in(MANGA_ID, Iterables.map(updatedIds, s -> s[0]))).build(), getInt(MANGA_ID), Entry3::new);

		mangarockDB.maneger.iterate(qm().select(
				MangarockManga.MANGA_ID,
				MangarockManga.AUTHOR,
				MangarockManga.DESCRIPTION,
				MangarockManga.TOTAL_CHAPTERS,
				MangarockManga.STATUS,
				MangarockManga.RANK,
				MangarockManga.CATEGORIES).from(MangarockManga.TABLE_NAME).where(w -> w.in(MangarockManga.MANGA_ID, Iterables.map(updatedIds, s -> s[0]))).build(),
				rs -> samrockmap.getOrDefault(rs.getInt(MangarockManga.MANGA_ID), dummy).setMangarock(rs));

		samrockmap.values().removeIf(Entry3::equal);

		if(samrockmap.isEmpty()) {
			LOGGER.info(red("nothing to update"));
			return;
		}

		values3 = new ArrayList<>(samrockmap.values());
		samrock3 = samrockDB;
		updateSql3 = qm().update(MANGAS_TABLE_NAME).placeholders("%s").where(w -> w.eqPlaceholder(MANGA_ID)).build();

		update(0, AUTHOR);
		update(1, DESCRIPTION);
		update(2, CHAP_COUNT_MANGAROCK);
		update(3, STATUS);
		update(4, RANK);
		update(5, CATEGORIES);
		LOGGER.info(coloredEnd);

		values3 = null;
		samrock3 = null;
		updateSql3 = null;

	}

	private ArrayList<Entry3> values3;
	private SamrockDB samrock3;
	private String updateSql3;	

	private void update(int index, String column) throws SQLException {
		List<Update3> list = values3.stream()
				.map(e -> e.toUpdate(index))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());;

				if(list.isEmpty())
					LOGGER.info(green("no updated for "+column));
				else {
					if(column == CATEGORIES)
						tags.addAll(CollectionUtils.map(list, e -> e.newValue));

					LOGGER.info(green("updates for "+column+": ")+list.size());
					StringBuilder2 sb = new StringBuilder2();

					samrock3.prepareStatementBlock(String.format(updateSql3, column), ps -> {
						for (Update3 s : list) {
							sb.append("  ").append(s.manga_id).append(": ").yellow(s.oldValue).append(" -> ").green(s.newValue).ln();
							ps.setString(1, s.newValue);
							ps.setInt(2, s.manga_id);
							ps.addBatch();
						}
						sb.ln()
						.cyan("executed: ").append(ps.executeBatch().length).append('/').append(list.size()).ln(); 
						return null;
					});
					sb.ln();
					LOGGER.info(sb.toString()); 
				}
	}

	class Update3 {
		final int manga_id;
		final String oldValue, newValue;

		public Update3(int manga_id, String oldValue, String newValue) {
			this.manga_id = manga_id;
			this.oldValue = oldValue;
			this.newValue = newValue;
		}
	}

	class Entry3 {
		final int manga_id;
		final String[] samrock;
		String[] mangarock;

		public Entry3(ResultSet rs) throws SQLException {
			if(rs == null) {
				manga_id = -1;
				samrock = null;
				return;
			} 
			manga_id = rs.getInt(MANGA_ID);
			samrock = new String[] {
					rs.getString(AUTHOR),
					rs.getString(DESCRIPTION),
					rs.getString(CHAP_COUNT_MANGAROCK),
					rs.getString(STATUS),
					rs.getString(RANK),
					rs.getString(CATEGORIES)};
		}

		public Update3 toUpdate(int index) {
			if(index != 1 && !Objects.equals(samrock[index], mangarock[index])) 
				LOGGER.fine(() -> manga_id +"  "+samrock[index]+"  "+ mangarock[index]);

			String ms = mangarock[index];
			String ss;
			if(isEmptyTrimmed(ms) || ms.equals("0") || ms.equals(ss = samrock[index]))
				return null;
			return new Update3(manga_id, ss, ms);
		}

		public void setMangarock(ResultSet rs) throws SQLException {
			this.mangarock = new String[] {
					rs.getString(MangarockManga.AUTHOR),
					rs.getString(MangarockManga.DESCRIPTION),
					rs.getString(MangarockManga.TOTAL_CHAPTERS),
					rs.getString(MangarockManga.STATUS),
					rs.getString(MangarockManga.RANK),
					rs.getString(MangarockManga.CATEGORIES)};
		}

		public boolean equal() {
			return Arrays.equals(samrock, mangarock);
		}
	}

	/**
	 * list manga_dir(s) exits in manga_folder but not in database<br>
	 * check missing thumbs  <br>
	 * missing buid
	 * @param allSamrockMangas 
	 * @param newMangas 
	 * @param samrockCon
	 * @throws IOException 
	 * @throws SQLException 
	 */
	private void remainingChecks() throws IOException {
		List<String> missing  = dirs.values().stream().filter(d -> d.samrock() == null && d.mangarock() == null).map(d -> d.dir.name).collect(Collectors.toList());

		//Mangas missing From Database
		if(!missing.isEmpty()){
			LOGGER.info(coloredMsgMaker("\nMangas missing From Database:  ") +coloredStart);
			String str = String.join(System.lineSeparator(), missing);
			LOGGER.log(SEVERE, str, "Manga Missing From Database\r\n");
			LOGGER.info(str);
			LOGGER.info(coloredEnd);
		}

		Path thumbsFolder = mangaDirs.getThumbsFolder();
		Path tempThumbDir = BACKUP_PATH.resolve("thumbs");
		Files.createDirectories(tempThumbDir);

		//check missing/extra thumbs 
		if(Files.exists(thumbsFolder)){
			ExtraAndMissing em = ThumbUtils.extraAndMissingThumbs(dirs.values().stream().filter(d -> d.samrock() != null || d.mangarock() != null).mapToInt(Dir2::getMangaId).toArray(), thumbsFolder.toFile());

			if(!em.getExtraThumbNames().isEmpty()){
				LOGGER.info(coloredMsgMaker("Extra Thumbs  ")+coloredStart);
				LOGGER.info("  "+String.join("\n  ", em.getExtraThumbNames())+"\n");

				if(confirm("Delete Extra thumbs")){
					for (String s : em.getExtraThumbNames()) {
						Path src = thumbsFolder.resolve(s);
						Path trgt = tempThumbDir.resolve(s);
						try {
							Files.move(src, trgt, REPLACE_EXISTING);
							LOGGER.info("moved: "+subpath(src)+" -> "+subpath(trgt));
						} catch (IOException e) {
							LOGGER.log(Level.SEVERE, "moved-failed: "+subpath(src)+" -> "+subpath(trgt), e);
						}
					}
				}
				else
					LOGGER.info(red("\nDelete Refused\n  "));
				LOGGER.info(coloredEnd);
			}

			Predicate<String> predicate = Pattern.compile("\\d+(?:\\.jpe?g)?").asPredicate();

			Map<Integer, File> cached = 
					Optional.ofNullable(System2.lookup("THUMB_CACHE"))
					.filter(s -> !isEmptyTrimmed(s))
					.map(s -> splitStream(s, ';'))
					.orElse(Stream.empty())
					.map(File::new)
					.filter(File::exists)
					.map(File::listFiles)
					.filter(f -> !isEmpty(f))
					.flatMap(Arrays::stream)
					.filter(f -> predicate.test(f.getName()))
					.collect(Collectors.toMap(s -> getNumber(s.getName()), s -> s, (s,t) -> s));

			if(em.getMissingThumbMangaIds().length != 0){
				File tfile = thumbsFolder.toFile();
				LOGGER.info(coloredMsgMaker("Missing Thumbs  ")+coloredStart);
				String format1 = "  %-10s%s\n";
				StringBuilder2 sb = new StringBuilder2();
				sb.green(String.format(format1, "manga_id", "manga_name"));
				int[] array = IntStream.of(em.getMissingThumbMangaIds())
						.filter(id -> {
							File file = cached.get(id);
							if(file != null && file.renameTo(new File(tfile, id+".jpg"))) {
								sb.green("thumb found in cache: ").yellow(id).append('\t').append(file).ln();
								return false;
							}
							return true;
						}).toArray();
				Arrays.sort(array);

				dirs.values()
				.stream()
				.filter(s -> s.samrock() != null || s.mangarock() != null)
				.filter(s -> Arrays.binarySearch(array, s.getMangaId()) >= 0)
				.forEach(s -> sb.format(format1, s.getMangaId(), s.getDirName()));
				sb.ln().append(coloredEnd).ln();
				LOGGER.info(sb.toString());
			}
		}
		else
			LOGGER.info(red("Thumb Folder Not Found"));
	}

	public int getNumber(String s) {
		int index = s.lastIndexOf('.');
		return Integer.parseInt(index < 0 ? s : s.substring(0, index) ); 

	}

	@SuppressWarnings("unused") //TODO
	private void preparingMangalist(SamrockDB samrock) throws SQLException {
		Map<Integer, Chapter> lastChaps = new ChapterUtils(samrock).lastChapter().all();
		List<ListManga> data = new ArrayList<>(lastChaps.size());

		new MangaUtils(samrock).selectAll(rs -> {
			int id = rs.getInt(MANGA_ID);
			data.add(new ListManga(rs.getInt(CHAP_COUNT_PC), rs.getString(MANGA_NAME), rs.getLong(LAST_UPDATE_TIME), lastChaps.get(id)));
		}, MANGA_ID, MANGA_NAME, CHAP_COUNT_PC, LAST_UPDATE_TIME);

		TreeMap<Object, List<ListManga>> mapAlphabetic = data.stream().collect(groupingBy(manga -> {
			char c1 = Character.toUpperCase(manga.manga_name.charAt(0));

			if(Character.isAlphabetic(c1))
				return c1;
			else
				return '#';
		}, TreeMap::new, toList()));

		TreeMap<LocalDate, List<ListManga>> mapTime = new TreeMap<>(Comparator.reverseOrder());
		ZoneOffset zoneOffset = ZoneOffset.of("+05:30");

		mapTime.putAll(data.stream().collect(groupingBy(manga -> LocalDateTime.ofInstant(Instant.ofEpochMilli(manga.last_update_time), zoneOffset).toLocalDate())));

		StringBuilder outAlphabetSB = new StringBuilder("<!DOCTYPE html>\r\n<html lang=\"en\">\r\n <head>\r\n <meta charset=\"utf-8\">\r\n\r\n <!-- Always force latest IE rendering engine (even in intranet) & Chrome Frame\r\n Remove this if you use the .htaccess -->\r\n <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge,chrome=1\">\r\n\r\n <title>Book List</title>\r\n <meta name=\"description\" content=\"\">\r\n <meta name=\"author\" content=\"Sameer\">\r\n\r\n <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\r\n\r\n <!-- Replace favicon.ico & apple-touch-icon.png in the root of your domain and delete these references -->\r\n <link rel=\"apple-touch-icon\" href=\"/apple-touch-icon.png\">\r\n </head>\r\n\r\n <style>\r\n body {\r\n margin-left: 3em;\r\n margin-right: 3em;\r\n font-family: Consolas;\r\n}\r\n\r\n #index {\r\n list-style-type: none;\r\n padding-bottom: 100px;\r\n padding-top: 40px;\r\n font-size: 24pt;\r\n }\r\n \r\n ul {\r\n list-style-type: none;\r\n }\r\n \r\n h1 {\r\n background-color: powderblue;\r\n text-indent: 12pt;\r\n }\r\n \r\n #index li a {\r\n float: left;\r\n padding: 2px;\r\n padding-right: 0.8em;\r\n text-decoration: none;\r\n }\r\n \r\n table, tr{\r\n width:100%;\r\n }\r\n \r\n tr:nth-child(odd) {\r\n background-color:#EAEAEA;\r\n align:center;\r\n }\r\n td:nth-child(even), th:nth-child(even){\r\n text-align:center;\r\n width:5%;\r\n }\r\n \r\n th {\r\n color:white;\r\n background-color:black;\r\n }\r\n \r\n td:nth-child(odd), th:nth-child(odd) {\r\n width:46%;\r\n padding-left:20px;\r\n }\r\n \r\n th:nth-child(odd) {\r\n text-align:left;\r\n }\r\n\r\nheader {\r\nfont-size: 10pt;\r\ntext-align: right;\r\ncolor: white;\r\nbackground-color: black;\r\nwidth: 20em;\r\npadding-right: 1em;\r\nfloat: right;\r\n}\r\n </style>\r\n <body>");
		Formatter outAlphabet = new Formatter(outAlphabetSB);

		outAlphabetSB.append("<header>no. of Mangas: ").append(data.size()).append("<br>Created on: ").append(LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))).append("</header>\r\n");
		outAlphabetSB.append("<ol id=\"index\">\r\n");

		StringBuilder outTimeSB = new StringBuilder(outAlphabetSB);
		Formatter outTime = new Formatter(outTimeSB); 
		outTimeSB.append("</ol>\r\n");

		mapAlphabetic.keySet().forEach(s-> outAlphabet.format("<li><a href=\"#index%1$s\">%1$s</a></li>\r\n", s));

		outAlphabetSB.append("</ol>\r\n");

		outAlphabetSB.append("<table>\r\n");
		outAlphabet.format("<tr>\r\n<th>%s</th>\r\n<th>%s</th>\r\n<th>%s</th></tr>\r\n", "Manga Name", "Chapter Count", "Last Chapter Name");
		outAlphabetSB.append("</table>\r\n");

		mapAlphabetic.forEach((s,mangas)-> {
			outAlphabet.format("<h1><a id =\"index%1$s\">%1$s</a></h1>\r\n", s);
			outAlphabetSB.append("<table>\r\n");

			Collections.sort(mangas, Comparator.comparing(m -> m.manga_name));

			mangas.forEach(manga -> outAlphabet.format("<tr>\r\n<td>%s</td>\r\n<td>%s</td>\r\n<td>%s</td></tr>\r\n", manga.manga_name, manga.chap_count_pc, manga.lastChapter.getFileName()));
			outAlphabetSB.append("</table>\r\n");
		});

		outAlphabetSB.append("</body>\r\n</html>");

		DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL);

		mapTime.forEach((s,mangas)-> {
			outTime.format("<h1><a id =\"index%1$s\">%1$s</a></h1>\r\n", formatter.format(s));
			outTimeSB.append("<table>\r\n");

			Collections.sort(mangas, Comparator.comparing(manga -> manga.last_update_time));
			Collections.reverse(mangas);

			mangas.forEach(manga -> outTime.format("<tr>\r\n<td>%s</td>\r\n<td>%s</td>\r\n<td>%s</td></tr>\r\n", manga.manga_name, manga.chap_count_pc, manga.lastChapter.getFileName()));
			outTimeSB.append("</table>\r\n");
		});

		outTimeSB.append("</body>\r\n</html>");
		outTime.close();
		outAlphabet.close();

		try {
			Path alphabatic = APP_DATA.resolve("mangalist_alphabatic.html");
			Path  lastmodified = APP_DATA.resolve("mangalist_lastmodified.html");

			StringWriter2.setText(alphabatic, outAlphabetSB);
			StringWriter2.setText(lastmodified, outTimeSB);
			LOGGER.info("created:\n   "+alphabatic.toAbsolutePath()+"\n   "+lastmodified.toAbsolutePath());
		} catch (IOException e) {
			LOGGER.log(SEVERE, "Error while preparing mangalists", e);
		}
	}
}
