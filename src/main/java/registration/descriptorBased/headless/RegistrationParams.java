package registration.descriptorBased.headless;

/**
 * Using this class, we can define the registration parameters. This class works
 * as a builder for essential params of {@link DescriptorParams}
 * 
 */
public class RegistrationParams {
    private double sigma1 = 2.23;
    private double sigma2 = 2.65;
    private double threshold = 0.03;
    private int numNeighbors = 3;
    private int redundancy = 1;
    private int thresholdRansac = 5;

    /**
     * Default values are sigma1 = 2.23, sigma2 = 2.65, threshold = 0.03,
     * numNeighbors = 3, redundancy = 1, thresholdRansac = 5.
     */
    public RegistrationParams() {

    }

    /**
     * Copy constructor.
     */
    public RegistrationParams(RegistrationParams params) {
        sigma1 = params.getSigma1();
        sigma2 = params.getSigma2();
        threshold = params.getThreshold();
        numNeighbors = params.getNumNeighbors();
        redundancy = params.getRedundancy();
        thresholdRansac = params.getRansacThreshold();
    }

    public RegistrationParams sigma1(double sigma1) {
        this.sigma1 = sigma1;
        return this;
    }

    public RegistrationParams sigma2(double sigma2) {
        this.sigma2 = sigma2;
        return this;
    }

    public RegistrationParams detectionThreshold(double threshold) {
        this.threshold = threshold;
        return this;
    }

    public RegistrationParams numNeighbors(int n) {
        this.numNeighbors = n;
        return this;
    }

    public RegistrationParams redundancy(int n) {
        this.redundancy = n;
        return this;
    }

    public RegistrationParams ransacThreshold(int threshold) {
        this.thresholdRansac = threshold;
        return this;
    }

    public double getSigma1() {
        return this.sigma1;
    }

    public double getSigma2() {
        return this.sigma2;
    }

    public double getThreshold() {
        return this.threshold;
    }

    public int getNumNeighbors() {
        return this.numNeighbors;
    }

    public int getRedundancy() {
        return this.redundancy;
    }

    public int getRansacThreshold() {
        return this.thresholdRansac;
    }
}