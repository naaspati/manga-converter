package samrock.converters.app.main;
import static sam.console.ANSI.FAILED_BANNER;
import static sam.console.ANSI.cyan;
import static sam.console.ANSI.green;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

import sam.logging.MyLoggerFactory;
import sam.myutils.MyUtilsCmd;
import sam.myutils.System2;
import sam.nopkg.Junk;
import samrock.converters.converter.ConvertProcessor;
import samrock.converters.doublepagesplitter.DoublePageSplitter;
import samrock.converters.extras.Utils;
@Cmd({"-c", "--convert"})
public class ConvertConfig extends CmdInitBase {
	private static final Logger LOGGER = MyLoggerFactory.logger(ConvertConfig.class);

	private static final String DDB_KEY = "DOWNLOADER_DB";
	private static final String MAX_FILE_NUMBER = "MAX_FILE_NUMBER";
	private static final String THREAD_COUNT = "THREAD_COUNT";

	private static final String SKIP_NUMBER_MISSINGS_CHECK = "SKIP_NUMBER_MISSINGS_CHECK";
	private static final String SKIP_DOUBLE_PAGE_CHECK = "SKIP_DOUBLE_PAGE_CHECK";
	private static final String SKIP_PAGE_SIZE_CHECK = "SKIP_PAGE_SIZE_CHECK";
	private static final String SKIP_FISHY_CHECK = "SKIP_FISHY_CHECK";

	public boolean mangarockMoveOnly;
	public String convertFoldersInFolder;
	public boolean dirs, full, update, dry_run, splitAll, rightToLeft;

	private Path downloaderDB;

	private  int thread_count = -1;
	private  int max_file_number = -1;

	private boolean number_missings ,double_page ,page_size ,fishy ;
	private Map<String, Boolean> conditions = new HashMap<>();

	@Override
	public boolean init(List<String> args) {
		for (String s : new String[]{
				SKIP_NUMBER_MISSINGS_CHECK,
				SKIP_DOUBLE_PAGE_CHECK,
				SKIP_PAGE_SIZE_CHECK,
				SKIP_FISHY_CHECK
		}) {
			conditions.put(s, lookupBoolean(s, false));
		}

		LOGGER.info(yellow("--convert"));
		if(args.isEmpty()) {
			LOGGER.info("no command specified");
			printHelp();
			return false;
		}

		ListIterator<String> iterator = args.listIterator();
		List<String> list = null;

		while (iterator.hasNext()) {
			String s = next(iterator);
			if(s == null)
				break;

			if(any(s, "-h", "--help")) {
				printHelp();
				return true;
			}

			boolean found = true;
			/*
			 * single command which is either boolean, or single valued   
			 */
			switch (s) {
				case "--move-only":
					mangarockMoveOnly = true;
					break;
				case "--dry-run":
					dry_run = true;
					break;
				case "--full":
					full = true;
					break;
				case "--split-all":
					splitAll = true;
					break;
				case "--dirs":
					dirs = true;
					break;
				case "-right-to-left":
					rightToLeft = true;
					break;
				case "-max-number":
					if((max_file_number = getNumber(s, iterator)) < 0)
						return false;
					break;
				case "-thread-count":
					if((thread_count = getNumber(s, iterator)) < 0)
						return false;
					break;
				case "-skip-check":
					if(!skipcheck(s, iterator))
						return false;
					break;
				default:
					found = false;
					break;
			}

			if(found) {
				list = null;
				continue;
			}

			if(any(s, "-u", "--update")) {
				update = true;
				list = null;
			} else if(any(s, "-ddb", "--downloader-db")) {
				list = null;
				downloaderDB = get(s, iterator, "a .db file", Paths::get);
				if(downloaderDB == null)
					return false;
			}

			//default
			else {
				if(list == null) {
					LOGGER.info(Junk.stackLocation()+red(" unknown command: ")+s);
					return false;
				} else {
					list.add(s);
				}
			}
		}

		// set values which were not specified in 
		initDefaults();
		print();
		LOGGER.info(yellow("-----------------------------------------\n"));
		return true;
	}
	private boolean skipcheck(String cmd, ListIterator<String> iterator) {
		if(!iterator.hasNext()) {
			printHelp();
			novalueMsg(cmd, "see help");
			return false;
		}

		while(iterator.hasNext()) {
			switch (iterator.next()) {
				case "missing-number":
					conditions.put(SKIP_NUMBER_MISSINGS_CHECK, Boolean.TRUE);
					break;
				case "missings-number":
					conditions.put(SKIP_NUMBER_MISSINGS_CHECK, Boolean.TRUE);
					break;
				case "double-page":
					conditions.put(SKIP_DOUBLE_PAGE_CHECK, Boolean.TRUE);
					break;
				case "page-size":
					conditions.put(SKIP_PAGE_SIZE_CHECK, Boolean.TRUE);
					break;
				case "fishy":
					conditions.put(SKIP_FISHY_CHECK, Boolean.TRUE);
					break;
				default: 
					iterator.previous();
					return true;
			}
		}
		return true;
	}
	private void print() {
		Logger l = MyLoggerFactory.logger(getClass());

		l.info("WORKING_DIR: "+new File(".").getAbsolutePath());
		l.info("DRY_RUN: "+string(dry_run));
		l.info(DDB_KEY+": "+(downloaderDB == null ? red("NOT SPECIFIED") : yellow(downloaderDB)));
		if(downloaderDB != null && Files.notExists(downloaderDB))
			System.out.println(red(DDB_KEY+" not found: \n"));
		
		l.info(MAX_FILE_NUMBER+": "+yellow(max_file_number));
		l.info(THREAD_COUNT+": "+yellow(thread_count));
		l.info(SKIP_DOUBLE_PAGE_CHECK+": "+string(double_page));
		l.info(SKIP_NUMBER_MISSINGS_CHECK+": "+string(number_missings));
		l.info(SKIP_PAGE_SIZE_CHECK+": "+string(page_size));
		l.info(SKIP_FISHY_CHECK+": "+string(fishy));

	}
	private void initDefaults() {
		if(downloaderDB == null) {
			String s = System2.lookup(DDB_KEY);
			if(s != null)
				downloaderDB = Paths.get(s);
		}
		if(max_file_number < 0)
			max_file_number = lookupInt(MAX_FILE_NUMBER, 1);
		if(thread_count < 0)
			thread_count = lookupInt(THREAD_COUNT, 1);

		number_missings = conditions.get(SKIP_NUMBER_MISSINGS_CHECK);
		double_page = conditions.get(SKIP_DOUBLE_PAGE_CHECK);
		page_size = conditions.get(SKIP_PAGE_SIZE_CHECK);
		fishy = conditions.get(SKIP_FISHY_CHECK);

		conditions = null;
	}
	private int getNumber(String s, Iterator<String> iterator) {
		Integer n =  get(s, iterator, "a number greater than 0", Integer::parseInt);
		if(n == null) return -1;
		if(n < 1) 
			LOGGER.info(red("Bad value for: "+s+", value: "+n)+", should-be: a number greater than 0");
		return n;
	}

	private boolean any(String s, String c1, String c2) {
		return s.equals(c1) || s.equals(c2);
	}
	private <E> E get(String cmd, Iterator<String> iterator, String valueShouldBe, Function<String, E> mapper) {
		if(!iterator.hasNext()) {
			novalueMsg(cmd, valueShouldBe);
			return null;
		}
		String v = iterator.next();
		try {
			return mapper.apply(v);
		} catch (Exception e) {
			LOGGER.info(red("Bad value for: "+cmd+", value: "+v)+", should-be: "+valueShouldBe);
		}
		return null;
	}

	private String next(Iterator<String> iterator) {
		return iterator.hasNext() ? iterator.next() : null;
	}
	@Override
	public void printHelp() {
		String[][] help = {
				{"COMMANDS", ""},
				{"  -h, --help",      "print this"},
				{"  --full",      "scan manga_dir for convertion/convert"},
				{"  --dry-run",      "dry run"},
				{"  --split-all",      "split double pages in current dir"},
				{"  --move-only",    "only Moves Mangarock Data"},
				{"  --dirs",         "Convert Folders In Folder"},
				{"  -u, --update",    "update with given chapters data"},
				{"OPTIONS", ""},
				{"-right-to-left", "split image from right-to-left"},
				{"  -ddb, --downloader-db [FILE]", "set downloader db path"},
				{"  -max-number [INT]", "max_file_number"},
				{"  -thread-count [INT]", "number of threads"},
				{"  -skip-check [LIST]", "{missings-number, double-page, page-size, fishy} skip(s) one/many checks"}
		};
		System.out.println(cyan("usage -c, --convert [commands] [options]"));
		System.out.println(MyUtilsCmd.helpString(help, new StringBuilder()));
	}
	@Override
	public void run() throws Exception {
		if(!Utils.confirm("sure to proceed?")) {
			System.out.println(red("cancelled (user requested)"));
			return;
		}
		
		if(splitAll) {
			new DoublePageSplitter(this, rightToLeft).call();
		} else if(full)
			new ConvertProcessor(this).full();
		else if(update)
			new ConvertProcessor(this).onlyUpdate();
		else {
			LOGGER.info(FAILED_BANNER);
			LOGGER.info(Junk.stackLocation()+red(" Doesn't know what to do"));
		}
	}
	private String string(boolean b) {
		return b ? red("YES") : green("NO");
	}
	public boolean skipFishyCheck() {
		return fishy;
	}
	public boolean skipPageSizeCheck() {
		return page_size;
	}
	public boolean skipDoublePageCheck() {
		return double_page;
	}
	public boolean skipMissingNumberCheck() {
		return number_missings;
	}
	public int getMaxFileNumber() {
		return max_file_number;
	}
	public int getThreadCount() {
		return thread_count;
	}
	public boolean isDryRun() {
		return dry_run;
	}

	public Path downloaderDB() {
		return downloaderDB;
	}
}