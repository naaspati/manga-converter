import sam.config.LoadConfig;
import samrock.converters.app.main.App;

public class Main {
	public static void main(String[] args) throws Exception {
		/*
		 * Files.copy(Paths.get("D:\\Core Files\\Emulator\\dolphin\\Manga\\Data\\SamrockDB - Copy.db"),
				Paths.get("D:\\Core Files\\Emulator\\dolphin\\Manga\\Data\\SamrockDB.db"),
				StandardCopyOption.REPLACE_EXISTING);

		 */

		LoadConfig.load();
		new App(args);

	}

}
