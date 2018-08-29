package samrock.converters.makestrip;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import sam.config.MyConfig;
import sam.console.ANSI;
import sam.manga.newsamrock.chapters.ChapterWithMangaId;
import sam.manga.newsamrock.converter.ConvertChapter;
import sam.tsv.Tsv;
import samrock.converters.cleanupupdate.CheckupsAndUpdates;
import samrock.converters.extras.Progressor;

public class ConvertProcessor {
	private ConvertProcessor() {}

	public static void process(Path chaptersDataFile, boolean onlyMove) throws IOException {
		List<ConvertChapter> chapters = read(chaptersDataFile);
		if(chapters == null || chapters.isEmpty())
			return;

		process(chapters, onlyMove);
	}
	private static List<ConvertChapter> read(Path chaptersDataFile) throws IOException {
		Objects.requireNonNull(chaptersDataFile);

		Tsv tsv = Tsv.parse(chaptersDataFile);
		if(tsv.isEmpty()) {
			Logger.getLogger(ConvertProcessor.class.getSimpleName()).info(ANSI.red("file does not have data: ")+chaptersDataFile);
			return null;
		}

		return ConvertChapter.parse(tsv);
	}

	public static void process(List<ConvertChapter> chapters, boolean onlyMove)  {
		Progressor progress = chapters.size() < 4 ? new Progressor(Logger.getLogger(ConvertProcessor.class.getName())) : new Progressor("", chapters.size());

		Converter converter = new Converter(progress);
		if(onlyMove) {
			// converter.move(); 
			throw new IllegalAccessError("converter.move() not yet implemented");
		}
		else {
			List<ChapterWithMangaId> chs = converter.convert(Collections.unmodifiableList(chapters));
			Path mp = Paths.get(MyConfig.MANGA_DIR); 
			if(chs != null && chapters.stream().anyMatch(c -> c.getTarget() != null && c.getTarget().startsWith(mp))) {
				CheckupsAndUpdates c = new CheckupsAndUpdates(chs, progress);
				c.start();
			}
			progress.dispose();
		}
	}

	public static void onlyOpdate(Path chaptersDataFile) throws IOException {
		Progressor p = new Progressor(Logger.getLogger("update"));

		List<ConvertChapter> chapters = read(chaptersDataFile);
		if(chapters == null || chapters.isEmpty())
			return;

		CheckupsAndUpdates c = new CheckupsAndUpdates(chapters.stream().mapToInt(ConvertChapter::getMangaId).distinct().toArray(), p);
		c.start();
	}
}
