package registration.descriptorBased.result;

import ij.ImagePlus;
import mpicbg.models.AffineModel2D;

public class DescriptorBased2DResult {
    public enum FailureCause {
        NOT_ENOUGH_FP, NO_INLIER_AFTER_RANSAC,
    }

    private boolean _isSuccessful = false;
    private FailureCause _description = null;
    private double _percentInliers = 0;
    private double[][] _affineTransform = new double[2][3];
    private ImagePlus _compositeImage = null;
    private double _error = 0;

    public void setIsSuccessful(boolean isSuccessful) {
        this._isSuccessful = isSuccessful;
    }

    public void setFailureDescription(FailureCause description) {
        this._description = description;
    }

    public void setPercentInliers(double percentInliers) {
        this._percentInliers = percentInliers;
    }

    public void setResultingCompositeImage(ImagePlus compositeImage) {
        this._compositeImage = compositeImage;
    }

    public void setAffineTransfrom(AffineModel2D affineModel2D) {
        affineModel2D.toMatrix(_affineTransform);
    }

    public void setRegistrationError(double error) {
        this._error = error;
    }

    public double[][] affineTransform() {
        return _affineTransform;
    }

    public ImagePlus compositeImage() {
        return _compositeImage;
    }

    public FailureCause description() {
        return _description;
    }

    public double error() {
        return _error;
    }

    public double percentInliers() {
        return _percentInliers;
    }

    public boolean isSuccessful() {
        return this._isSuccessful;
    }

}