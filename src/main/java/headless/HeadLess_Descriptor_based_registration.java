package headless;

import ij.ImagePlus;
import plugin.DescriptorParameters;
import process.Matching;
import result.DescriptorBased2DResult;

public class HeadLess_Descriptor_based_registration {
	// public static int defaultTransformationModel = 1;
	// public static int defaultRegularizationTransformationModel = 1;
	// public static double defaultLambda = 0.1;
	// public static boolean defaultFixFirstTile = false;
	// public static boolean defaultRegularize = false;

	// public static String[] detectionBrightness = { "Very low", "Low", "Medium",
	// "Strong", "Advanced ...",
	// "Interactive ..." };
	// public static int defaultDetectionBrightness = detectionBrightness.length -
	// 1;
	// public static double defaultSigma = 2;
	// public static double defaultThreshold = 0.03;

	// public static String[] detectionSize = { "2 px", "3 px", "4 px", "5 px", "6
	// px", "7 px", "8 px", "9 px", "10 px",
	// "Advanced ...", "Interactive ..." };
	// public static int defaultDetectionSize = detectionSize.length - 1;

	// public static String[] detectionTypes = { "Maxima only", "Minima only",
	// "Minima & Maxima", "Interactive ..." };
	// public static int defaultDetectionType = detectionTypes.length - 1;
	// public static boolean defaultInteractiveMaxima = true;
	// public static boolean defaultInteractiveMinima = false;

	// public static String[] orientation = { "Not prealigned", "Approxmiately
	// aligned",
	// "I will provide the approximate alignment" };
	// public static int defaultSimilarOrientation = 0;
	// public static int defaultNumNeighbors = 3;
	// public static int defaultRedundancy = 1;
	// public static double defaultSignificance = 3;
	// public static double defaultRansacThreshold = 5;
	// public static int defaultChannel1 = 1;
	// public static int defaultChannel2 = 1;

	/**
	 * Register the @param imageToRegister to @param baseImage, with the given
	 * registration parameters. It is assumed that both imageToRegister and
	 * baseImage are planar images.
	 * 
	 * 
	 * @param baseImage       is the base image for registration.
	 * @param imageToRegister the image to be registered to the base image.
	 * @param params          is the descriptor based registration params.
	 */
	public DescriptorBased2DResult register(ImagePlus imageToRegister, ImagePlus baseImage, DescriptorParameters registrationParams) {
		this._checkImage(imageToRegister);
		this._checkImage(baseImage);

		// compute the actual matching
		return Matching.descriptorBasedRegistration(imageToRegister, baseImage, registrationParams);
	}

	private void _checkImage(ImagePlus img) {
		int[] dimensions = img.getDimensions();
		if (dimensions[2] > 1 || dimensions[3] > 1 || dimensions[4] > 1) {
			throw new IllegalArgumentException("Registration images should only be planar.");
		}

	}


}
