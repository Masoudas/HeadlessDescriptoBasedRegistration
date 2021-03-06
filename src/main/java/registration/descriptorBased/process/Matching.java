package registration.descriptorBased.process;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.SingularMatrixException;

import fiji.util.KDTree;
import fiji.util.NNearestNeighborSearch;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussian.SpecialPoint;
import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianPeak;
import mpicbg.imglib.container.array.ArrayContainerFactory;
import mpicbg.imglib.cursor.LocalizableCursor;
import mpicbg.imglib.cursor.array.ArrayCursor;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImageFactory;
import mpicbg.imglib.multithreading.SimpleMultiThreading;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyMirrorFactory;
import mpicbg.imglib.type.numeric.integer.UnsignedByteType;
import mpicbg.imglib.type.numeric.integer.UnsignedShortType;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.models.AffineModel2D;
import mpicbg.models.InterpolatedAffineModel2D;
import mpicbg.models.InterpolatedAffineModel3D;
import mpicbg.models.InvertibleBoundable;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.models.TranslationModel2D;
import mpicbg.pointdescriptor.AbstractPointDescriptor;
import mpicbg.pointdescriptor.ModelPointDescriptor;
import mpicbg.pointdescriptor.SimplePointDescriptor;
import mpicbg.pointdescriptor.exception.NoSuitablePointsException;
import mpicbg.pointdescriptor.matcher.Matcher;
import mpicbg.pointdescriptor.matcher.SubsetMatcher;
import mpicbg.pointdescriptor.model.TranslationInvariantModel;
import mpicbg.pointdescriptor.model.TranslationInvariantRigidModel2D;
import mpicbg.pointdescriptor.model.TranslationInvariantRigidModel3D;
import mpicbg.pointdescriptor.similarity.SimilarityMeasure;
import mpicbg.pointdescriptor.similarity.SquareDistance;
import mpicbg.spim.registration.ViewDataBeads;
import mpicbg.spim.registration.ViewStructure;
import net.imglib2.util.Util;
import registration.descriptorBased.headless.RegistrationParams;
import registration.descriptorBased.result.DescriptorBased2DResult;

public class Matching {
	public static boolean applyScaling = false;
	public static float factor = 1f;
	protected static PrintWriter outAll = null;

	public static int ransac_candidates;
	public static int inliers_afterRansac;

	/**
	 * 
	 * @param imp1
	 * @param imp2
	 * @param params
	 * @return - the number of inliers
	 */
	public static DescriptorBased2DResult descriptorBasedRegistration(final ImagePlus imp1, final ImagePlus imp2,
			final RegistrationParams registParams) {
		DescriptorParameters params = DescriptorParameters.Build(registParams);
		DescriptorBased2DResult result_2D = new DescriptorBased2DResult();
		int numInliers = 0;

		Model<?> model1;
		Model<?> model2;

		// zStretching if applicable
		float zStretching1 = params.dimensionality == 3
				? (float) imp1.getCalibration().pixelDepth / (float) imp1.getCalibration().pixelWidth
				: 1;
		float zStretching2 = params.dimensionality == 3
				? (float) imp2.getCalibration().pixelDepth / (float) imp2.getCalibration().pixelWidth
				: 1;

		// get the peaks (Masoud: The size of peaks indicate the number of FP detected
		// for registration.)
		ArrayList<DifferenceOfGaussianPeak<FloatType>> peaks1 = extractCandidates(imp1, params.channel1, 0, params,
				null);
		ArrayList<DifferenceOfGaussianPeak<FloatType>> peaks2 = extractCandidates(imp2, params.channel2, 0, params,
				null);

		peaks1 = filterForROI(params.roi1, peaks1);
		peaks2 = filterForROI(params.roi2, peaks2);

		final int minNumPeaks = params.numNeighbors + params.redundancy + 1;
		if (peaks1.size() < minNumPeaks || peaks2.size() < minNumPeaks) {
			result_2D.setIsSuccessful(false);
			result_2D.setFailureDescription(DescriptorBased2DResult.FailureCause.NOT_ENOUGH_FP);
			return result_2D;
		}

		// compute ransac
		ArrayList<PointMatch> finalInliers = new ArrayList<PointMatch>();
		model1 = pairwiseMatching(finalInliers, peaks1, peaks2, zStretching1, zStretching2, params, "");
		model2 = params.model.copy();

		numInliers = finalInliers.size();

		// nothing found
		if (model1 == null || model2 == null) {
			result_2D.setIsSuccessful(false);
			result_2D.setFailureDescription(DescriptorBased2DResult.FailureCause.NO_INLIER_AFTER_RANSAC);
			return result_2D;
		}

		if (params.storePoints)
			params.inliers = finalInliers;

		if (params.storeModels) {
			params.model1 = (InvertibleBoundable) model1.copy();
			params.model2 = (InvertibleBoundable) model2.copy();
		}

		_putDotsOnDetectedFeaturePoints(imp1, imp2, params, finalInliers);

		if (!_isRegistrationMatrixInvertible((AffineModel2D) model1)) {
			result_2D.setIsSuccessful(false);
			result_2D.setRegistrationError(-1);
			result_2D.setPercentInliers((double) inliers_afterRansac / ransac_candidates * 100);
			result_2D.setAffineTransfrom((AffineModel2D) model1);
			result_2D.setFailureDescription(DescriptorBased2DResult.FailureCause.NO_INVERTIBLE_TRANSFORMATION);
		} else {
			result_2D.setIsSuccessful(true);
			result_2D.setRegistrationError(model1.getCost());
			result_2D.setPercentInliers((double) inliers_afterRansac / ransac_candidates * 100);
			result_2D.setAffineTransfrom((AffineModel2D) model1);

		}

		// CompositeImage compositeImage = _generateCompositeImage(imp1, imp2, params,
		// model1, model2);
		// result_2D.setResultingCompositeImage(compositeImage);
		return result_2D;
	}

	private static void _putDotsOnDetectedFeaturePoints(final ImagePlus imp1, final ImagePlus imp2,
			final DescriptorParameters params, ArrayList<PointMatch> finalInliers) {
		// set point rois if 2d and wanted
		if (params.setPointsRois)
			setPointRois(imp1, imp2, finalInliers);
	}

	private static boolean _isRegistrationMatrixInvertible(AffineModel2D mat) {
		double[][] matAsDouble = new double[2][3];
		mat.toMatrix(matAsDouble);

		double[][] rotation = new double[2][2];
		rotation[0][0] = matAsDouble[0][0];
		rotation[0][1] = matAsDouble[0][1];
		rotation[1][0] = matAsDouble[1][0];
		rotation[1][1] = matAsDouble[1][1];

		Array2DRowRealMatrix matrix = new Array2DRowRealMatrix(rotation);

		try {
			MatrixUtils.inverse(matrix);
			return true;
		} catch (SingularMatrixException e) {
			return false;
		}
	}

	private static CompositeImage _generateCompositeImage(final ImagePlus imp1, final ImagePlus imp2,
			final DescriptorParameters params, Model<?> model1, Model<?> model2) {
		final CompositeImage composite;

		if (imp1.getType() == ImagePlus.GRAY32 || imp2.getType() == ImagePlus.GRAY32)
			composite = OverlayFusion.createOverlay(new FloatType(), imp1, imp2, (InvertibleBoundable) model1,
					(InvertibleBoundable) model2, params.dimensionality);
		else if (imp1.getType() == ImagePlus.GRAY16 || imp2.getType() == ImagePlus.GRAY16)
			composite = OverlayFusion.createOverlay(new UnsignedShortType(), imp1, imp2, (InvertibleBoundable) model1,
					(InvertibleBoundable) model2, params.dimensionality);
		else
			composite = OverlayFusion.createOverlay(new UnsignedByteType(), imp1, imp2, (InvertibleBoundable) model1,
					(InvertibleBoundable) model2, params.dimensionality);

		return composite;
	}

	/**
	 * Computes one iteration and updates the lastModels ArrayList with the new
	 * models
	 * 
	 * @param lastModels    - models from last iteration (or init)
	 * @param peaksComplete - all peaks for all images
	 * @param numImages     - how many images are there
	 * @param params        - the parameters
	 * @param zStretching   - the zStretching if applicable
	 * 
	 * @return the number of matches found
	 */
	protected static int performIteration(final ArrayList<InvertibleBoundable> lastModels,
			final ArrayList<ArrayList<DifferenceOfGaussianPeak<FloatType>>> peaksComplete, final int numImages,
			final DescriptorParameters params, final float zStretching) {
		// filter for roi with updated global coordinates
		final ArrayList<ArrayList<DifferenceOfGaussianPeak<FloatType>>> peaks = new ArrayList<ArrayList<DifferenceOfGaussianPeak<FloatType>>>();
		for (int t = 0; t < numImages; ++t)
			peaks.add(filterForROI(params.roi1, peaksComplete.get(t), (Model) lastModels.get(t)));

		// compute descriptormatching between all pairs of images
		final Vector<ComparePair> pairs = descriptorMatching(peaks, numImages, params, zStretching);

		// perform global optimization
		final ArrayList<InvertibleBoundable> models = globalOptimization(pairs, numImages, params);

		// update old models
		lastModels.clear();
		for (final InvertibleBoundable model : models)
			lastModels.add(model);

		// count matches
		return countMatches(pairs);
	}

	protected static int countMatches(final List<ComparePair> pairs) {
		int numMatches = 0;

		for (final ComparePair pair : pairs)
			numMatches += pair.inliers.size();

		return numMatches;
	}

	static Vector<ComparePair> descriptorMatching(final ArrayList<ArrayList<DifferenceOfGaussianPeak<FloatType>>> peaks,
			final int numImages, final DescriptorParameters params, final float zStretching) {
		// get all compare pairs
		final Vector<ComparePair> pairs = getComparePairs(params, numImages);

		// compute all matchings
		final AtomicInteger ai = new AtomicInteger(0);
		final Thread[] threads = SimpleMultiThreading.newThreads();
		final int numThreads = threads.length;

		// open debug file if wanted
		if (DescriptorParameters.correspondenceDirectory != null) {
			final File dir = new File(DescriptorParameters.correspondenceDirectory);

			if (dir.exists() && dir.isDirectory())
				outAll = openFileWrite(new File(DescriptorParameters.correspondenceDirectory, "_all.txt"));

			if (outAll == null)
				IJ.log("Could not open file to write all correspondences: "
						+ new File(DescriptorParameters.correspondenceDirectory, "_all.txt"));
		}

		for (int ithread = 0; ithread < threads.length; ++ithread)
			threads[ithread] = new Thread(new Runnable() {
				public void run() {
					final int myNumber = ai.getAndIncrement();

					for (int i = 0; i < pairs.size(); i++)
						if (i % numThreads == myNumber) {
							final ComparePair pair = pairs.get(i);
							pair.model = pairwiseMatching(pair.inliers, peaks.get(pair.indexA), peaks.get(pair.indexB),
									zStretching, zStretching, params, pair.indexA + "<->" + pair.indexB);

							if (pair.model == null) {
								pair.inliers.clear();
								pair.model = params.model.copy();
							}
						}
				}
			});

		SimpleMultiThreading.startAndJoin(threads);

		if (outAll != null)
			outAll.close();

		return pairs;
	}

	static ArrayList<InvertibleBoundable> globalOptimization(final Vector<ComparePair> pairs, final int numImages,
			final DescriptorParameters params) {
		// perform global optimization
		final ArrayList<Tile<?>> tiles = new ArrayList<Tile<?>>();
		for (int t = 0; t < numImages; ++t)
			tiles.add(new Tile(params.model.copy()));

		// reset the coordinates of all points so that we directly get the correct model
		for (final ComparePair pair : pairs) {
			if (pair.inliers.size() > 0) {
				for (final PointMatch pm : pair.inliers) {
					((Particle) pm.getP1()).restoreCoordinates();
					((Particle) pm.getP2()).restoreCoordinates();
				}
			}
			// IJ.log( pair.indexA + "<->" + pair.indexB + ": " + pair.model );
		}

		// add the matches
		for (final ComparePair pair : pairs)
			addPointMatches(pair.inliers, tiles.get(pair.indexA), tiles.get(pair.indexB));

		final TileConfiguration tc = new TileConfiguration();

		if (!params.silent) {
			if (params.fixFirstTile)
				IJ.log("Fixing first tile.");
			else
				IJ.log("Not fixing any tile.");
		}

		boolean fixed = false;
		for (int t = 0; t < numImages; ++t) {
			final Tile<?> tile = tiles.get(t);

			if (tile.getConnectedTiles().size() > 0) {
				tc.addTile(tile);

				if (params.fixFirstTile) {
					if (!fixed) {
						tc.fixTile(tile);
						fixed = true;
					}
				}
			} else {
				if (!params.silent)
					IJ.log("Tile " + t + " is not connected to any other tile, cannot compute a model");
			}
		}

		try {
			// compute an approximate correct orientation (this is important for all models
			// execpt translation and affine!, they might not converge otherwise)
			// which models have already an approximate location
			tc.preAlign();

			// compute the global optimum
			tc.optimize(10, 10000, 200);
		} catch (Exception e) {
			IJ.log("Global optimization failed: " + e);
			return null;
		}

		// assemble final list of models
		final ArrayList<InvertibleBoundable> models = new ArrayList<InvertibleBoundable>();

		for (int t = 0; t < numImages; ++t) {
			final Tile<?> tile = tiles.get(t);

			if (tile.getConnectedTiles().size() > 0) {
				if (params.regularize) {
					if (params.dimensionality == 2)
						models.add(((InterpolatedAffineModel2D) tile.getModel()).createAffineModel2D());
					else
						models.add(((InterpolatedAffineModel3D) tile.getModel()).createAffineModel3D());
				} else {
					models.add((InvertibleBoundable) tile.getModel());
				}

				if (!params.silent)
					IJ.log("Tile " + t + " (connected): " + models.get(models.size() - 1));
			} else {
				if (params.regularize) {
					if (params.dimensionality == 2)
						models.add(((InterpolatedAffineModel2D) params.model.copy()).createAffineModel2D());
					else
						models.add(((InterpolatedAffineModel3D) params.model.copy()).createAffineModel3D());
				} else {
					models.add((InvertibleBoundable) params.model.copy());
				}

				if (!params.silent)
					IJ.log("Tile " + t + " (NOT connected): " + models.get(models.size() - 1));
			}
		}

		if (!params.silent) {
			IJ.log("average displacement: " + tc.getError() + " px");
			IJ.log("minimal displacement: " + tc.getMinError() + " px");
			IJ.log("maximal displacement: " + tc.getMaxError() + " px");

			int numCorrespondences = 0;
			for (final ComparePair pair : pairs)
				numCorrespondences += pair.inliers.size();

			IJ.log("Total number of correspondending detection: " + numCorrespondences);
		}

		return models;
	}

	synchronized static void addPointMatches(final ArrayList<PointMatch> correspondences, final Tile<?> tileA,
			final Tile<?> tileB) {
		if (correspondences.size() > 0) {
			tileA.addMatches(correspondences);
			tileB.addMatches(PointMatch.flip(correspondences));
			tileA.addConnectedTile(tileB);
			tileB.addConnectedTile(tileA);
		}
	}

	protected static Vector<ComparePair> getComparePairs(final DescriptorParameters params, final int numImages) {
		final Vector<ComparePair> pairs = new Vector<ComparePair>();

		if (params.globalOpt == 0) // all-to-all
		{
			for (int indexA = 0; indexA < numImages - 1; indexA++)
				for (int indexB = indexA + 1; indexB < numImages; indexB++)
					pairs.add(new ComparePair(indexA, indexB, params.model));
		} else if (params.globalOpt == 1) // all-to-all-withrange
		{
			for (int indexA = 0; indexA < numImages - 1; indexA++)
				for (int indexB = indexA + 1; indexB < numImages; indexB++)
					if (Math.abs(indexB - indexA) <= params.range)
						pairs.add(new ComparePair(indexA, indexB, params.model));
		} else if (params.globalOpt == 2) // all-to-1
		{
			for (int indexA = 1; indexA < numImages; ++indexA)
				pairs.add(new ComparePair(indexA, 0, params.model));
		} else // Consecutive
		{
			for (int indexA = 1; indexA < numImages; ++indexA)
				pairs.add(new ComparePair(indexA, indexA - 1, params.model));
		}

		return pairs;
	}

	protected static Model<?> pairwiseMatching(final ArrayList<PointMatch> finalInliers,
			final ArrayList<DifferenceOfGaussianPeak<FloatType>> peaks1,
			final ArrayList<DifferenceOfGaussianPeak<FloatType>> peaks2, final float zStretching1,
			final float zStretching2, final DescriptorParameters params, String explanation) {
		final Matcher matcher = new SubsetMatcher(params.numNeighbors, params.numNeighbors + params.redundancy);
		ArrayList<PointMatch> candidates;

		// if the images are already in similar orientation, we do not do a
		// rotation-invariant matching, but only translation-invariant
		if (params.similarOrientation) {
			// an empty model with identity transform
			final Model<?> identityTransform = params.getInitialModel();// = params.model.copy();

			/*
			 * if ( params.dimensionality == 2 ) identityTransform = new
			 * TranslationModel2D(); else identityTransform = new TranslationModel3D();
			 */

			candidates = getCorrespondenceCandidates(params.significance, matcher, peaks1, peaks2, identityTransform,
					params.dimensionality, zStretching1, zStretching2, explanation);

			// before we compute the RANSAC we will reset the coordinates of all points so
			// that we directly get the correct model
			for (final PointMatch pm : candidates) {
				((Particle) pm.getP1()).restoreCoordinates();
				((Particle) pm.getP2()).restoreCoordinates();
			}
		} else
			candidates = getCorrespondenceCandidates(params.significance, matcher, peaks1, peaks2, null,
					params.dimensionality, zStretching1, zStretching2, explanation);

		// compute ransac
		// ArrayList<PointMatch> finalInliers = new ArrayList<PointMatch>();
		Model<?> finalModel = params.model.copy();

		// IJ.log( "after Candidates:" );
		// for ( final PointMatch pm : candidates )
		// {
		// 0; (336.40515, 285.3888, 51.396233) [(335.1637, 293.7457, 44.46349)]
		// {(336.40515, 285.3888, 16.24121)} <-> 467; (334.7591, 293.14224, 44.127663)
		// [(335.1637, 293.7457, 44.46349)
		// if ( ((Particle)pm.getP1()).getID() == 0 )
		// IJ.log( ((Particle)pm.getP1()).getID() + "; " + Util.printCoordinates(
		// ((Particle)pm.getP1()).getL() ) + " ["+Util.printCoordinates(
		// ((Particle)pm.getP1()).getW() )+"] {" + Util.printCoordinates(
		// ((Particle)pm.getP1()).getPeak().getSubPixelPosition() )+"} <-> " +
		// ((Particle)pm.getP2()).getID() + "; " + Util.printCoordinates(
		// ((Particle)pm.getP2()).getL() ) + " ["+Util.printCoordinates(
		// ((Particle)pm.getP2()).getW() )+"] {" + Util.printCoordinates(
		// ((Particle)pm.getP2()).getPeak().getSubPixelPosition() )+"}" );
		// }

		String statement;

		if (candidates.size() >= finalModel.getMinNumMatches()) {
			statement = computeRANSAC(candidates, finalInliers, finalModel, (float) params.ransacThreshold);
		} else {
			statement = "Not enough candidates " + candidates.size();
			finalInliers.clear();
		}

		// IJ.log( "First ransac: " + explanation + ": " + statement );
		// IJ.log( "first model: " + finalModel );
		// IJ.log( "Z1 " + zStretching1 );
		// IJ.log( "Z2 " + zStretching2 );

		// IJ.log( "after RANSAC:" );
		// for ( final PointMatch pm : finalInliers )
		// {
		// IJ.log( ((Particle)pm.getP1()).getID() + "; " + Util.printCoordinates(
		// ((Particle)pm.getP1()).getL() ) + " ["+Util.printCoordinates(
		// ((Particle)pm.getP1()).getW() )+"] {" + Util.printCoordinates(
		// ((Particle)pm.getP1()).getPeak().getSubPixelPosition() )+"} <-> " +
		// ((Particle)pm.getP2()).getID() + "; " + Util.printCoordinates(
		// ((Particle)pm.getP2()).getL() ) + " ["+Util.printCoordinates(
		// ((Particle)pm.getP2()).getW() )+"] {" + Util.printCoordinates(
		// ((Particle)pm.getP2()).getPeak().getSubPixelPosition() )+"}" );

		// pm.getP1().apply( finalModel );
		// IJ.log( ((Particle)pm.getP1()).getID() + "; " + Util.printCoordinates(
		// ((Particle)pm.getP1()).getL() ) + " ["+Util.printCoordinates(
		// ((Particle)pm.getP1()).getW() )+"] {" + Util.printCoordinates(
		// ((Particle)pm.getP1()).getPeak().getSubPixelPosition() )+"} <-> " +
		// ((Particle)pm.getP2()).getID() + "; " + Util.printCoordinates(
		// ((Particle)pm.getP2()).getL() ) + " ["+Util.printCoordinates(
		// ((Particle)pm.getP2()).getW() )+"] {" + Util.printCoordinates(
		// ((Particle)pm.getP2()).getPeak().getSubPixelPosition() )+"}" );
		// }

		// apply rotation-variant matching after applying the model until it converges
		if (finalInliers.size() > finalModel.getMinNumMatches() * DescriptorParameters.minInlierFactor) {
			int i = 1;
			int previousNumInliers = 0;
			int numInliers = 0;
			do {
				// get the correspondence candidates with the knowledge of the previous model
				candidates = getCorrespondenceCandidates(params.significance, matcher, peaks1, peaks2, finalModel,
						params.dimensionality, zStretching1, zStretching2, explanation);

				// before we compute the RANSAC we will reset the coordinates of all points so
				// that we directly get the correct model
				for (final PointMatch pm : candidates) {
					((Particle) pm.getP1()).restoreCoordinates();
					((Particle) pm.getP2()).restoreCoordinates();
				}

				// compute ransac
				previousNumInliers = finalInliers.size();

				final ArrayList<PointMatch> inliers = new ArrayList<PointMatch>();
				Model<?> model2 = params.model.copy();
				String tmpStatement = computeRANSAC(candidates, inliers, model2, (float) params.ransacThreshold);

				// IJ.log( "ransac " + i + ": " + explanation + ": " + tmpStatement );

				numInliers = inliers.size();
				// IJ.log( explanation + ": " + statement );

				// update model if this one was better
				if (numInliers > previousNumInliers) {
					finalModel = model2;
					finalInliers.clear();
					finalInliers.addAll(inliers);

					// it might go wrong to update (or be worse), then we want to preserve the old
					// statement
					statement = tmpStatement;
					// finalInliers = inliers;
				}
			} while (numInliers > previousNumInliers);
		} else {
			if (!params.silent)
				IJ.log(explanation + ": " + statement
						+ " - No inliers foundTipp: You could increase the number of neighbors, redundancy or use a model that has more degrees of freedom.");
			finalInliers.clear();
			return null;
		}

		if (!params.silent)
			IJ.log(explanation + ": " + statement);

		if (DescriptorParameters.printAllSimilarities) {
			for (final PointMatch pm : finalInliers) {
				Particle particleA = (Particle) pm.getP1();
				Particle particleB = (Particle) pm.getP2();

				IJ.log(particleA.id + " <-> " + particleB.id);
			}
		}

		// write out this pair to disk
		if (DescriptorParameters.correspondenceDirectory != null) {
			final File dir = new File(DescriptorParameters.correspondenceDirectory);

			if (dir.exists()) {
				if (dir.isDirectory()) {
					String ex2 = explanation.replaceAll("<->", "-");
					final File file = new File(DescriptorParameters.correspondenceDirectory, ex2 + ".txt");

					synchronized (outAll) {
						writePoints(finalInliers, params, finalModel, outAll);
					}

					final PrintWriter out = openFileWrite(file);

					if (out == null) {
						IJ.log("Could not create file: " + file);
					} else {
						writePoints(finalInliers, params, finalModel, out);
						out.close();
					}
				} else {
					IJ.log("Directory(?) " + dir + " is NO directory, cannot write out correspondences.");
				}
			} else {
				IJ.log("Directory " + dir + " does not exist, cannot write out correspondences.");
			}
		}

		return finalModel;
	}

	protected static void writePoints(final ArrayList<PointMatch> finalInliers, final DescriptorParameters params,
			final Model<?> finalModel, final PrintWriter out) {
		for (final PointMatch pm : finalInliers) {
			Particle particleA = (Particle) pm.getP1();
			Particle particleB = (Particle) pm.getP2();

			particleA.apply(finalModel);

			if (params.dimensionality == 3)
				out.println(particleA.getW()[0] + "\t" + particleA.getW()[1] + "\t"
						+ particleA.getW()[2] / particleA.zStretching + "\t" + particleB.getW()[0] + "\t"
						+ particleB.getW()[1] + "\t" + particleB.getW()[2] / particleB.zStretching);
			else
				out.println(particleA.getW()[0] + "\t" + particleA.getW()[1] + "\t" + particleB.getW()[0] + "\t"
						+ particleB.getW()[1]);
		}
	}

	static float[] computeMinMax(final ImagePlus imp, final int channel) {
		final int size = imp.getWidth() * imp.getHeight();
		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;

		IJ.log("Computing min/max over " + imp.getNSlices() + " slices and " + imp.getNFrames() + " frames for channel "
				+ channel);

		for (int z = 0; z < imp.getNSlices(); ++z)
			for (int t = 0; t < imp.getNFrames(); ++t) {
				final ImageProcessor ip = imp.getStack().getProcessor(imp.getStackIndex(channel, z + 1, t + 1));

				for (int i = 0; i < size; ++i) {
					final float f = ip.getf(i);
					min = Math.min(min, f);
					max = Math.max(max, f);
				}
			}

		return new float[] { min, max };
	}

	static ArrayList<DifferenceOfGaussianPeak<FloatType>> extractCandidates(final ImagePlus imp, final int channel,
			final int timepoint, final DescriptorParameters params, final float[] minmax) {
		// get the input images for registration
		final Image<FloatType> img = convertToFloat(imp, channel, timepoint, minmax);

		// extract Calibrations
		final Calibration cal = imp.getCalibration();

		if (params.dimensionality == 2)
			img.setCalibration(new float[] { (float) cal.pixelWidth, (float) cal.pixelHeight });
		else
			img.setCalibration(new float[] { (float) cal.pixelWidth, (float) cal.pixelHeight, (float) cal.pixelDepth });

		// extract candidates
		final ArrayList<DifferenceOfGaussianPeak<FloatType>> peaks = computeDoG(img, (float) params.sigma1,
				(float) params.sigma2, params.lookForMaxima, params.lookForMinima, (float) params.threshold,
				params.localization, params.iterations, params.sigma, params.region);

		// remove invalid peaks
		final int[] stats1 = removeInvalidAndCollectStatistics(peaks);

		String statement = "Found " + peaks.size() + " candidates for " + imp.getTitle() + " [" + timepoint + "] ("
				+ stats1[1] + " maxima, " + stats1[0] + " minima)";

		// filter strongest detections
		if (DescriptorParameters.brightestNPoints > 0) {
			final ArrayList<PeakSort> sortList = new ArrayList<PeakSort>();

			for (final DifferenceOfGaussianPeak<FloatType> peak : peaks)
				sortList.add(new PeakSort(peak));

			Collections.sort(sortList);

			peaks.clear();
			for (int i = sortList.size() - 1; i >= sortList.size() - DescriptorParameters.brightestNPoints
					&& i >= 0; --i)
				peaks.add(sortList.get(i).peak);

			statement += ", kept brightest " + peaks.size() + " peaks for matching.";
		}

		if (!params.silent)
			IJ.log(statement);

		return peaks;
	}

	static class PeakSort implements Comparable<PeakSort> {
		final DifferenceOfGaussianPeak<FloatType> peak;

		public PeakSort(final DifferenceOfGaussianPeak<FloatType> peak) {
			this.peak = peak;
		}

		@Override
		public int compareTo(final PeakSort o) {
			final float diff = Math.abs(peak.getValue().get()) - Math.abs(o.peak.getValue().get());

			if (diff < 0)
				return -1;
			else if (diff > 0)
				return 1;
			else
				return 0;
		}

	}

	/**
	 * Normalize and make a copy of the {@link ImagePlus} into an {@link Image} of
	 * FloatType for faster access when copying the slices
	 * 
	 * @param imp - the {@link ImagePlus} input image
	 * @return - the normalized copy [0...1]
	 */
	static Image<FloatType> convertToFloat(final ImagePlus imp, int channel, int timepoint, final float[] minmax) {
		// stupid 1-offset of imagej
		channel++;
		timepoint++;

		final Image<FloatType> img;

		if (imp.getNSlices() > 1)
			img = new ImageFactory<FloatType>(new FloatType(), new ArrayContainerFactory())
					.createImage(new int[] { imp.getWidth(), imp.getHeight(), imp.getNSlices() });
		else
			img = new ImageFactory<FloatType>(new FloatType(), new ArrayContainerFactory())
					.createImage(new int[] { imp.getWidth(), imp.getHeight() });

		final int sliceSize = imp.getWidth() * imp.getHeight();

		int z = 0;
		ImageProcessor ip = imp.getStack().getProcessor(imp.getStackIndex(channel, z + 1, timepoint));

		if (ip instanceof FloatProcessor) {
			final ArrayCursor<FloatType> cursor = (ArrayCursor<FloatType>) img.createCursor();

			float[] pixels = (float[]) ip.getPixels();
			int i = 0;

			while (cursor.hasNext()) {
				// only get new imageprocessor if necessary
				if (i == sliceSize) {
					++z;

					pixels = (float[]) imp.getStack().getProcessor(imp.getStackIndex(channel, z + 1, timepoint))
							.getPixels();

					i = 0;
				}

				cursor.next().set(pixels[i++]);
			}
		} else if (ip instanceof ByteProcessor) {
			final ArrayCursor<FloatType> cursor = (ArrayCursor<FloatType>) img.createCursor();

			byte[] pixels = (byte[]) ip.getPixels();
			int i = 0;

			while (cursor.hasNext()) {
				// only get new imageprocessor if necessary
				if (i == sliceSize) {
					++z;
					pixels = (byte[]) imp.getStack().getProcessor(imp.getStackIndex(channel, z + 1, timepoint))
							.getPixels();

					i = 0;
				}

				cursor.next().set(pixels[i++] & 0xff);
			}
		} else if (ip instanceof ShortProcessor) {
			final ArrayCursor<FloatType> cursor = (ArrayCursor<FloatType>) img.createCursor();

			short[] pixels = (short[]) ip.getPixels();
			int i = 0;

			while (cursor.hasNext()) {
				// only get new imageprocessor if necessary
				if (i == sliceSize) {
					++z;

					pixels = (short[]) imp.getStack().getProcessor(imp.getStackIndex(channel, z + 1, timepoint))
							.getPixels();

					i = 0;
				}

				cursor.next().set(pixels[i++] & 0xffff);
			}
		} else // some color stuff or so
		{
			final LocalizableCursor<FloatType> cursor = img.createLocalizableCursor();
			final int[] location = new int[img.getNumDimensions()];

			while (cursor.hasNext()) {
				cursor.fwd();
				cursor.getPosition(location);

				// only get new imageprocessor if necessary
				if (location[2] != z) {
					z = location[2];

					ip = imp.getStack().getProcessor(imp.getStackIndex(channel, z + 1, timepoint));
				}

				cursor.getType().set(ip.getPixelValue(location[0], location[1]));
			}
		}

		ViewDataBeads.normalizeImage(img, minmax);

		return img;
	}

	protected static ArrayList<DifferenceOfGaussianPeak<FloatType>> filterForROI(final Roi roi,
			final ArrayList<DifferenceOfGaussianPeak<FloatType>> peaks) {
		if (roi == null) {
			return peaks;
		} else {
			final ArrayList<DifferenceOfGaussianPeak<FloatType>> peaksNew = new ArrayList<DifferenceOfGaussianPeak<FloatType>>();

			for (final DifferenceOfGaussianPeak<FloatType> peak : peaks)
				if (roi.contains(Math.round(peak.getSubPixelPosition(0)), Math.round(peak.getSubPixelPosition(1))))
					peaksNew.add(peak);

			return peaksNew;
		}
	}

	protected static ArrayList<DifferenceOfGaussianPeak<FloatType>> filterForROI(final Roi roi,
			final ArrayList<DifferenceOfGaussianPeak<FloatType>> peaks, final Model<?> model) {
		if (roi == null) {
			return peaks;
		} else {
			final ArrayList<DifferenceOfGaussianPeak<FloatType>> peaksNew = new ArrayList<DifferenceOfGaussianPeak<FloatType>>();

			// init a temporary point that we will transform
			final int numDimensions = peaks.get(0).getSubPixelPosition().length;
			final double[] tmp = new double[numDimensions];

			for (final DifferenceOfGaussianPeak<FloatType> peak : peaks) {
				// update tmp
				for (int d = 0; d < numDimensions; ++d)
					tmp[d] = peak.getSubPixelPosition(d);

				// apply the model
				model.applyInPlace(tmp);

				if (roi.contains((int) Math.round(tmp[0]), (int) Math.round(tmp[1])))
					peaksNew.add(peak);
			}

			return peaksNew;
		}
	}

	protected static void setPointRois(final ImagePlus imp1, final ImagePlus imp2,
			final ArrayList<PointMatch> inliers) {
		final ArrayList<Point> list1 = new ArrayList<Point>();
		final ArrayList<Point> list2 = new ArrayList<Point>();

		PointMatch.sourcePoints(inliers, list1);
		PointMatch.targetPoints(inliers, list2);

		PointRoi sourcePoints = mpicbg.ij.util.Util.pointsToPointRoi(list1);
		PointRoi targetPoints = mpicbg.ij.util.Util.pointsToPointRoi(list2);

		imp1.setRoi(sourcePoints);
		imp2.setRoi(targetPoints);

	}

	protected static String computeRANSAC(final ArrayList<PointMatch> candidates, final ArrayList<PointMatch> inliers,
			final Model<?> model, final float maxEpsilon) {
		boolean modelFound = false;
		float minInlierRatio = DescriptorParameters.minInlierRatio;
		int numIterations = DescriptorParameters.ransacIterations;
		float maxTrust = DescriptorParameters.maxTrust;
		float minInlierFactor = DescriptorParameters.minInlierFactor;

		try {
			if (DescriptorParameters.filterRANSAC) {
				modelFound = model.filterRansac(candidates, inliers, numIterations, maxEpsilon, minInlierRatio,
						maxTrust);

			} else {
				modelFound = model.ransac(candidates, inliers, numIterations, maxEpsilon, minInlierRatio);
			}

			if (modelFound && inliers.size() > model.getMinNumMatches() * minInlierFactor) {
				model.fit(inliers);
				ransac_candidates = candidates.size();
				inliers_afterRansac = inliers.size();

				return "Remaining inliers after RANSAC (" + model.getClass().getSimpleName() + "): " + inliers.size()
						+ " of " + candidates.size() + " with average error " + model.getCost();
			} else {
				inliers.clear();
				return "NO Model found after RANSAC (" + model.getClass().getSimpleName() + ") of " + candidates.size();
			}
		} catch (Exception e) {
			inliers.clear();
			return "Exception - NO Model found after RANSAC (" + model.getClass().getSimpleName() + ") of "
					+ candidates.size();
		}
	}

	protected static ArrayList<PointMatch> getCorrespondenceCandidates(final double nTimesBetter, final Matcher matcher,
			ArrayList<DifferenceOfGaussianPeak<FloatType>> peaks1,
			ArrayList<DifferenceOfGaussianPeak<FloatType>> peaks2, final Model<?> model, final int dimensionality,
			final float zStretching1, final float zStretching2, String explanation) {
		// test if there are enough points for the matcher
		if (peaks1.size() <= matcher.getRequiredNumNeighbors() || peaks2.size() <= matcher.getRequiredNumNeighbors()) {
			IJ.log(explanation + ": Not enough peaks to perform a matching (at least "
					+ matcher.getRequiredNumNeighbors() + " are required to build a descriptor).");
			return new ArrayList<PointMatch>();
		}

		// two new lists
		ArrayList<Particle> listA = new ArrayList<Particle>();
		ArrayList<Particle> listB = new ArrayList<Particle>();

		int id = 0;

		if (model == null) {
			// no prior model known, do a locally rigid matching
			for (DifferenceOfGaussianPeak<FloatType> peak : peaks1)
				listA.add(new Particle(id++, peak, zStretching1));
			for (DifferenceOfGaussianPeak<FloatType> peak : peaks2)
				listB.add(new Particle(id++, peak, zStretching2));
		} else {
			// prior model known, apply to the points before matching and then do a simple
			// descriptor matching
			for (DifferenceOfGaussianPeak<FloatType> peak : peaks1) {
				final Particle particle = new Particle(id++, peak, zStretching1);
				particle.apply(model);
				for (int d = 0; d < particle.getL().length; ++d)
					particle.getL()[d] = particle.getW()[d];
				listA.add(particle);
			}

			for (DifferenceOfGaussianPeak<FloatType> peak : peaks2) {
				final Particle particle = new Particle(id++, peak, zStretching2);
				listB.add(particle);
			}
		}

		/* create KDTrees */
		final KDTree<Particle> treeA = new KDTree<Particle>(listA);
		final KDTree<Particle> treeB = new KDTree<Particle>(listB);

		/* extract point descriptors */
		final int numNeighbors = matcher.getRequiredNumNeighbors();

		final SimilarityMeasure similarityMeasure = new SquareDistance();

		final ArrayList<AbstractPointDescriptor> descriptorsA, descriptorsB;

		if (model == null) {
			descriptorsA = createModelPointDescriptors(treeA, listA, numNeighbors, matcher, similarityMeasure,
					dimensionality);
			descriptorsB = createModelPointDescriptors(treeB, listB, numNeighbors, matcher, similarityMeasure,
					dimensionality);
		} else {
			descriptorsA = createSimplePointDescriptors(treeA, listA, numNeighbors, matcher, similarityMeasure);
			descriptorsB = createSimplePointDescriptors(treeB, listB, numNeighbors, matcher, similarityMeasure);
		}

		// IJ.log( "before" );
		// for ( final Particle p : listA )
		// {
		// 0; (336.40515, 285.3888, 51.396233) [(335.1637, 293.7457, 44.46349)]
		// {(336.40515, 285.3888, 16.24121)} <-> 467; (334.7591, 293.14224, 44.127663)
		// [(335.1637, 293.7457, 44.46349)
		// if ( p.getID() == 0 || p.getID() == 467 )
		// IJ.log( p.getID() + "; " + Util.printCoordinates( p.getL() ) + "
		// ["+Util.printCoordinates( p.getW() )+"] {" + Util.printCoordinates(
		// p.getPeak().getSubPixelPosition() ) );
		// }

		/* compute matching */
		/* the list of correspondence candidates */
		final ArrayList<PointMatch> correspondenceCandidates = findCorrespondingDescriptors(descriptorsA, descriptorsB,
				(float) nTimesBetter);

		// IJ.log( "after" );
		// for ( final Particle p : listA )
		// {
		// 0; (336.40515, 285.3888, 51.396233) [(335.1637, 293.7457, 44.46349)]
		// {(336.40515, 285.3888, 16.24121)} <-> 467; (334.7591, 293.14224, 44.127663)
		// [(335.1637, 293.7457, 44.46349)
		// if ( p.getID() == 0 || p.getID() == 467 )
		// IJ.log( p.getID() + "; " + Util.printCoordinates( p.getL() ) + "
		// ["+Util.printCoordinates( p.getW() )+"] {" + Util.printCoordinates(
		// p.getPeak().getSubPixelPosition() ) );
		// }

		return correspondenceCandidates;
	}

	protected static final ArrayList<PointMatch> findCorrespondingDescriptors(
			final ArrayList<AbstractPointDescriptor> descriptorsA,
			final ArrayList<AbstractPointDescriptor> descriptorsB, final float nTimesBetter) {
		final ArrayList<PointMatch> correspondenceCandidates = new ArrayList<PointMatch>();

		for (final AbstractPointDescriptor descriptorA : descriptorsA) {
			double bestDifference = Double.MAX_VALUE;
			double secondBestDifference = Double.MAX_VALUE;

			AbstractPointDescriptor bestMatch = null;
			AbstractPointDescriptor secondBestMatch = null;

			for (final AbstractPointDescriptor descriptorB : descriptorsB) {
				final double difference = descriptorA.descriptorDistance(descriptorB);

				if (difference < secondBestDifference) {
					secondBestDifference = difference;
					secondBestMatch = descriptorB;

					if (secondBestDifference < bestDifference) {
						double tmpDiff = secondBestDifference;
						AbstractPointDescriptor tmpMatch = secondBestMatch;

						secondBestDifference = bestDifference;
						secondBestMatch = bestMatch;

						bestDifference = tmpDiff;
						bestMatch = tmpMatch;
					}
				}
			}

			if (bestDifference < DescriptorParameters.minSimilarity
					&& bestDifference * nTimesBetter < secondBestDifference) {
				// add correspondence for the two basis points of the descriptor
				Particle particleA = (Particle) descriptorA.getBasisPoint();
				Particle particleB = (Particle) bestMatch.getBasisPoint();

				// for RANSAC
				correspondenceCandidates.add(new PointMatch(particleA, particleB));

				if (DescriptorParameters.printAllSimilarities)
					IJ.log(particleA.id + " <-> " + particleB.id + " = " + bestDifference);
			}
		}

		return correspondenceCandidates;
	}

	protected static ArrayList<AbstractPointDescriptor> createSimplePointDescriptors(final KDTree<Particle> tree,
			final ArrayList<Particle> basisPoints, final int numNeighbors, final Matcher matcher,
			final SimilarityMeasure similarityMeasure) {
		final NNearestNeighborSearch<Particle> nnsearch = new NNearestNeighborSearch<Particle>(tree);
		final ArrayList<AbstractPointDescriptor> descriptors = new ArrayList<AbstractPointDescriptor>();

		for (final Particle p : basisPoints) {
			final ArrayList<Particle> neighbors = new ArrayList<Particle>();
			final Particle neighborList[] = nnsearch.findNNearestNeighbors(p, numNeighbors + 1);

			// the first hit is always the point itself
			for (int n = 1; n < neighborList.length; ++n)
				neighbors.add(neighborList[n]);

			try {
				descriptors.add(new SimplePointDescriptor<Particle>(p, neighbors, similarityMeasure, matcher));
			} catch (NoSuitablePointsException e) {
			}
		}

		return descriptors;
	}

	protected static ArrayList<AbstractPointDescriptor> createModelPointDescriptors(final KDTree<Particle> tree,
			final ArrayList<Particle> basisPoints, final int numNeighbors, final Matcher matcher,
			final SimilarityMeasure similarityMeasure, final int dimensionality) {
		final NNearestNeighborSearch<Particle> nnsearch = new NNearestNeighborSearch<Particle>(tree);
		final ArrayList<AbstractPointDescriptor> descriptors = new ArrayList<AbstractPointDescriptor>();

		for (final Particle p : basisPoints) {
			final ArrayList<Particle> neighbors = new ArrayList<Particle>();
			final Particle neighborList[] = nnsearch.findNNearestNeighbors(p, numNeighbors + 1);

			// the first hit is always the point itself
			for (int n = 1; n < neighborList.length; ++n)
				neighbors.add(neighborList[n]);

			final TranslationInvariantModel<?> model;

			if (dimensionality == 2)
				model = new TranslationInvariantRigidModel2D();
			else if (dimensionality == 3)
				model = new TranslationInvariantRigidModel3D();
			else {
				IJ.log("dimensionality " + dimensionality + " not supported.");
				return descriptors;
			}

			try {
				descriptors.add(new ModelPointDescriptor<Particle>(p, neighbors, model, similarityMeasure, matcher));
			} catch (NoSuitablePointsException e) {
			}
		}

		return descriptors;
	}

	protected static ArrayList<DifferenceOfGaussianPeak<FloatType>> computeDoG(final Image<FloatType> image,
			final float sigma1, final float sigma2, final boolean lookForMaxima, final boolean lookForMinima,
			final float threshold, final int localization, final int iterations, final double[] sigmaGuess,
			final int[] region) // gaussian fit parameters
	{
		return DetectionSegmentation.extractBeadsLaPlaceImgLib(image, new OutOfBoundsStrategyMirrorFactory<FloatType>(),
				0.5f, sigma1, sigma2, threshold, threshold / 4, lookForMaxima, lookForMinima, localization, iterations,
				sigmaGuess, region, ViewStructure.DEBUG_MAIN);
	}

	private static PrintWriter openFileWrite(final File file) {
		PrintWriter outputFile;
		try {
			outputFile = new PrintWriter(new FileWriter(file));
		} catch (IOException e) {
			System.out.println("TextFileAccess.openFileWrite(): " + e);
			outputFile = null;
		}
		return (outputFile);
	}

	protected static int[] removeInvalidAndCollectStatistics(ArrayList<DifferenceOfGaussianPeak<FloatType>> peaks) {
		int min = 0;
		int max = 0;

		// remove entries that are too low
		for (int i = peaks.size() - 1; i >= 0; --i) {
			final DifferenceOfGaussianPeak<FloatType> peak = peaks.get(i);

			if (!peak.isValid())
				peaks.remove(i);
			else if (peak.getPeakType() == SpecialPoint.MIN)
				min++;
			else if (peak.getPeakType() == SpecialPoint.MAX)
				max++;
		}

		return new int[] { min, max };
	}

	static ArrayList<InvertibleBoundable> descriptorBasedStackRegistration(final ImagePlus imp,
			final DescriptorParameters params) {
		return null;
	}

	public static void main(String[] args) throws NotEnoughDataPointsException {
		Point p1 = new Point(new double[] { 10, 20 });
		Point p2 = new Point(new double[] { 100, 200 });

		TranslationModel2D m = new TranslationModel2D();

		ArrayList<PointMatch> list = new ArrayList<PointMatch>();

		list.add(new PointMatch(p1, p2));

		m.fit(list);

		System.out.println(m);
		p1.apply(m);

		System.out.println(Util.printCoordinates(p1.getL()));
		System.out.println(Util.printCoordinates(p1.getW()));

		System.out.println(Util.printCoordinates(p2.getL()));
		System.out.println(Util.printCoordinates(p2.getW()));
	}
}
