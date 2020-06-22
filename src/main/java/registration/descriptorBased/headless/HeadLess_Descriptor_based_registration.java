package registration.descriptorBased.headless;

import ij.ImagePlus;
import mpicbg.pointdescriptor.exception.NoSuitablePointsException;
import registration.descriptorBased.process.Matching;
import registration.descriptorBased.result.DescriptorBased2DResult;
import registration.descriptorBased.result.DescriptorBased2DResult.FailureCause;

public class HeadLess_Descriptor_based_registration {
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
	public DescriptorBased2DResult register(ImagePlus imageToRegister, ImagePlus baseImage,
			RegistrationParams registrationParams) {
		this._checkImage(imageToRegister);
		this._checkImage(baseImage);

		// compute the actual matching
		try {
			return Matching.descriptorBasedRegistration(imageToRegister, baseImage, registrationParams);
		} catch (Exception e) {
			DescriptorBased2DResult result = new DescriptorBased2DResult();
			result.setIsSuccessful(false);

			if (e instanceof NoSuitablePointsException) {
				result.setFailureDescription(FailureCause.NOT_ENOUGH_FP);
			}
			result.setRegistrationError(-1);
			return result;
		}
	}

	private void _checkImage(ImagePlus img) {
		int[] dimensions = img.getDimensions();
		if (dimensions[2] > 1 || dimensions[3] > 1 || dimensions[4] > 1) {
			throw new IllegalArgumentException("Registration images should only be planar.");
		}

	}

}
