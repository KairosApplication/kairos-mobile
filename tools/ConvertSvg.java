import java.nio.file.Files;
import java.nio.file.Path;
import com.android.ide.common.vectordrawable.Svg2Vector;

/** Uses the installed Android Studio asset converter; never hand edits Figma paths. */
class ConvertSvg {
    public static void main(String[] args) throws Exception {
        try (var output = Files.newOutputStream(Path.of(args[1]))) {
            String errors = Svg2Vector.parseSvgToXml(Path.of(args[0]), output);
            if (!errors.isBlank()) {
                throw new IllegalStateException(errors);
            }
        }
    }
}
