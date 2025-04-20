package consulo.gradle.bundle;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.content.OrderRootType;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.base.SourcesOrderRootType;
import consulo.content.bundle.BundleType;
import consulo.content.bundle.SdkModificator;
import consulo.content.bundle.SdkType;
import consulo.gradle.icon.GradleIconGroup;
import consulo.platform.Platform;
import consulo.ui.image.Image;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author VISTALL
 * @since 2025-04-06
 */
@ExtensionImpl
public class GradleBundleType extends BundleType {

    @Nonnull
    public static GradleBundleType getInstance() {
        return Application.get().getExtensionPoint(SdkType.class).findExtensionOrFail(GradleBundleType.class);
    }

    private static final String GRADLE_CORE_PREFIX = "gradle-core-";

    public GradleBundleType() {
        super("GRADLE");
    }

    @Override
    public void collectHomePaths(@Nonnull Platform platform, @Nonnull Consumer<Path> consumer) {
        String gradleHome = platform.os().getEnvironmentVariable("GRADLE_HOME");
        if (!StringUtil.isEmptyOrSpaces(gradleHome)) {
            Path gradleHomePath = platform.fs().getPath(gradleHome);
            
            if (Files.exists(gradleHomePath)) {
                consumer.accept(gradleHomePath);
            }
        }
    }

    @Nullable
    @Override
    public String getVersionString(@Nonnull Platform platform, @Nonnull Path path) {
        Path gradleScript = path.resolve("bin/gradle");
        if (!Files.exists(gradleScript)) {
            return null;
        }

        Path libDir = path.resolve("lib");
        if (!Files.exists(libDir)) {
            return null;
        }

        try {
            List<String> children = Files.list(libDir).map(Path::getFileName).map(Path::toString).toList();
            for (String child : children) {
                if (child.startsWith(GRADLE_CORE_PREFIX) && child.endsWith(".jar"))  {
                    return child.substring(GRADLE_CORE_PREFIX.length(), child.length() - 4);
                }
            }
        }
        catch (IOException ignored) {
        }

        return null;
    }

    @Override
    public void setupSdkPaths(@Nonnull SdkModificator sdkModificator) {
        Path homeNioPath = sdkModificator.getHomeNioPath();

        if (!Files.exists(homeNioPath)) {
            return;
        }

        addFrom(homeNioPath, "lib", sdkModificator);

        addFrom(homeNioPath, "lib/plugins", sdkModificator);

        Path srcDir = homeNioPath.resolve("src");
        if (Files.exists(srcDir)) {
            try {
                List<Path> srcChildren = Files.list(srcDir).toList();

                for (Path srcChild : srcChildren) {
                    if (Files.exists(srcChild) && Files.isDirectory(homeNioPath)) {
                        VirtualFile vFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(srcChild);

                        if (vFile != null) {
                            sdkModificator.addRoot(vFile, SourcesOrderRootType.getInstance());
                        }
                    }
                }
            }
            catch (IOException ignored) {
            }
        }
    }

    private static void addFrom(Path homeNioPath, String dir, @Nonnull SdkModificator sdkModificator) {
        Path libDir = homeNioPath.resolve(dir);
        if (!Files.exists(libDir)) {
            return;
        }

        try {
            List<Path> children = Files.list(libDir).toList();

            for (Path child : children) {
                String childName = child.getFileName().toString();

                if (childName.endsWith(".jar")) {
                    VirtualFile vFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(child);

                    if (vFile == null) {
                        continue;
                    }

                    VirtualFile archiveRoot = ArchiveVfsUtil.getArchiveRootForLocalFile(vFile);
                    if (archiveRoot == null) {
                        continue;
                    }

                    sdkModificator.addRoot(archiveRoot, BinariesOrderRootType.getInstance());
                }
            }
        }
        catch (IOException ignored) {
        }
    }

    @Override
    public boolean isRootTypeApplicable(OrderRootType type) {
        return type == BinariesOrderRootType.getInstance() || type == SourcesOrderRootType.getInstance();
    }

    @Nonnull
    @Override
    public String getPresentableName() {
        return "Gradle";
    }

    @Nonnull
    @Override
    public Image getIcon() {
        return GradleIconGroup.gradle();
    }
}
