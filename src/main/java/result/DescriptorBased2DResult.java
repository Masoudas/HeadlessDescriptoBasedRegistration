package result;

import ij.ImagePlus;
import mpicbg.models.AffineModel2D;

public class DescriptorBased2DResult {
    private boolean _isSuccessful;
    private String _description;
    private double _percentInliers;
    private double[][] _affineTransform = new double[2][3];
    private ImagePlus _compositeImage;
    private double _error;

    public void setIsSuccessful(boolean isSuccessful) {
        this._isSuccessful = isSuccessful;
    }

    public void setDescription(String description) {
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

    public String description() {
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