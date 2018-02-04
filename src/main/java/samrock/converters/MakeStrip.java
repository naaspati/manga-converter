package samrock.converters;
import static sam.console.ansi.ANSI.red;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import sam.myutils.fileutils.FilesUtils;


/**
 * to convert a double paged manga (reported wrong or is actually a double paged and want to convert is as it is, the set MangaConvert.forceDoublePagedConvert = true;) 
 * 
 *
 */
public class MakeStrip implements Runnable {
	public static Path garbageFolder;

	public static final StringBuffer errorsCollected = new StringBuffer(); 

	public static boolean DONT_SKIP_DOUBLE_PAGE_CHECK = true;
	public static boolean DONT_SKIP_NUMBER_CONTINUITY_CHECK = true;
	public static boolean DONT_SKIP_PAGE_SIZE_CHECK = true;
	public static boolean DONT_SKIP_FISHY_CHECK = true;

	private final File folder;

	private final String folderString;

	private final Consumer<String> progresser;
	private StringBuilder errors;

	/**
	 * 
	 * @param file
	 * @throws IOException
	 */
	public MakeStrip(File file, Consumer<String> progresser) throws IOException {
		/* considering these are false
		 * 		if(ROOT_FOLDER == null)
			throw new NullPointerException("ROOT_FOLDER not set");

		if(garbageFolder == null)
			throw new NullPointerException("garbageFolder not set");

		if(file == null)
			throw new NullPointerException("cannot be null");

		if(!file.exists())
			throw new IOException("does not exists");

		if(!file.isDirectory())
			throw new IOException("not a directory");

		if(file.getParentFile().equals(ROOT_FOLDER) || file.equals(ROOT_FOLDER))
			throw new IOException("Input folder : cannot be the root folder or manga folder");

		if(file.listFiles(File::isFile).length == 0)
			throw new IOException("Empty folder");
		 */

		this.progresser = progresser; 

		this.folder = file;
		this.folderString  = file.getParentFile().getName()+" -> "+file.getName();
	}

	@Override
	public void run() {
		try {
			startProcess();
		} catch (NullPointerException|IOException|OutOfMemoryError e) {
			addError(e, "Error while converting");
		}

		if(errors != null){
			String str = errors.toString(); 
			System.out.println(str);
			errorsCollected.append(str);
		}
		progresser.accept(folderString);
	}
	private void startProcess() throws IOException {
		String[] filesNames = folder.list();

		if (filesNames.length == 0) {
			addError(null, "directory is empty");
			return;
		}

		boolean errorFound = false;

		for (int i = 0; i < filesNames.length; i++) {
			String name = filesNames[i]; 
			if(!name.matches("\\d+") && 
					!(name.matches("\\d+\\.jpe?g") && 
							new File(folder, name).renameTo(new File(folder, filesNames[i] = name.replaceFirst("\\.jpe?g$", "").trim())))){
				addError(null, name," -> file name is not a number");
				errorFound = true;
			}
		}

		if(errorFound)
			return;

		Arrays.sort(filesNames, Comparator.comparing(Integer::parseInt));

		if(DONT_SKIP_NUMBER_CONTINUITY_CHECK && filesNames.length - 1 != Integer.parseInt(filesNames[filesNames.length - 1])){
			int[] numbers = new int[filesNames.length];

			for (int j = 0; j < filesNames.length; j++) numbers[j] =  Integer.parseInt(filesNames[j]);

			StringBuilder b = new StringBuilder();
			b.append("missing numbers: ");
			IntStream.rangeClosed(0, Integer.parseInt(filesNames[filesNames.length - 1]))
			.filter(i -> Arrays.binarySearch(numbers, i) < 0)
			.forEach(i -> b.append(i).append(","));
			addError(null, b.toString());
			return;
		}

		BufferedImage[] images = new BufferedImage[filesNames.length];

		int currentHeight = 0;
		int doublePageCount = 0;
		ArrayList<int[]> range = new ArrayList<>();
		int low = 0;

		StringBuilder doublePages = null;
		if(DONT_SKIP_DOUBLE_PAGE_CHECK)
			doublePages = new StringBuilder();

		for (int i = 0; i < images.length; i++) {
			File f = new File(folder, filesNames[i]);
			
			try {
				BufferedImage img = ImageIO.read(f);
				images[i] = img;

				//check non images and nullPointers
				int w = img.getWidth();
				int h = img.getHeight();

				if(DONT_SKIP_FISHY_CHECK && h > 65500/3){
					System.out.println(red("something is fishy, image height found to be: "+h)+"\t"+folder);
					Files.write(Paths.get("fish.txt"), ("something is fishy, image height found to be: "+h+"\t"+folder).getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
					FilesUtils.openFile(new File("fish.txt"));
					System.exit(0);
				}

				currentHeight += h;

				if(currentHeight > 65500){
					range.add(new int[]{low, i - 1});
					low = i;
					currentHeight = h;
				}

				//non Image check
				if(DONT_SKIP_PAGE_SIZE_CHECK && (w < 100 || h < 100)){
					errorFound = true;
					addError(null, f.getName(), "  Image size Error: size: ", w, " X ", h);
				}

				if(DONT_SKIP_DOUBLE_PAGE_CHECK && w > 1000){
					doublePageCount++;
					doublePages.append(f.getName()).append(",");
				}

			} catch (IOException|NullPointerException|IllegalArgumentException e) {
				errorFound = true;
				addError(e, f.getName(), " Image Error");
			}
		}

		if(low < images.length)
			range.add(new int[]{low, images.length - 1});

		if(doublePageCount > 0  && doublePageCount > filesNames.length  - filesNames.length/4){
			addError(null, "Possibly double paged, double pages : ", doublePages.toString());
			return;
		}

		if(errorFound)
			return;

		//create Strip
		boolean status = true;
		if(range.size() == 1)
			status = createStrip(range.get(0), images, "");
		else{
			for (int i = 0; i < range.size(); i++) 
				status = status && createStrip(range.get(i), images, " - ".concat(String.valueOf(i + 1)));
		}

		if(status){
			for (String f : filesNames) new File(folder, f).delete();
			if(!folder.delete())
				addError(null, "Failed to delete folder");
		}
	}

	private boolean createStrip(int[] range, BufferedImage[] images, String stripNumber) {
		int maxWidth = 0, totalHeight = 0;

		for (int i = range[0]; i <= range[1]; i++) {
			BufferedImage img = images[i];
			totalHeight += img.getHeight();
			int w = img.getWidth();

			if(maxWidth < w)
				maxWidth = w;
		}

		BufferedImage finalImage = new BufferedImage(maxWidth, totalHeight, images[0].getType());
		Graphics2D g = finalImage.createGraphics();

		totalHeight = 0;

		for (int i = range[0]; i <= range[1]; i++) {
			BufferedImage m = images[i]; 
			g.drawImage(m, (maxWidth - m.getWidth()) / 2, totalHeight, null);
			totalHeight += m.getHeight();
			m.flush();
		}

		g.dispose();

		try {
			File imgFile = new File(folder+stripNumber+".jpeg");
			if (imgFile.exists()) {
				Path temp;
				if(Files.notExists(temp = garbageFolder.resolve(folder.getParentFile().getName()).resolve(folder.getName())))
					Files.createDirectories(temp);

				Files.move(imgFile.toPath(), temp.resolve(imgFile.getName()), StandardCopyOption.REPLACE_EXISTING);
				addError(null, "Garbaged: ", imgFile);
			}

			ImageIO.write(finalImage, "jpeg", imgFile);

		} catch (IOException e) {
			addError(e, "Unable to save Image, startIndex: ", range[0], "lastIndex: ",range[1],"stripNumber:" , stripNumber);
			return false;
		}

		return true;
	}


	private void addError(Throwable e,  Object... head){
		if(errors == null)
			errors = new StringBuilder(folderString).append(System.lineSeparator());

		if(head != null){
			errors.append("\t");
			for (Object s : head) errors.append(s); 
		}
		if(e != null)
			errors.append(", Error: ").append(e);

		errors.append(System.lineSeparator());
	}

}
