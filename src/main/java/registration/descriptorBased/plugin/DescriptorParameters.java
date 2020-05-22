package registration.descriptorBased.plugin;

import java.util.ArrayList;

import ij.gui.Roi;
import mpicbg.models.AbstractModel;
import mpicbg.models.AffineModel2D;
import mpicbg.models.InvertibleBoundable;
import mpicbg.models.PointMatch;
import mpicbg.models.TranslationModel3D;
import registration.descriptorBased.headless.RegistrationParams;

public class DescriptorParameters {
	/**
	 * How many iterations for a RANSAC
	 */
	public static int ransacIterations = 1000;

	/**
	 * minimal number of inliers to number of candidates in RANSAC
	 */
	public static float minInlierRatio = 0.05f;

	/**
	 * if there is a ROI designed, how many iterations
	 */
	public static int maxIterations = 5;

	/**
	 * Max trust: reject candidates with a cost larger than maxTrust * median cost
	 */
	public static float maxTrust = 4f;

	/**
	 * How many times more inliers are required than the minimum number of
	 * correspondences required for the model.
	 * 
	 * E.g. AffineModel3d needs at least 4 corresponences, so we reject if the
	 * number of inliers is smaller than minInlierFactor*4
	 */
	public static float minInlierFactor = 2f;

	/**
	 * if true we use filterRANSAC, otherwise only RANSAC
	 */
	public static boolean filterRANSAC = true;

	/**
	 * How similar two descriptors at least have to be
	 */
	public static double minSimilarity = 100;

	/**
	 * Writes out all corresponding points of all pairs if this is set to a
	 * directory
	 */
	public static String correspondenceDirectory = null;

	/**
	 * Just keep the brightest N points of all detections
	 */
	public static int brightestNPoints = 0;

	/**
	 * 0 == compute per image (per timepoint/channel individually) 1 == compute
	 * global min/max 2 == define min/max
	 */
	public static int minMaxType = 0;
	public static double min = 0;
	public static double max = 0;

	// for debug
	public static boolean printAllSimilarities = false;

	public int dimensionality;
	public double sigma1, sigma2, threshold;
	public int localization = 1; // localizationChoice = { "None", "3-dimensional quadratic fit", "Gaussian mask
									// localization fit" };
	public boolean lookForMaxima, lookForMinima;
	// (Masoud: Add AffineModel2D as the desired model).
	public AbstractModel<?> model = new AffineModel2D();
	public boolean similarOrientation;

	// (Masoud: The sum of these two parameteres determine how many feature points
	// must be present, otherwise no registration
	public int numNeighbors;
	public int redundancy;

	public double significance;
	public double ransacThreshold;
	public int channel1, channel2;

	public boolean regularize = false;
	public boolean fixFirstTile = true;
	public double lambda = 0.1;

	// for stack-registration
	public int globalOpt; // 0=all-to-all; 1=all-to-all-withrange; 2=all-to-1; 3=Consecutive
	public int range;
	public String directory;

	public boolean reApply = false;
	// These RoIs are the ones used in the interactive. If not the size of the
	// image, then FPs
	// are detected only over these regions.
	public Roi roi1, roi2;

	public boolean setPointsRois = true;

	// Display anything?
	public boolean silent = false;

	// 0 == fuse in memory, 1 == write to disk, 2 == nothing
	public int fuse = 0;

	protected AbstractModel<?> initialModel = null;

	public AbstractModel<?> getInitialModel() {
		if (initialModel != null)
			return initialModel;
		else if (this.dimensionality == 2)
			// Masoud: Start with a 2D affine model;
			return new AffineModel2D();
		// return new TranslationModel2D();
		else
			return new TranslationModel3D();

	}

	// for java-based calling
	public boolean storePoints = false;
	public boolean storeModels = false;

	public ArrayList<PointMatch> inliers = null;
	public InvertibleBoundable model1 = null;
	public InvertibleBoundable model2 = null;

	// gaussian parameters
	public double[] sigma;
	public int[] region;
	public int iterations;

	public static DescriptorParameters Build(RegistrationParams registrationParams) {
		DescriptorParameters params = new DescriptorParameters();
		params.channel1 = 0;
		params.channel2 = 0;
		params.globalOpt = 0;
		params.numNeighbors = registrationParams.getNumNeighbors();
		params.range = 0;
		params.ransacThreshold = registrationParams.getRansacThreshold();
		params.redundancy = registrationParams.getRedundancy();
		params.regularize = false;
		params.setPointsRois = false;
		params.storeModels = false;
		params.dimensionality = 2;
		params.lookForMaxima = true;
		params.lookForMinima = false;
		params.sigma1 = registrationParams.getSigma1();
		params.sigma2 = registrationParams.getSigma2();
		params.threshold = registrationParams.getThreshold();
		params.model = new AffineModel2D();

		return params;
	}
}
