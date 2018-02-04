import static sam.console.ansi.ANSI.FINISHED_BANNER;
import static sam.console.ansi.ANSI.green;
import static sam.console.ansi.ANSI.red;
import static sam.console.ansi.ANSI.yellow;
import static sam.swing.utils.SwingUtils.showErrorDialog;

import java.awt.Color;
import java.awt.Font;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.function.BiPredicate;

import javax.imageio.ImageIO;
import javax.swing.UIManager;

import sam.myutils.myutils.MyUtils;
import samrock.converters.MakeStrip;
import samrock.converters.MangaConvert;

/*
 * This Java source file was generated by the Gradle 'init' task.
 */
public class App {
    static final double VERSION = 1.83;
    
    public static void main(String[] args) {
        
        String help = 
                "-h        --help                               help\r\n"+
                        "-v        --version                            version\r\n"+
                        "-mc       --mangarockConverter                 Moves and converts Mangarock Data\r\n"+
                        "-mo       --mangarockMoveOnly                  only Moves Mangarock Data\r\n"+
                        "-rmc      --resumeMangarockConversion          Resume Previously Cancelled Mangarock Conversion\r\n"+
                        "-cfif     --convertFoldersInFolder             Convert Folders In Folder\r\n"+
                        "-cfnc     --checkFoldersNotConverted           Check Folders Not Converted\r\n"+
                        "-cmi      --changeMangaId                       this will make required changes, in databases and files required transition in old manga_id to new manga_id\r\n"+
                        "-cau      --cleanupsAndUpdates                 Cleanups And Updates\r\n"+
                        "-dps      --doublePageSplitter                 Double Page Splitter\r\n"+
                        "-bdps     --batchDoublePageSplitter            Batch Double Page Splitter\r\n"+
                        "-add      --add-new-manga                      add new manga manually\r\n"+
                        "\r\n\r\nOptions\n"+
                        "-dp    SKIP_DOUBLE_PAGE_CHECK\n"+
                        "-nc    SKIP_NUMBER_CONTINUITY_CHECK\n"+
                        "-ps    SKIP_PAGE_SIZE_CHECK\n"+
                        "-fish  SKIP_FISHY_CHECK\n"+
                        "\r\n"
                        ;

        if(args.length < 1){
            System.out.println(red("Invalid use of Command"));
            System.out.println("\n"+help);
            return;
        }

        if(args[0].equals("sound-test"))
            MyUtils.beep(5);

        BiPredicate<String, String> cmdCheck = (cmd1, cmd2) -> args[0].equalsIgnoreCase(cmd1) || args[0].equalsIgnoreCase(cmd2);

        if(cmdCheck.test("-h","--help")){
            System.out.println(yellow(help));
            return;
        }
        if(cmdCheck.test("-v","--version")){
            System.out.println(yellow(String.valueOf(VERSION)));
            return;
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            showErrorDialog("Error while loading sqlite ", e);
            return;
        }

        System.out.println();
        System.out.println(green("Version: "+VERSION));
        System.out.println("JVM PARAMETERS");
        ManagementFactory.getRuntimeMXBean().getInputArguments().forEach(s -> System.out.println("\t"+s));
        System.out.println("\n\n");

        System.out.println(yellow("App Started at:\t")+LocalDateTime.now()+"\n\n");

        UIManager.put("Button.contentAreaFilled", Boolean.FALSE);
        UIManager.put("Button.foreground", Color.white);
        UIManager.put("Button.background", Color.black);
        UIManager.put("Button.font", new Font(null, 0, 20));

        ImageIO.setUseCache(false);

        if(args.length > 1){
            HashSet<String> set = new HashSet<>(Arrays.asList(args));

            MakeStrip.DONT_SKIP_DOUBLE_PAGE_CHECK = !set.contains("-dp");
            MakeStrip.DONT_SKIP_NUMBER_CONTINUITY_CHECK = !set.contains("-nc");
            MakeStrip.DONT_SKIP_PAGE_SIZE_CHECK = !set.contains("-ps");
            MakeStrip.DONT_SKIP_FISHY_CHECK = !set.contains("-fish");
        }

        System.out.println("SKIP_DOUBLE_PAGE_CHECK = "+ (!MakeStrip.DONT_SKIP_DOUBLE_PAGE_CHECK ? red("TRUE") :  green("FALSE")));
        System.out.println("SKIP_NUMBER_CONTINUITY_CHECK = "+(!MakeStrip.DONT_SKIP_NUMBER_CONTINUITY_CHECK  ? red("TRUE") :  green("FALSE")));
        System.out.println("SKIP_FISHY_CHECK = "+(!MakeStrip.DONT_SKIP_FISHY_CHECK ? red("TRUE") :  green("FALSE")) );
        System.out.println("SKIP_PAGE_SIZE_CHECK = "+(!MakeStrip.DONT_SKIP_PAGE_SIZE_CHECK ? red("TRUE\r\n\r\n") :  green("FALSE\r\n\r\n")) );

        MangaConvert converter = null;

        if(cmdCheck.test("-mc","--mangarockConverter"))
            (converter = new MangaConvert()).mangaRockMoverConverter(false);
        else if(cmdCheck.test("-mo","--mangarockMoveOnly"))
            (converter = new MangaConvert()).mangaRockMoverConverter(true);
        else if(cmdCheck.test("-rmc","--resumeMangarockConversion"))
            (converter = new MangaConvert()).resumeMangarockConversion();
        else if(cmdCheck.test("-cfif","--convertFoldersInFolder"))
            (converter = new MangaConvert()).convertFoldersInThisFolder(args.length > 1);
        else if(cmdCheck.test("-cfnc","--checkFoldersNotConverted"))
            (converter = new MangaConvert()).checkfoldersNotConverted();
        else if(cmdCheck.test("-cau","--cleanupsAndUpdates"))
            (converter = new MangaConvert()).fakeCleanupsANDUpdates();
        else if(cmdCheck.test("-dps","--doublePageSplitter"))
            (converter = new MangaConvert()).splitSingleFolder();
        else if(cmdCheck.test("-bdps","--batchDoublePageSplitter"))
            (converter = new MangaConvert()).batchFolderSplitting();
        else if(cmdCheck.test("-cmi","--changeMangaId"))
            MangaConvert.changeMangaId();
        else if(cmdCheck.test("-add","--add-new-manga")){
            if(args.length < 2){
                System.out.println(red("missings values for id and url"));
                System.out.println(red("proper use: -add ID URL"));
                return;
            }
            String id = args[1].replace("\"", "").trim();
            String url = args.length > 2 ? args[2].replace("\"", "").trim() : null;

            System.out.println("id: "+id);
            System.out.println("url: "+url);

            (converter = new MangaConvert()).addNewMangaManually(id, url);
        }
        else{
            System.out.println("unrecognized command: "+args[0]+"\n\n"+help);
            return;
        }
        if(converter != null){
            MangaConvert.createErrorLog();
            converter.clean();
        }
        System.out.println(FINISHED_BANNER);
    }
}
