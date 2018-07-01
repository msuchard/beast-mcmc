package dr.inference.operators.hmc;

import dr.inference.hmc.HessianWrtParameterProvider;
import dr.math.MathUtils;
import dr.math.matrixAlgebra.ReadableVector;
import dr.math.matrixAlgebra.WrappedVector;

/**
 * @author Marc A. Suchard
 */
public interface MassPreconditioner {

    // It looks like my interface is slowly reverting to something close to what Xiang had previously

    WrappedVector drawInitialMomentum();

    double getKineticEnergy(ReadableVector momentum);

    double getVelocity(int index, ReadableVector momentum);

    void storeSecant(ReadableVector gradient, ReadableVector position);

    class NoPreconditioning implements MassPreconditioner {

        final int dim;

        NoPreconditioning(int dim) { this.dim = dim; }

        @Override
        public WrappedVector drawInitialMomentum() {

            double[] momentum = new double[dim];

            for (int i = 0; i < dim; ++i) {
                momentum[i] = MathUtils.nextGaussian();
            }

            return new WrappedVector.Raw(momentum);
        }

        @Override
        public double getKineticEnergy(ReadableVector momentum) {
            double total = 0.0;
            for (int i = 0; i < dim; i++) {
                total += momentum.get(i) * momentum.get(i) / 2.0;
            }
            return total;
        }

        @Override
        public double getVelocity(int i, ReadableVector momentum) {
            return momentum.get(i);
        }

        @Override
        public void storeSecant(ReadableVector gradient, ReadableVector position) { }
    }

    abstract class HessianBased implements MassPreconditioner {
        final protected int dim;
        final protected HessianWrtParameterProvider hessian;
        final protected double[] inverseMass;

        HessianBased(HessianWrtParameterProvider hessian) {
            this.dim = hessian.getDimension();
            this.hessian = hessian;
            this.inverseMass = computeInverseMass();
        }

        public void storeSecant(ReadableVector gradient, ReadableVector position) {
            // Do nothing
        }

        abstract protected double[] computeInverseMass();
    }

    class DiagonalPreconditioning extends HessianBased {


        DiagonalPreconditioning(int dim, HessianWrtParameterProvider hessian) {
            super(hessian);
        }

        @Override
        public WrappedVector drawInitialMomentum() {

            double[] momentum = new double[dim];

            for (int i = 0; i < dim; i++) {
                momentum[i] = MathUtils.nextGaussian() * Math.sqrt(1.0 / inverseMass[i]);
            }

            return new WrappedVector.Raw(momentum);
        }

        @Override
        protected double[] computeInverseMass() {

            double[] diagonalHessian = hessian.getDiagonalHessianLogDensity();
            double[] boundedMassInverse = boundMassInverse(diagonalHessian);

            return boundedMassInverse;
        }

        protected int getMassSize() { return dim; }

//        private void updateMass() {
//            double[] diagonalHessian = hessian.getDiagonalHessianLogDensity();
//            double[] boundedMassInverse = boundMassInverse(diagonalHessian);
//            for (int i = 0; i < dim; i++) {
//                mass[i] = 1.0 / boundedMassInverse[i];
//            }
//        }

        private double[] boundMassInverse(double[] diagonalHessian) {

            double sum = 0.0;
            final double lowerBound = 1E-2; //TODO bad magic numbers
            final double upperBound = 1E2;
            double[] boundedMassInverse = new double[dim];

            for (int i = 0; i < dim; i++) {
                boundedMassInverse[i] = -1.0 / diagonalHessian[i];
                if (boundedMassInverse[i] < lowerBound) {
                    boundedMassInverse[i] = lowerBound;
                } else if (boundedMassInverse[i] > upperBound) {
                    boundedMassInverse[i] = upperBound;
                }
                sum += 1.0 / boundedMassInverse[i];
            }
            final double mean = sum / dim;
            for (int i = 0; i < dim; i++) {
                boundedMassInverse[i] = boundedMassInverse[i] * mean;
            }
            return boundedMassInverse;
        }

        @Override
        public double getKineticEnergy(ReadableVector momentum) {
            double total = 0.0;
            for (int i = 0; i < dim; i++) {
                total += momentum.get(i) * momentum.get(i) * inverseMass[i];
            }
            return total / 2.0;
        }

        @Override
        public double getVelocity(int i, ReadableVector momentum) {
            return momentum.get(i) * inverseMass[i];
        }
    }

    // TODO Implement
    class FullPreconditioning extends HessianBased {

        FullPreconditioning(HessianWrtParameterProvider hessian) {
            super(hessian);
        }

        @Override
        protected double[] computeInverseMass() {
            return new double[dim * dim];
        }

        @Override
        public WrappedVector drawInitialMomentum() {
            return null;
        }

        @Override
        public double getKineticEnergy(ReadableVector momentum) {
            return 0.0;
        }

        @Override
        public double getVelocity(int i, ReadableVector momentum) {
            return 0.0; // M^{-1}_{i} momentum
        }
    }

    abstract // TODO Implement interface
    class SecantPreconditioing extends HessianBased {

        private final Secant[] queue;
        private int secantIndex;
        private int secantUpdateCount;

        SecantPreconditioing(HessianWrtParameterProvider hessian, int secantSize) {
            super(hessian);

            this.queue = new Secant[secantSize];
            this.secantIndex = 0;
            this.secantUpdateCount = 0;
        }

        @Override
        public void storeSecant(ReadableVector gradient, ReadableVector position) {
            queue[secantIndex] = new Secant(gradient, position);
            secantIndex = (secantIndex + 1) % queue.length;
            ++secantUpdateCount;
        }

        private class Secant { // TODO Inner class because we may change the storage depending on efficiency

            ReadableVector gradient;
            ReadableVector position;

            Secant(ReadableVector gradient, ReadableVector position) {
                this.gradient = gradient;
                this.position = position;
            }
        }
    }
}
