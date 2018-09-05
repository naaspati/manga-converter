package samrock.converters.cleanupupdate;

import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static sam.config.MyConfig.MANGAROCK_DB_BACKUP;
import static sam.config.MyConfig.MANGAROCK_INPUT_DB;
import static sam.config.MyConfig.MANGAROCK_INPUT_DIR;
import static sam.config.MyConfig.MANGA_DATA_DIR;
import static sam.config.MyConfig.MANGA_DIR;
import static sam.config.MyConfig.SAMROCK_DB;
import static sam.config.MyConfig.SAMROCK_THUMBS_DIR;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.manga.newsamrock.mangas.MangasMeta.AUTHOR;
import static sam.manga.newsamrock.mangas.MangasMeta.CATEGORIES;
import static sam.manga.newsamrock.mangas.MangasMeta.CHAP_COUNT_MANGAROCK;
import static sam.manga.newsamrock.mangas.MangasMeta.CHAP_COUNT_PC;
import static sam.manga.newsamrock.mangas.MangasMeta.DESCRIPTION;
import static sam.manga.newsamrock.mangas.MangasMeta.DIR_NAME;
import static sam.manga.newsamrock.mangas.MangasMeta.LAST_UPDATE_TIME;
import static sam.manga.newsamrock.mangas.MangasMeta.MANGA_ID;
import static sam.manga.newsamrock.mangas.MangasMeta.MANGA_NAME;
import static sam.manga.newsamrock.mangas.MangasMeta.RANK;
import static sam.manga.newsamrock.mangas.MangasMeta.STATUS;
import static sam.manga.newsamrock.mangas.MangasMeta.TABLE_NAME;
import static samrock.converters.extras.Utils.confirm;
import static samrock.converters.extras.Utils.qm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.PreparedStatement;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

import sam.config.MyConfig;
import sam.manga.newsamrock.SamrockDB;
import sam.manga.newsamrock.chapters.Chapter;
import sam.manga.newsamrock.chapters.ChapterWithMangaId;
import sam.manga.newsamrock.chapters.ChaptersMeta;
import sam.manga.newsamrock.mangas.MangaUtils;
import sam.manga.newsamrock.thumb.ThumbUtils;
import sam.manga.newsamrock.thumb.ThumbUtils.ExtraAndMissing;
import sam.string.StringBuilder2;
import sam.tsv.Tsv;
import samrock.converters.extras.Progressor;
import samrock.converters.extras.Utils;
import samrock.converters.mangarock.MangarockDB;
import samrock.converters.mangarock.MangarockManga;

public class CheckupsAndUpdates {
	private final Path BACKUP_PATH = Utils.createBackupFolder(CheckupsAndUpdates.class);
	private final String coloredStart = "\u001b[94;102m Start \u001b[0m";
	private final String coloredEnd = "\u001b[94;102m End \u001b[0m\r\n";
	private final Logger logger = Logger.getLogger(getClass().getSimpleName());

	private final int PROGRESS_STEPS_IN_CLEANUP_UPDATE = 7;
	private final IntArray mangaIds;
	private final Progressor progressor;
	private final List<Integer> mangasToUpdate = new ArrayList<>();
	private final  List<ChapterWithMangaId> chaptersData;

	public CheckupsAndUpdates(int[] mangaids, Progressor progressor) {
		if(mangaids == null || mangaids.length == 0)
			throw new IllegalArgumentException("no mangaIds specified: ");

		this.chaptersData = new ArrayList<>();
		this.mangaIds = new IntArray(mangaids);
		this.progressor = progressor;
	}

	public CheckupsAndUpdates(List<ChapterWithMangaId> chaptersData, Progressor progressor) {
		if(chaptersData == null || chaptersData.isEmpty())
			throw new IllegalArgumentException("no mangaIds specified: "+chaptersData);

		this.mangaIds = new IntArray(chaptersData.stream().mapToInt(ChapterWithMangaId::getMangaId).sorted().distinct().toArray());
		this.progressor = progressor;
		this.chaptersData = chaptersData;
	}
	public boolean start(){
		progressor.setExitOnClose(false);
		progressor.setReset("Cleanup and Updates");
		progressor.setMaximum(PROGRESS_STEPS_IN_CLEANUP_UPDATE);
		progressor.resetProgress();

		//input clean up
		progressor.setTitle("input clean up");
		cleanInputs();
		progressor.increaseBy1();

		try (SamrockDB samrock = new SamrockDB();
				MangarockDB mangarock = new MangarockDB();
				) {

			progressor.setTitle("loading Mangas");

			List<SamrockManga> allSamrockMangas = Collections.unmodifiableList(SamrockManga.loadAll(samrock));
			IntArray samrockMangaIds = new IntArray(allSamrockMangas.stream().mapToInt(SamrockManga::getMangaId).sorted().toArray());

			int[] newIds = new int[samrockMangaIds.length()];
			int newIndex = 0;
			int[] oldIds = new int[samrockMangaIds.length()];
			int oldIndex = 0;

			for (int j = 0; j < mangaIds.length(); j++) {
				int id = mangaIds.at(j);

				if(samrockMangaIds.contains(id))
					oldIds[oldIndex++] = id;
				else
					newIds[newIndex++] = id ;
			}

			final IntArray newMangaIds = newIndex == 0 ? new IntArray(new int[0]) : new IntArray(newIds, newIndex);
			final IntArray oldMangaIds = oldIndex == 0 ? new IntArray(new int[0]) :new IntArray(oldIds, oldIndex);

			if(newMangaIds != null) {
				progressor.setTitle("proceessing New Mangas");
				processNewMangas(samrock, mangarock, newMangaIds);    
			}
			progressor.increaseBy1();

			if(oldMangaIds != null) {
				progressor.setTitle("Processing modified chapters");
				updateOrDeleteMangas(samrock, mangarock, oldMangaIds, newMangaIds, allSamrockMangas);                
			}
			progressor.increaseBy1();

			if(!mangarock.getPath().equals(MyConfig.MANGAROCK_DB_BACKUP)) {
				progressor.setTitle("performing total Update");
				performTotalUpdate(samrock, mangarock, allSamrockMangas);
			}

			progressor.increaseBy1();

			//report manga not listed in database
			progressor.setTitle("Report missing mangas in Database");
			remainingChecks(allSamrockMangas);
			progressor.increaseBy1();

			samrock.commit();
			if(!mangasToUpdate.isEmpty()) {
				Tsv tsv = new Tsv(ChaptersMeta.MANGA_ID, ChaptersMeta.NUMBER, ChaptersMeta.NAME);
				chaptersData.sort(Comparator.comparing(ChapterWithMangaId::getMangaId).thenComparing(ChapterWithMangaId::getNumber));

				for (ChapterWithMangaId c : chaptersData)
					tsv.addRow(String.valueOf(c.getMangaId()), String.valueOf(c.getNumber()), c.getFileName());

				tsv.save(BACKUP_PATH.resolve("chapters-data-"+LocalDateTime.now().toString().replace(':', '_').replace('.', '_').replace('T', '[')+"].tsv"));

				StringBuilder sb = new StringBuilder();
				samrock.chapter().updateChaptersInDB(chaptersData, mangasToUpdate, sb);
				logger.info(sb.append('\n').toString());
				samrock.commit();
			}
			//preparing mangalist
			progressor.setTitle("Preparing manga lists");
			//TODO preparingMangalist(samrock);
			progressor.increaseBy1();
		} catch (Exception e) {
			logger.log(SEVERE, "Cleanups AND updates Error", e);
			return false;
		}

		try {
			Path p = Paths.get("db_backups");
			Files.createDirectories(p);
			Path db = Paths.get(SAMROCK_DB);
			Files.copy(db, p.resolve(db.getFileName().toString().replaceFirst(".db$", "") + "_"+LocalDate.now().getDayOfMonth()+ ".db"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			logger.log(SEVERE, "samrock database: failed to backup", e);
		}
		progressor.setCompleted();
		progressor.setExitOnClose(true);
		return true;
	}

	private String coloredMsgMaker(String s) { return "\u001b[97;104m"+s+"\u001b[0m";}

	private void cleanInputs() {
		Path mangarockInputDb = Paths.get(MANGAROCK_INPUT_DB);
		Path mangarockInputFolder = Paths.get(MANGAROCK_INPUT_DIR);

		if(Files.notExists(mangarockInputDb) && Files.notExists(mangarockInputFolder))
			return;

		logger.info(coloredMsgMaker("starting input cleanups  ") + coloredStart);

		if(Files.exists(mangarockInputFolder)){
			File mangarockInputFolderFile = new File(MANGAROCK_INPUT_DIR);
			if (!mangarockInputFolderFile.delete()) {
				for (File f : mangarockInputFolderFile.listFiles()) f.delete();
				if(!mangarockInputFolderFile.delete())
					logger.info("Cleanup: com.notabasement.mangarock.android.titan is non empty");
			}
			if(Files.notExists(mangarockInputFolder))
				logger.info("\t delete: " + mangarockInputFolderFile.getName());
		}
		if(Files.exists(mangarockInputDb)){
			try {
				Files.copy(mangarockInputDb,
						Paths.get(MANGAROCK_DB_BACKUP), StandardCopyOption.REPLACE_EXISTING);
				logger.info("\t backup: " + MANGAROCK_INPUT_DB);
			} catch (IOException e2) {
				logger.log(WARNING, "Error while moving moving mangarock.db", e2);
			}
		}
		logger.info(coloredEnd);
	}
	private void processNewMangas(SamrockDB samrock, MangarockDB mangarock, IntArray array) throws SQLException, IOException {
		if(array.isEmpty())
			return;

		StringBuilder2 sb = new StringBuilder2();
		sb.append(coloredMsgMaker("Processing new Mangas: ")).append(coloredStart).ln();
		int executes = -1;

		try {
			String format1 = "  %-10s%s\n";
			sb.green(String.format(format1, "manga_id", "manga_name"));

			List<MangarockManga> list =  mangarock.getMangas(array.toArray());

			if(list.size() != array.length()) {
				sb.red("manga_ids not found in mangarock");
				if(list.isEmpty()) {
					array.forEach(i -> logger.info("  "+i));
					sb.yellow("\nno new mangas");
					return;
				}
				IntArray all = new IntArray(list.stream().mapToInt(MangarockManga::getMangaId).sorted().toArray());
				array.forEach(i -> {
					if(!all.contains(i))
						sb.append("  ").append(i);
				});
				sb.ln();                
			}

			String sql = qm().insertInto(TABLE_NAME)
					.placeholders(
							MANGA_ID,              // 1 
							DIR_NAME,              // 2 
							MANGA_NAME,            // 3 
							AUTHOR,                // 4 
							DESCRIPTION,           // 5 
							CHAP_COUNT_MANGAROCK,  // 6 
							CATEGORIES,            // 7 
							STATUS,                // 8 
							RANK                   // 9 
							);

			executes = samrock.prepareStatementBlock(sql, ps ->{
				for (MangarockManga m : list) {
					ps.setInt(1, m.getMangaId());
					ps.setString(2, MangaUtils.toDirName(m.getName()));
					ps.setString(3, m.getName());
					ps.setString(4, m.getAuthor());
					ps.setString(5, m.getDescription());
					ps.setInt(6, m.getTotalChapters());
					ps.setString(7, m.getCategories());
					ps.setInt(8, m.getStatus());
					ps.setInt(9, m.getRank());
					mangasToUpdate.add(m.getMangaId());
					ps.addBatch();
				}
				return ps.executeBatch().length;
			});
		} finally {
			if(executes != -1)
				sb.yellow("Found: " +array.length()+", execute: "+executes).ln();

			logger.info(sb.ln().append(coloredEnd).ln().toString());    
		}
	}

	private void updateOrDeleteMangas(SamrockDB samrock, MangarockDB mangarock, IntArray oldMangaIds, IntArray newMangaIds, List<SamrockManga> allSamrockMangas) throws SQLException {
		logger.info(coloredMsgMaker("Checking Updated Mangas: ")+coloredStart);

		List<SamrockManga> deleteQueue = new ArrayList<>();
		Path manga_folder = Paths.get(MANGA_DIR);

		oldMangaIds.forEach(mangasToUpdate::add);

		for (SamrockManga m : allSamrockMangas) {
			int id = m.getMangaId();

			if(!newMangaIds.contains(id) && !oldMangaIds.contains(id)) {
				Path p = manga_folder.resolve(m.getDirName());

				if(Files.notExists(p))
					deleteQueue.add(m);
				else if(p.toFile().lastModified() != m.getLastUpdateTime())
					mangasToUpdate.add(id);
			}
		}
		if(!deleteQueue.isEmpty()){
			StringBuilder2 sb = new StringBuilder2();
			sb.red("These Manga(s) will be deleted from database");
			String format = "%-12s%s\n";
			sb.format(yellow(format), "manga_id", "dir_name");

			deleteQueue.forEach(d -> sb.format("%-12s%s", d.getMangaId(), d.getDirName()));
			sb.ln();

			logger.info(sb.toString());

			if(confirm("wish to delete?"))
				samrock.executeUpdate(qm().deleteFrom(TABLE_NAME).where(w -> w.in(MANGA_ID, deleteQueue.stream().mapToInt(SamrockManga::getMangaId).toArray())).build());
			else
				logger.info(red("DELETE Cancelled"));

		}

		logger.info(coloredEnd);
	}
	private void performTotalUpdate(SamrockDB samrock, MangarockDB mangarock, List<SamrockManga> allSamrockMangas) throws SQLException {
		logger.info(coloredMsgMaker("Total updates: ")+coloredStart);

		List<MangarockManga> mangas = mangarock.getMangas(allSamrockMangas.stream().mapToInt(SamrockManga::getMangaId).toArray() , " AND last_view IS NOT 0");

		String format = qm().update(TABLE_NAME).placeholders("%s").where(w -> w.eq(MANGA_ID, "?", false)).build();

		try (
				PreparedStatement authorP = samrock.prepareStatement(String.format(format, AUTHOR));
				PreparedStatement descriptionP = samrock.prepareStatement(String.format(format, DESCRIPTION));
				PreparedStatement chapCountMangarock_statusP = samrock.prepareStatement(String.format(format, CHAP_COUNT_MANGAROCK+"  = ?, "+STATUS));
				PreparedStatement categoriesP = samrock.prepareStatement(String.format(format, CATEGORIES));
				PreparedStatement rankP = samrock.prepareStatement(String.format(format, RANK));
				) {

			for (MangarockManga m : mangas) {
				String author = m.getAuthor();
				String description = m.getDescription();
				int chap_count = m.getTotalChapters();
				int status = m.getStatus();
				String categories = m.getCategories();
				int rank = m.getRank();

				int id = m.getMangaId();

				if(author != null && !author.trim().isEmpty()){
					authorP.setString(1, author.trim());
					authorP.setInt(2, id);
					authorP.addBatch();
				}

				if(description != null && !description.trim().isEmpty()){
					descriptionP.setString(1, description.trim());
					descriptionP.setInt(2, id);
					descriptionP.addBatch();
				}

				if(chap_count != 0){
					chapCountMangarock_statusP.setInt(1, chap_count);
					chapCountMangarock_statusP.setInt(2, status);
					chapCountMangarock_statusP.setInt(3, id);
					chapCountMangarock_statusP.addBatch();
				}

				if(categories != null && !categories.replaceAll("\\.", "").trim().isEmpty()){
					categoriesP.setString(1, categories.trim());
					categoriesP.setInt(2, id);
					categoriesP.addBatch();
				}
				rankP.setInt(1, rank);
				rankP.setInt(2, id);
				rankP.addBatch();
			}

			authorP.executeBatch();
			descriptionP.executeBatch();
			chapCountMangarock_statusP.executeBatch();
			categoriesP.executeBatch();
			rankP.executeBatch();
		}
		logger.info(coloredEnd);
	}

	/**
	 * list manga_dir(s) exits in manga_folder but not in database<br>
	 * check missing thumbs  <br>
	 * missing buid
	 * @param allSamrockMangas 
	 * @param samrockCon
	 * @throws SQLException 
	 */
	private void remainingChecks(List<SamrockManga> allSamrockMangas) {
		Set<String> dirNames = Stream.of(new File(MANGA_DIR).list()).collect(toSet());
		dirNames.remove(new File(MANGA_DATA_DIR).getName());
		dirNames.remove("desktop.ini");

		allSamrockMangas.forEach(s -> dirNames.remove(s.getDirName()));

		//Mangas missing From Database
		if(!dirNames.isEmpty()){
			logger.info(coloredMsgMaker("\nMangas missing From Database:  ") +coloredStart);
			String str = String.join(System.lineSeparator(), dirNames);
			logger.log(SEVERE, str, "Manga Missing From Database\r\n");
			logger.info(str);
			logger.info(coloredEnd);
		}

		File thumbsFolder = new File(SAMROCK_THUMBS_DIR);

		//check missing/extra thumbs 
		if(thumbsFolder.exists()){
			ExtraAndMissing em = ThumbUtils.extraAndMissingThumbs(allSamrockMangas.stream().mapToInt(SamrockManga::getMangaId).toArray(), thumbsFolder);

			if(!em.getExtraThumbNames().isEmpty()){
				logger.info(coloredMsgMaker("Extra Thumbs  ")+coloredStart);
				logger.info("  "+String.join("\n  ", em.getExtraThumbNames())+"\n");

				if(confirm("Delete Extra thumbs")){
					em.getExtraThumbNames().stream().map(s -> new File(thumbsFolder, s)).forEach(f -> {
						if(f.isDirectory()){
							File[] f2 = f.listFiles();
							for (File f1 : f2) f1.delete();
						}
						f.delete();
					});
				}
				else
					logger.info(red("\nDelete Refused\n  "));
				logger.info(coloredEnd);
			}

			if(em.getMissingThumbMangaIds().length != 0){
				logger.info(coloredMsgMaker("Missing Thumbs  ")+coloredStart);
				String format1 = "  %-10s%s\n";
				StringBuilder2 sb = new StringBuilder2();
				sb.green(String.format(format1, "manga_id", "manga_name"));
				int[] array = em.getMissingThumbMangaIds();
				Arrays.sort(array);
				allSamrockMangas.stream()
				.filter(s -> Arrays.binarySearch(array, s.getMangaId()) >= 0)
				.forEach(s -> sb.format(format1, s.getMangaId(), s.getDirName()));
				sb.ln().append(coloredEnd).ln();
				logger.info(sb.toString());
			}
		}
		else
			logger.info(red("Thumb Folder Not Found"));
	}

	private void preparingMangalist(SamrockDB samrock) throws SQLException {
		Map<Integer, Chapter> lastChaps = samrock.chapter().lastChapter().all();
		List<ListManga> data = new ArrayList<>(lastChaps.size());

		samrock.manga().selectAll(rs -> {
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
			Path alphabatic = Paths.get("mangalist_alphabatic.html");
			Path  lastmodified = Paths.get("mangalist_lastmodified.html");

			Files.write(alphabatic, outAlphabetSB.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			Files.write(lastmodified, outTimeSB.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			logger.info("created:\n   "+alphabatic.toAbsolutePath()+"\n   "+lastmodified.toAbsolutePath());
		} catch (IOException e) {
			logger.log(SEVERE, "Error while preparing mangalists", e);
		}
	}
}
