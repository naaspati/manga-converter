package samrock.converters.app.main;
import static java.util.logging.Level.SEVERE;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import sam.swing.SwingUtils;
import samrock.converters.extras.Utils;
import samrock.converters.makestrip.ConvertProcessor;

public class App {
    static final double VERSION = 2.022;

    @Parameter(names= {"-h", "--help"},description = "print this", help=true, order=0)
    public boolean help;

    @Parameter(names= {"-v", "--version"},description = "print version", order=1)
    public boolean printVersion;

    @Parameter(names= {"-c", "--convert"}, description =  "samrock converter", order=2)
    public boolean convert;

    @Parameter(names= {"-s", "--split"}, description =  "Double page splitter", order=3)
    public boolean split;

    @Parameter(names= {"-u","--utils" },description = "Utils", order=4)
    public boolean utils;

    @Parameter
    List<String> argsList;

    public static void main(String[] args) throws IllegalArgumentException, IllegalAccessException, IOException {
        System.setProperty("java.util.logging.config.file","logging.properties");
        new File("app_data/logs").mkdirs();
        
        if(args.length == 1 && (args[0].equals("-v") || args[0].equals("--version"))) {
            System.out.println(VERSION);
            System.exit(0);
        }
        try {
            new App(args);
        } catch (Exception e) {
            logger().log(SEVERE, "App failed", e);
        }
    }

    public App(String[] args) {
        JCommander jc = parse(this, "conv", args);

        if(!(convert || split || utils)) {
            logger().warning(() -> yellow("no options specified"));
            printUsage(jc);
            return;
        }
        if(convert) 
            convertCmd();
        else if(split) {
            Split c = new Split();
            jc = check(c, "conv --split [file]");
        }
    }

    private static Logger logger() {
        return Logger.getLogger(App.class.getSimpleName());
    }

    private void convertCmd() {
        Convert c = new Convert();
        String convertProgramName = "conv --convert [file]"; 
        if(help || (argsList == null && c.tempChapter == null)) {
            if(!help)
                System.out.println(red("no file spefied"));
            check(c, convertProgramName);
        }
        else {
            if(argsList != null) {
                JCommander jc = JCommander.newBuilder()
                        .programName(convertProgramName)
                        .addObject(c).build();
                jc.parse(argsList.toArray(new String[argsList.size()]));
            }
            c.setChapterFile();
            if(Utils.CHAPTERS_DATA_FILE == null) {
                logger().warning(() -> red("\nno file spefied\n"));
                check(c, convertProgramName);
                return;
            }
            try {
                logger().config(() -> ManagementFactory.getRuntimeMXBean().getInputArguments().stream().collect(Collectors.joining("\n  ", yellow("\nJVM PARAMETERS\n  "), "")));
                ConvertProcessor cp = new ConvertProcessor();

                if(c.update)
                	cp.onlyOpdate(Utils.CHAPTERS_DATA_FILE);
                else
                	cp.process(Utils.CHAPTERS_DATA_FILE, c.mangarockMoveOnly);
            } catch (IOException e) {
                SwingUtils.showErrorDialog("Error with conversion", e);
                logger().log(SEVERE, "Error with conversion", e);
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
