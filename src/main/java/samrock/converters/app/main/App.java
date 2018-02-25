package samrock.converters.app.main;
import static sam.console.ansi.ANSI.yellow;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import sam.console.ansi.ANSI;
import sam.manga.newsamrock.SamrockDB;
import sam.manga.newsamrock.chapters.Chapter;
import sam.manga.newsamrock.converter.ConvertChapter;
import sam.manga.newsamrock.mangas.MangasMeta;
import sam.swing.utils.SwingUtils;
import samrock.converters.extras.Utils;
import samrock.converters.makestrip.ConvertProcessor;

public class App {
    static final double VERSION = 2.001;

    @Parameter(names= {"-h", "--help"},description = "print this", help=true, order=0)
    public boolean help;

    @Parameter(names= {"-v", "--version"},description = "print version", order=1)
    public boolean version;

    @Parameter(names= {"-c", "--convert"}, description =  "samrock converter", order=2)
    public boolean convert;

    @Parameter(names= {"-s", "--split"}, description =  "Double page splitter", order=3)
    public boolean split;

    @Parameter(names= {"-u","--utils" },description = "Utils", order=4)
    public boolean utils;

    @Parameter
    List<String> argsList;

    public static void main(String[] args) throws IOException {
        if(args.length == 1 && (args[0].equals("-v") || args[0].equals("--version"))) {
            System.out.println(VERSION);
            System.exit(0);
        }
        try {
            new App(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public App(String[] args) {
        JCommander jc = parse(this, "conv", args);

        if(!(convert || split || utils)) {
            System.out.println(yellow("no options specified"));
            printUsage(jc);
            return;
        }
        if(convert) 
            convertCmd();
        else if(split) {
            Split c = new Split();
            jc = check(c, "conv --split [file]");
        }
        else if(utils) {
            Utils2 uti = new Utils2();
            jc = check(uti, "conv --utils ");
            if(jc == null)
                return;

            if(uti.checkFoldersNotConverted)
                checkFoldersNotConverted(uti);
        }
    }
    private void checkFoldersNotConverted(Utils2 uti) {
        Path path = Paths.get("app_data/modifiedDates.tsv");
        HashMap<String, Long> modifiedDates =  new HashMap<>();

        try {
            Files.lines(path, StandardCharsets.UTF_16)
            .forEach(s -> {
                int i = s.indexOf('\t');
                if(i < 0)
                    return;
                modifiedDates.put(s.substring(0, i), Long.parseLong(s.substring(i+1)));
            });
        } catch (IOException e) {
            SwingUtils.showErrorDialog("failed reading: "+path, e);
            return;
        }    

        List<File> files = ConvertProcessor.checkfoldersNotConverted(modifiedDates);

        if(files != null && !files.isEmpty()) {
            System.out.println("\n");

            Convert c = new Convert();
            if(uti.args != null && !uti.args.isEmpty())
                parse(c, "conv", uti.args);

            if(JOptionPane.showConfirmDialog(null, "Wanna convert") != JOptionPane.YES_OPTION)
                return;

            Path p = Utils.APP_DATA; //initiating Utils;

            HashMap<String, Integer> dirnameMangaIdMap = new HashMap<>();
            Map<String, List<File>> filesMap = files.stream().collect(Collectors.groupingBy(f -> f.getParentFile().getName()));

            try(SamrockDB db = new SamrockDB()) {
                db.manga().selectAll(rs -> {
                    String dirname = MangasMeta.getDirName(rs);
                    if(filesMap.containsKey(dirname))
                        dirnameMangaIdMap.put(dirname, MangasMeta.getMangaId(rs));
                }, MangasMeta.MANGA_ID, MangasMeta.DIR_NAME);
            } catch (SQLException | InstantiationException | IllegalAccessException | ClassNotFoundException | IOException e) {
                SwingUtils.showErrorDialog("failed to load samrockdb", e);
                return;
            }
            
            List<ConvertChapter> list = new ArrayList<>();
            filesMap.forEach((dirname, filelist) -> {
                int id = dirnameMangaIdMap.get(dirname);
                for (File f : filelist) {
                    String name = f.getName();
                    Path filePath = f.toPath();
                    list.add(new ConvertChapter(id, Chapter.parseChapterNumber(name), name, filePath, filePath));
                }
            });
            try {
                ConvertProcessor.process(list, false);
            } catch (IOException e) {
                SwingUtils.showErrorDialog("failed conversion", e);
                return;
            }
        }
        if(files != null) {
            StringBuilder sb = new StringBuilder();
            modifiedDates.forEach((s,t) -> sb.append(s).append('\t').append(t).append('\n'));
            sb.setLength(sb.length() - 1);

            try {
                Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_16), StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                System.out.println("failed to save: "+path);
                e.printStackTrace();
            }
        }

    }

    private void convertCmd() {
        Convert c = new Convert();
        if(help || (argsList == null && c.tempChapter == null)) {
            if(!help)
                System.out.println(ANSI.red("no file spefied"));
            check(c, "conv --convert [file]");
        }
        else {
            if(argsList != null) {
                JCommander jc = JCommander.newBuilder()
                        .programName("conv --convert [file]")
                        .addObject(c).build();
                jc.parse(argsList.toArray(new String[argsList.size()]));
            }
            c.setChapterFile();
            if(Utils.CHAPTERS_DATA_FILE == null) {
                System.out.println(ANSI.red("\nno file spefied\n"));
                check(c, "conv --convert [file]");
                return;
            }
            try {
                System.out.println(yellow("\nJVM PARAMETERS"));
                ManagementFactory.getRuntimeMXBean().getInputArguments().forEach(s -> System.out.println("\t"+s));
                System.out.println();

                ConvertProcessor.process(Utils.CHAPTERS_DATA_FILE, c.mangarockMoveOnly);
            } catch (IOException e) {
                SwingUtils.showErrorDialog("Error with conversion", e);
            }
        }
    }
    private JCommander check(Object obj, String programName) {
        if(help || argsList == null || argsList.isEmpty()) {
            printUsage(parse(obj, programName, "-h"));
            return null;
        } else {
            JCommander jc = parse(obj, programName, argsList);
            printUsage(jc);
            return jc;
        } 
    }

    private JCommander parse(Object obj, String programName, List<String> args){
        return parse(obj, programName, args == null ? null : args.toArray(new String[args.size()]));
    }
    private JCommander parse(Object obj, String programName, String...args){
        JCommander jc = JCommander.newBuilder()
                .programName(programName)
                .addObject(obj).build();

        if(args != null)
            jc.parse(args);
        return jc;
    }

    private void printUsage(JCommander jc) {
        StringBuilder sb = new StringBuilder();
        Formatter fm = new Formatter(sb);

        yellow(sb, "Usage: ");
        yellow(sb, jc.getProgramName());
        yellow(sb, " [options]\n");

        if(jc.getObjects().get(0) instanceof Convert)
            sb.append("  [file] : is a tsv file cotaining chapters data\n");
        if(jc.getObjects().get(0) instanceof Split)
            sb.append("  [file] : is a folder containing images, \n")
            .append("           if --batch is present then it's a folder containing folders of images\n");

        yellow(sb, "  Options:\n");

        String format2 = "    %-"+(jc.getParameters().stream().mapToInt(p -> p.getNames().length()).max().getAsInt()+5)+"s%s\n";
        jc.getParameters()
        .stream()
        .sorted(Comparator.comparing(p -> p.getParameterAnnotation().order()))
        .forEach(p -> {
            String format = p.getParameterAnnotation().order() >= 20 ? "      "+format2 : format2; 
            if(p.getParameterAnnotation().order() == 10)
                yellow(sb, "\n  Convert Options:\n");
            fm.format(format, p.getNames(), p.getDescription());
        });
        if(jc.getObjects().get(0) == this) {
            yellow(sb, "\nhelp usage:\n")
            .append("  conv --help [option]").append('\n');
            sb.append("   e.g.  ").append('\n');
            sb.append("      conv --help -c").append('\n');
        }

        fm.close();
        System.out.println(sb.toString());
    }
}
