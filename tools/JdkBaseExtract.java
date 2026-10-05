/*
 * AUDIO-rec build helper.
 *
 * ECJ is run with android.jar as the boot classpath so that the exact API 34
 * platform types are used - but android.jar has no java.lang.invoke package,
 * which the compiler needs to represent lambdas (LambdaMetafactory).  This tool
 * copies the handful of java.base classes ECJ asks for out of the running
 * runtime image into tools/.toolchain/jdkbase, which is then appended to the
 * boot classpath.  build.sh regenerates the directory automatically when it is
 * missing.
 */
import java.net.URI;
import java.nio.file.*;
import java.util.*;

public class JdkBaseExtract {
    public static void main(String[] args) throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path root = fs.getPath("/modules/java.base");
        Path out = Paths.get(args[0]);
        String[] pkgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length)
                : new String[]{"java/lang/invoke", "java/lang/constant"};
        int n = 0;
        for (String pkg : pkgs) {
            Path dir = root.resolve(pkg);
            if (!Files.isDirectory(dir)) { System.out.println("missing " + pkg); continue; }
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path p : ds) {
                    String name = p.getFileName().toString();
                    if (!name.endsWith(".class")) continue;
                    Path dest = out.resolve(pkg).resolve(name);
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                    n++;
                }
            }
        }
        System.out.println("copied " + n + " classes");
    }
}
