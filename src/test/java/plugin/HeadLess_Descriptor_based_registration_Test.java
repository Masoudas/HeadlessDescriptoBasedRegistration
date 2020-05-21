package plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

import headless.HeadLess_Descriptor_based_registration;
import ij.ImagePlus;
import io.scif.img.ImgOpener;
import mpicbg.models.AffineModel2D;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import result.DescriptorBased2DResult;

public class HeadLess_Descriptor_based_registration_Test {
    private static String root = HeadLess_Descriptor_based_registration_Test.class.getResource("")
            .getPath();

    @Test
    public void register_TwoProperBeadImages_RegistersSuccessfully() throws InterruptedException {
        ImgOpener opener = new ImgOpener();

        File baseFile = new File(root, "TwoProperBeadImages/Base.tif");

        ImagePlus baseImage = ImageJFunctions
                .wrap(opener.openImgs(baseFile.getAbsolutePath(), new UnsignedShortType()).get(0), "base");

        File toRegisterFile = new File(root, "TwoProperBeadImages/ToRegister.tif");
        ImagePlus toRegisterImage = ImageJFunctions.wrap(
                opener.openImgs(toRegisterFile.getAbsolutePath(), new UnsignedShortType()).get(0), "register");

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
        params.threshold = 0.3;
        params.model = new AffineModel2D();
        DescriptorBased2DResult result = new HeadLess_Descriptor_based_registration().register(toRegisterImage,
                baseImage, params);
        System.out.println(result.description());
    }

}