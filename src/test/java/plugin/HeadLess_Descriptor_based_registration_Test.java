package plugin;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import ij.ImagePlus;
import ij.io.FileInfo;
import ij.io.FileOpener;
import io.scif.img.ImgOpener;
import mpicbg.models.AffineModel2D;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import result.DescriptorBased2DResult;

public class HeadLess_Descriptor_based_registration_Test {
    private static String root = HeadLess_Descriptor_based_registration_Test.class.getResource("TwoProperBeadImages")
            .getPath();

    @Test
    public void register_TwoProperBeadImages_RegistersSuccessfully() throws InterruptedException {
        ImgOpener opener = new ImgOpener();

        File baseFile = new File(root, "Beads_test2_C1.tif");

        ImagePlus baseImage = ImageJFunctions
                .showUnsignedShort(opener.openImgs(baseFile.getAbsolutePath(), new UnsignedShortType()).get(0), "base");

        File toRegisterFile = new File(root, "Beads_test2_C2.tif");
        ImagePlus toRegisterImage = ImageJFunctions.showUnsignedShort(
                opener.openImgs(toRegisterFile.getAbsolutePath(), new UnsignedShortType()).get(0), "base");

        DescriptorParameters params = new DescriptorParameters();
        params.channel1 = 0;
        params.channel2 = 0;
        params.globalOpt = 0;
        params.numNeighbors = 3;
        params.range = 0;
        params.ransacThreshold = 5;
        params.redundancy = 1;
        params.regularize = false;
        params.setPointsRois = false;

        params.dimensionality = 2;
        params.lookForMaxima = true;
        params.lookForMinima = false;
        params.sigma1 = 2.001;
        params.sigma2 = 2.38;
        params.threshold = 0.03;
        params.model = new AffineModel2D();
        DescriptorBased2DResult result = new HeadLess_Descriptor_based_registration().register(toRegisterImage,
                baseImage, params);
        

        TimeUnit.SECONDS.wait(30);

    }
}