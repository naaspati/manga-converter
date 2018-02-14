package samrock.converters.app.main;
import static sam.console.ansi.ANSI.yellow;

import java.io.IOException;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import sam.console.ansi.ANSI;
import sam.swing.utils.SwingUtils;
import samrock.converters.extras.Utils;
import samrock.converters.makestrip.ConvertProcessor;

public class App {
    static final double VERSION = 2.00;

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

    //TODO move to samrock tools
    /*
     * @Parameter(names= {"-cmi", "--changeMangaId"},description = "this will make required changes, in databases and files required transition in old manga_id to new manga_id")
    public boolean changeMangaId;
     */



    //TODO move to samrock tools
    // -add      --add-new-manga                      add new manga manually

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
        JCommander jc = JCommander.newBuilder()
                .programName("conv")
                .addObject(this).build();
        jc.parse(args);

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
            Utils2 c = new Utils2();
            jc = check(c, "conv --utils ");
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
                ConvertProcessor.process(Utils.CHAPTERS_DATA_FILE, c.mangarockMoveOnly);
            } catch (IOException e) {
                SwingUtils.showErrorDialog("Error with conversion", e);
            }
        }
    }
    private JCommander check(Object obj, String programName) {
        JCommander jc = JCommander.newBuilder()
                .programName(programName)
                .addObject(obj).build();

        if(help || argsList == null || argsList.isEmpty()) {
            jc.parse("-h");
            printUsage(jc);
            return null;
        } else 
            jc.parse(argsList.toArray(new String[argsList.size()]));
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
